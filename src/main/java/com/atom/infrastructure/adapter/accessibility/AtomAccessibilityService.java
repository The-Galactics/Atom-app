package com.atom.infrastructure.adapter.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Path;
import android.graphics.Rect;
import android.accessibilityservice.GestureDescription;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * On-device executor for actions a plain {@code Intent} cannot perform — global
 * navigation (back/home/recents) and future gesture/node interactions.
 *
 * <p>The OS owns this service's lifecycle (enabled by the user in Settings), so
 * the action layer reaches the live instance via {@link #getInstance()}.
 */
public class AtomAccessibilityService extends AccessibilityService {

    private static final String TAG = "AtomA11yService";

    // Bounds the snapshot so a huge tree never bloats the gRPC payload.
    private static final int MAX_NODES = 200;

    /** Domain-friendly view of one on-screen node; proto mapping lives in the gRPC adapter. */
    public static final class ScreenNode {
        public final String text;
        public final String role;
        public final boolean clickable;
        public final boolean focusable;
        public final boolean editable;
        public final boolean scrollable;
        public final int index;

        public ScreenNode(String text, String role, boolean clickable, boolean focusable,
                          boolean editable, boolean scrollable, int index) {
            this.text = text;
            this.role = role;
            this.clickable = clickable;
            this.focusable = focusable;
            this.editable = editable;
            this.scrollable = scrollable;
            this.index = index;
        }
    }

    // Set while the service is connected; read by the action executor.
    @Nullable
    private static volatile AtomAccessibilityService instance;

    /** The running service, or {@code null} when the user has not enabled it. */
    @Nullable
    public static AtomAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "Atom accessibility service connected.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // FUTURE WORK: inspect the active window for node-targeted actions.
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "Atom accessibility service interrupted.");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        if (instance == this) {
            instance = null;
        }
        Log.i(TAG, "Atom accessibility service unbound.");
        return super.onUnbind(intent);
    }

    // --- global-action helpers (used by the action layer) -------------------

    /** Navigates back, equivalent to the system Back button. */
    public boolean back() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    /** Goes to the launcher/home screen. */
    public boolean home() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    /** Opens the recent-apps overview. */
    public boolean recents() {
        return performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    /** Opens the Quick Settings shade (handy for manual radio toggles). */
    public boolean quickSettings() {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
    }

    /** Routes a {@code NAVIGATE} direction to the matching global action. */
    public boolean navigate(String direction) {
        if (direction == null) {
            return false;
        }
        return switch (direction.toLowerCase(Locale.ROOT).trim()) {
            case "back" -> back();
            case "home" -> home();
            case "recents" -> recents();
            case "quick_settings" -> quickSettings();
            default -> false;
        };
    }

    // --- gesture / node interactions ----------------------------------------

    /**
     * Scrolls the active window in the given direction. Prefers a scrollable
     * node's {@code ACTION_SCROLL_FORWARD/BACKWARD} (respects the app's own
     * scroll containers) and falls back to a swipe gesture when no scrollable
     * node is exposed.
     */
    public boolean scroll(String direction) {
        if (direction == null) {
            return false;
        }
        String dir = direction.toLowerCase(Locale.ROOT).trim();
        boolean forward = "down".equals(dir) || "right".equals(dir);

        AccessibilityNodeInfo root = getRootInActiveWindow();
        try {
            AccessibilityNodeInfo scrollable = findScrollable(root);
            if (scrollable != null) {
                try {
                    int action = forward
                            ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                            : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
                    return scrollable.performAction(action);
                } finally {
                    if (scrollable != root) {
                        scrollable.recycle();
                    }
                }
            }
        } finally {
            if (root != null) {
                root.recycle();
            }
        }
        return swipe(dir);
    }

    /** Reads the visible text of the active window, joined by newlines. */
    public String readScreen() {
        StringBuilder sb = new StringBuilder();
        for (ScreenNode node : captureScreen()) {
            if (!node.text.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(node.text);
            }
        }
        return sb.toString().trim();
    }

    /**
     * Walks the active window once and returns a structured map of nodes that
     * carry signal (have text/contentDescription, or are clickable/editable/
     * scrollable). Empty when no window is available.
     */
    public List<ScreenNode> captureScreen() {
        List<ScreenNode> nodes = new ArrayList<>();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return nodes;
        }
        try {
            collectNodes(root, nodes);
        } finally {
            root.recycle();
        }
        return nodes;
    }

    /**
     * Taps the first clickable node whose visible text matches {@code text},
     * climbing to a clickable ancestor when the matched node itself is not
     * clickable (e.g. a label inside a button row).
     */
    public boolean tapByText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String needle = text.trim();
        // Prefer the active/foreground window so we never click a stale
        // background window that merely contains matching text.
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) {
            try {
                if (tapInRoot(active, needle)) {
                    return true;
                }
            } finally {
                active.recycle();
            }
        }
        // Fall back to other windows: at tap time the active window may be a
        // just-dismissed dialog or the IME while the target lives behind it.
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) {
                    continue;
                }
                try {
                    if (tapInRoot(root, needle)) {
                        return true;
                    }
                } finally {
                    root.recycle();
                }
            }
        }
        return false;
    }

    /**
     * Taps the first node whose text OR contentDescription contains {@code needle}
     * within one window root. We search both because icon buttons (e.g. the
     * top-bar "Ajustes"/"Historial") expose only a contentDescription, which the
     * framework's {@code findAccessibilityNodeInfosByText} does not match.
     */
    private boolean tapInRoot(AccessibilityNodeInfo root, String needle) {
        AccessibilityNodeInfo match = findByTextOrDesc(root, needle.toLowerCase(Locale.ROOT));
        if (match == null) {
            return false;
        }
        AccessibilityNodeInfo clickable = firstClickable(match);
        try {
            return clickable != null
                    && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } finally {
            if (clickable != null && clickable != match) {
                clickable.recycle();
            }
            match.recycle();
        }
    }

    /** DFS for the first node whose text or contentDescription contains {@code needleLower}. */
    @Nullable
    private AccessibilityNodeInfo findByTextOrDesc(@Nullable AccessibilityNodeInfo node,
                                                   String needleLower) {
        if (node == null) {
            return null;
        }
        if (contains(node.getText(), needleLower)
                || contains(node.getContentDescription(), needleLower)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            AccessibilityNodeInfo found = findByTextOrDesc(child, needleLower);
            if (found != null) {
                if (found != child) {
                    child.recycle();
                }
                return found;
            }
            child.recycle();
        }
        return null;
    }

    private static boolean contains(@Nullable CharSequence text, String needleLower) {
        return text != null
                && text.toString().toLowerCase(Locale.ROOT).contains(needleLower);
    }

    // --- helpers ------------------------------------------------------------

    /** Depth-first search for the first scrollable node. */
    @Nullable
    private AccessibilityNodeInfo findScrollable(@Nullable AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isScrollable()) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            AccessibilityNodeInfo found = findScrollable(child);
            if (found != null) {
                if (found != child) {
                    child.recycle();
                }
                return found;
            }
            child.recycle();
        }
        return null;
    }

    /** Returns the node itself or its nearest clickable ancestor; null if none. */
    @Nullable
    private AccessibilityNodeInfo firstClickable(@Nullable AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (current != node) {
                current.recycle();
            }
            current = parent;
        }
        return null;
    }

    /** Depth-first walk collecting signal-bearing nodes into {@code out}. */
    private void collectNodes(@Nullable AccessibilityNodeInfo node, List<ScreenNode> out) {
        if (node == null || out.size() >= MAX_NODES) {
            return;
        }
        CharSequence raw = node.getText();
        if (raw == null || raw.toString().trim().isEmpty()) {
            raw = node.getContentDescription();
        }
        String text = raw == null ? "" : raw.toString().trim();

        boolean clickable = node.isClickable();
        boolean editable = node.isEditable();
        boolean scrollable = node.isScrollable();
        // Keep only nodes with text or some interactivity; skip layout containers.
        if (!text.isEmpty() || clickable || editable || scrollable) {
            out.add(new ScreenNode(text, simpleRole(node.getClassName()), clickable,
                    node.isFocusable(), editable, scrollable, out.size()));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectNodes(child, out);
                child.recycle();
            }
        }
    }

    /** "android.widget.Button" -> "Button"; empty when unavailable. */
    private static String simpleRole(@Nullable CharSequence className) {
        if (className == null) {
            return "";
        }
        String name = className.toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    /** Fallback swipe gesture across the screen centre in the given direction. */
    private boolean swipe(String dir) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        Rect bounds = new Rect();
        if (root != null) {
            root.getBoundsInScreen(bounds);
            root.recycle();
        }
        if (bounds.isEmpty()) {
            return false;
        }
        int cx = bounds.centerX();
        int cy = bounds.centerY();
        // A swipe moves the content opposite to the finger; to scroll down
        // (content up) the finger goes from bottom to top, and vice versa.
        int dx = 0;
        int dy = 0;
        int qx = bounds.width() / 4;
        int qy = bounds.height() / 4;
        switch (dir) {
            case "down" -> dy = -qy;
            case "up" -> dy = qy;
            case "right" -> dx = -qx;
            case "left" -> dx = qx;
            default -> { return false; }
        }
        Path path = new Path();
        path.moveTo(cx - dx, cy - dy);
        path.lineTo(cx + dx, cy + dy);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 250L))
                .build();
        return dispatchGesture(gesture, null, null);
    }
}

package com.atom.infrastructure.adapter.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Path;
import android.graphics.Rect;
import android.accessibilityservice.GestureDescription;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.Nullable;

import com.atom.app.R;
import com.atom.domain.utils.TextNormalizer;

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

    // Retry the find a few times so a step tolerates render/network lag (~6 * 400ms ≈ 2.4s).
    private static final int FIND_RETRIES = 6;
    private static final long FIND_RETRY_DELAY_MS = 400L;

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
        Log.i(TAG, "scroll direction=" + dir);

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
     * clickable (e.g. a label inside a button row). Polls a few times so the
     * target survives render/network lag.
     */
    public boolean tapByText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String needle = text.trim();
        for (int attempt = 0; attempt < FIND_RETRIES; attempt++) {
            // Prefer the active/foreground window so we never click a stale
            // background window that merely contains matching text.
            AccessibilityNodeInfo active = getRootInActiveWindow();
            if (active != null) {
                try {
                    if (tapInRoot(active, needle)) {
                        Log.i(TAG, "tapByText matched in active window: \"" + needle + "\"");
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
                            Log.i(TAG, "tapByText matched in window: \"" + needle + "\"");
                            return true;
                        }
                    } finally {
                        root.recycle();
                    }
                }
            }
            sleepRetry(attempt);
        }
        Log.w(TAG, "tapByText found no match for \"" + needle + "\" after "
                + FIND_RETRIES + " attempts");
        return false;
    }

    /**
     * Taps the first node whose text OR contentDescription matches {@code needle}
     * within one window root. We search both because icon buttons (e.g. the
     * top-bar "Ajustes"/"Historial") expose only a contentDescription, which the
     * framework's {@code findAccessibilityNodeInfosByText} does not match.
     * When the matched node has no clickable ancestor, we fall back to clicking
     * the node itself (last resort: its parent).
     */
    private boolean tapInRoot(AccessibilityNodeInfo root, String needle) {
        AccessibilityNodeInfo match = findByTextOrDesc(root, TextNormalizer.fold(needle));
        if (match == null) {
            return false;
        }
        AccessibilityNodeInfo clickable = firstClickable(match);
        try {
            // Prefer the interactive wrapper when it geometrically contains the text
            // node (icon/row overlapping the label) — gives the real hit target instead
            // of a blind center coordinate. Otherwise fall back to the match itself.
            AccessibilityNodeInfo target = match;
            if (clickable != null && clickable != match) {
                Rect textBounds = new Rect();
                Rect wrapBounds = new Rect();
                match.getBoundsInScreen(textBounds);
                clickable.getBoundsInScreen(wrapBounds);
                target = wrapBounds.contains(textBounds) ? clickable : match;
            } else if (clickable != null) {
                target = clickable;
            }
            boolean done = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!done && clickable == null) {
                // Last resort: try the parent of an unclickable match.
                AccessibilityNodeInfo parent = match.getParent();
                if (parent != null) {
                    try {
                        done = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    } finally {
                        parent.recycle();
                    }
                }
            }
            return done;
        } finally {
            if (clickable != null && clickable != match) {
                clickable.recycle();
            }
            match.recycle();
        }
    }

    /**
     * DFS for the best node whose text or contentDescription matches
     * {@code needleLower}, preferring (in order) exact match, prefix match, then
     * substring. Atom's own overlay nodes are skipped so the bubble's labels can
     * never shadow the foreground app's UI.
     */
    @Nullable
    private AccessibilityNodeInfo findByTextOrDesc(@Nullable AccessibilityNodeInfo node,
                                                   String needleLower) {
        Match best = findBest(node, needleLower, null);
        if (best == null) {
            return null;
        }
        Log.i(TAG, "findByTextOrDesc rank=" + best.rank + " for \"" + needleLower + "\"");
        return best.node;
    }

    /** Match quality ranks; lower is better. */
    private static final int RANK_EXACT = 0;
    private static final int RANK_PREFIX = 1;
    // A whole-word (token) match ranks below prefix but above a bare substring,
    // so a short needle like "ana" matches the token "ana" but not "susana".
    private static final int RANK_WORD = 2;
    private static final int RANK_SUBSTRING = 3;

    // Bare (non-word) substring matches are only allowed for needles this long,
    // so short needles can't sub-word-match a longer label ("ana"/"susana").
    private static final int MIN_SUBSTRING_NEEDLE = 4;

    /** A candidate node with its match quality, so the DFS can keep the best. */
    private static final class Match {
        final AccessibilityNodeInfo node;
        final int rank;

        Match(AccessibilityNodeInfo node, int rank) {
            this.node = node;
            this.rank = rank;
        }
    }

    /**
     * DFS keeping the best-ranked match. {@code current} is the best found so far
     * (its node owned by the caller); we recycle whatever we don't keep so callers
     * still own exactly one node.
     */
    @Nullable
    private Match findBest(@Nullable AccessibilityNodeInfo node, String needleLower,
                           @Nullable Match current) {
        if (node == null || isOwnOverlay(node)) {
            return current;
        }
        int rank = rankOf(node.getText(), needleLower);
        if (rank < 0) {
            rank = rankOf(node.getContentDescription(), needleLower);
        }
        if (rank >= 0 && (current == null || rank < current.rank)) {
            if (current != null) {
                current.node.recycle();
            }
            current = new Match(AccessibilityNodeInfo.obtain(node), rank);
            if (rank == RANK_EXACT) {
                return current; // can't do better; stop early
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            current = findBest(child, needleLower, current);
            child.recycle();
            if (current != null && current.rank == RANK_EXACT) {
                return current;
            }
        }
        return current;
    }

    // Stable view-id markers on Atom's overlay roots, matched by suffix (no hardcoded
    // package). Requires flagReportViewIds in accessibility_service_config.
    private static final String[] OVERLAY_ROOT_ID_SUFFIXES = {
            ":id/bubble_root",
            ":id/overlay_panel_root",
            ":id/handle_bar",
            ":id/dismiss_root",
    };

    /**
     * Skips Atom's own overlay nodes (bubble/panel) so their labels can't shadow the
     * foreground app, without skipping the rest of Atom's UI. A node is overlay when its
     * window is an accessibility overlay, or it/an ancestor carries an overlay-root view-id.
     */
    private boolean isOwnOverlay(AccessibilityNodeInfo node) {
        if (isAccessibilityOverlayWindow(node)) {
            return true;
        }
        // Walk up checking the view-id marker. Cheap: overlay trees are shallow,
        // and the very common case (foreground app nodes) has no Atom view-id.
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        try {
            while (current != null) {
                if (matchesOverlayRootId(current.getViewIdResourceName())) {
                    return true;
                }
                AccessibilityNodeInfo parent = current.getParent();
                current.recycle();
                current = parent;
            }
        } finally {
            if (current != null) {
                current.recycle();
            }
        }
        return false;
    }

    /** True when the node belongs to a genuine accessibility-overlay window. */
    private boolean isAccessibilityOverlayWindow(AccessibilityNodeInfo node) {
        AccessibilityWindowInfo window = node.getWindow();
        if (window == null) {
            return false;
        }
        try {
            return window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY;
        } finally {
            window.recycle();
        }
    }

    /** True when {@code viewId} ends with one of Atom's overlay-root id suffixes. */
    private static boolean matchesOverlayRootId(@Nullable String viewId) {
        if (viewId == null) {
            return false;
        }
        for (String suffix : OVERLAY_ROOT_ID_SUFFIXES) {
            if (viewId.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exact/prefix/word/substring rank for one CharSequence, or -1 when no match.
     * {@code needleLower} is already folded by the caller; the haystack is folded here.
     */
    private static int rankOf(@Nullable CharSequence text, String needleLower) {
        if (text == null) {
            return -1;
        }
        return rankFolded(TextNormalizer.fold(text.toString()), needleLower);
    }

    /**
     * Ranks an already-folded haystack against an already-folded needle. Exact &gt;
     * prefix &gt; whole-word token &gt; bare substring. A bare substring only counts
     * when the needle is at least {@link #MIN_SUBSTRING_NEEDLE} chars, so a short
     * needle ("ana") can't sub-word-match a longer label ("susana").
     */
    private static int rankFolded(String hay, String needleLower) {
        if (hay.isEmpty()) {
            return -1;
        }
        if (hay.equals(needleLower)) {
            return RANK_EXACT;
        }
        if (hay.startsWith(needleLower)) {
            return RANK_PREFIX;
        }
        for (String token : hay.split("[^\\p{Alnum}]+")) {
            if (token.equals(needleLower)) {
                return RANK_WORD;
            }
        }
        if (needleLower.length() >= MIN_SUBSTRING_NEEDLE && hay.contains(needleLower)) {
            return RANK_SUBSTRING;
        }
        return -1;
    }

    /**
     * Package-private JVM test entry point: folds both operands (as the caller +
     * {@link #rankOf} do at runtime) and runs the same ranking logic, so the tier
     * ordering and length guard are unit-testable without an Android runtime.
     */
    static int rankFor(String hayRaw, String needleRaw) {
        return rankFolded(TextNormalizer.fold(hayRaw), TextNormalizer.fold(needleRaw));
    }

    // --- text entry ---------------------------------------------------------

    /**
     * Types {@code text} into the focused editable field (or the first editable
     * node in the active window), then optionally submits. Polls so the target
     * field survives render lag. Returns true on a successful set-text.
     */
    public boolean typeText(String text, boolean submit) {
        if (text == null) {
            return false;
        }
        for (int attempt = 0; attempt < FIND_RETRIES; attempt++) {
            AccessibilityNodeInfo target = resolveEditable();
            if (target != null) {
                try {
                    Bundle args = new Bundle();
                    args.putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    boolean set = target.performAction(
                            AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    Log.i(TAG, "typeText set=" + set + " submit=" + submit
                            + " text=\"" + text + "\"");
                    if (!set) {
                        return false;
                    }
                    if (submit) {
                        boolean submitted = submit(target);
                        // A failed submit does not fail typeText: the typed text is set and
                        // recoverable (user/loop can submit via a follow-up), so don't discard it.
                        if (!submitted) {
                            Log.w(TAG, "typeText: text set but submit did not fire "
                                    + "(continuing; not treated as failure)");
                        } else {
                            Log.i(TAG, "typeText submit result=true");
                        }
                    }
                    return true;
                } finally {
                    target.recycle();
                }
            }
            sleepRetry(attempt);
        }
        Log.w(TAG, "typeText found no editable field after " + FIND_RETRIES + " attempts");
        return false;
    }

    /** Focused input if it is editable, else the first editable node in the window. */
    @Nullable
    private AccessibilityNodeInfo resolveEditable() {
        // 1. Active/foreground window first (current behavior).
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) {
            try {
                AccessibilityNodeInfo target = editableInRoot(active);
                if (target != null) {
                    return target;
                }
            } finally {
                active.recycle();
            }
        }
        // 2. Fall back to every window (mirrors tapByText): when typing into a
        //    search box (e.g. Google), the active window is the IME/suggestions
        //    popup while the editable field lives in another window.
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) {
                    continue;
                }
                try {
                    AccessibilityNodeInfo target = editableInRoot(root);
                    if (target != null) {
                        return target;
                    }
                } finally {
                    root.recycle();
                }
            }
        }
        return null;
    }

    /** Focused input if it is editable, else the first editable node within one
     *  root. Returns a caller-owned node, or null. */
    @Nullable
    private AccessibilityNodeInfo editableInRoot(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null) {
            if (focused.isEditable()) {
                return focused;
            }
            focused.recycle();
        }
        return findEditable(root);
    }

    /** DFS for the first editable node; returned node is owned by the caller. */
    @Nullable
    private AccessibilityNodeInfo findEditable(@Nullable AccessibilityNodeInfo node) {
        if (node == null || isOwnOverlay(node)) {
            return null;
        }
        if (node.isEditable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            AccessibilityNodeInfo found = findEditable(child);
            child.recycle();
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Submits typed text: prefers IME enter/search (API 30+), else taps a localized
     * search/submit button. Returns true only when a submit actually fired.
     */
    private boolean submit(AccessibilityNodeInfo field) {
        // 1. Preferred: IME enter/search action (API 30+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (field.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId())) {
                Log.i(TAG, "submit via ACTION_IME_ENTER");
                return true;
            }
        }
        // 2. Apps with non-standard IME hooks (e.g. TikTok) often fire their editor
        //    action on a click of the focused field itself. Accessibility-only; no
        //    hardware KeyEvent injection is possible without INJECT_EVENTS/root.
        if (field.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.i(TAG, "submit via field ACTION_CLICK fallback");
            return true;
        }
        // 3. Fallback (also covers API 26-29): tap a localized search/submit button.
        String[] labels = getResources().getStringArray(R.array.search_submit_labels);
        for (String label : labels) {
            if (tapByText(label)) {
                Log.i(TAG, "submit via localized button \"" + label + "\"");
                return true;
            }
        }
        // Nothing submitted: text set but search not sent. Log loudly so it isn't mistaken for success.
        Log.w(TAG, "submit failed: text typed but NOT submitted (no IME action, no field click, "
                + "no fallback button matched " + java.util.Arrays.toString(labels) + ")");
        return false;
    }

    /** Sleeps between find attempts (no-op after the final attempt). */
    private void sleepRetry(int attempt) {
        if (attempt >= FIND_RETRIES - 1) {
            return;
        }
        SystemClock.sleep(FIND_RETRY_DELAY_MS);
    }

    // --- helpers ------------------------------------------------------------

    /** Depth-first search for the first scrollable node; returned node is owned by the caller. */
    @Nullable
    private AccessibilityNodeInfo findScrollable(@Nullable AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.isScrollable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            AccessibilityNodeInfo found = findScrollable(child);
            child.recycle();
            if (found != null) {
                return found;
            }
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

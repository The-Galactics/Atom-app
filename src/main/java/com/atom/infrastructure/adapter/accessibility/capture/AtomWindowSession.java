package com.atom.infrastructure.adapter.accessibility.capture;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.atom.infrastructure.adapter.accessibility.oem.OemCompatibilityAdapter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** One attempt's native windows. close() recycles every window + obtained root once. */
public final class AtomWindowSession implements WindowSession {

    private static final int MAX_NODES = 200;

    private final List<AccessibilityWindowInfo> nativeWindows = new ArrayList<>();
    private final List<AccessibilityNodeInfo> rootsToRecycle = new ArrayList<>();
    private final long lastMutationAtMs;

    public AtomWindowSession(AccessibilityService service, long lastMutationAtMs) {
        this.lastMutationAtMs = lastMutationAtMs;
        List<AccessibilityWindowInfo> ws = service.getWindows();
        if (ws != null) {
            nativeWindows.addAll(ws);
        }
    }

    @Override
    public List<WindowSnapshot> windows() {
        List<WindowSnapshot> out = new ArrayList<>(nativeWindows.size());
        Rect r = new Rect();
        for (AccessibilityWindowInfo w : nativeWindows) {
            w.getBoundsInScreen(r);
            String pkg = "";
            AccessibilityNodeInfo root = w.getRoot();
            if (root != null) {
                CharSequence p = root.getPackageName();
                pkg = p == null ? "" : p.toString();
                safeRecycle(root); // transient: only needed for the package name here
            }
            out.add(new WindowSnapshot(w.getId(), w.getType(), w.getLayer(),
                    w.isFocused(), w.isActive(), pkg, r.left, r.top, r.right, r.bottom));
        }
        return out;
    }

    @Override
    public CaptureProbe probe(WindowSnapshot target, int prevTopLevelChildCount, long msSinceLastMutation) {
        AccessibilityNodeInfo root = rootFor(target.windowId());
        if (root == null) {
            return new CaptureProbe(false, false, false, 0, prevTopLevelChildCount, msSinceLastMutation);
        }
        rootsToRecycle.add(root); // recycled in close()
        boolean refreshed = root.refresh();
        int childCount = root.getChildCount();
        return new CaptureProbe(true, refreshed, true, childCount,
                prevTopLevelChildCount, msSinceLastMutation);
    }

    @Override
    public List<NodeSnapshot> extract(WindowSnapshot target, OemCompatibilityAdapter adapter) {
        List<NodeSnapshot> out = new ArrayList<>();
        AccessibilityNodeInfo root = rootFor(target.windowId());
        if (root == null) {
            return out;
        }
        // Iterative DFS; recycle ONLY children obtained via getChild(). Root is owned
        // by rootsToRecycle / close(), never recycled here.
        Deque<Frame> stack = new ArrayDeque<>();
        boolean rootAlreadyTracked = rootsToRecycle.contains(root);
        if (!rootAlreadyTracked) {
            rootsToRecycle.add(root);
        }
        stack.push(new Frame(root, 0, 0));
        Rect r = new Rect();
        try {
            while (!stack.isEmpty() && out.size() < MAX_NODES) {
                Frame f = stack.pop();
                AccessibilityNodeInfo node = f.node;
                boolean isRoot = node == root;
                try {
                    NodeSnapshot snap = toSnapshot(node, f.depth, f.siblingIndex, r);
                    if (snap != null && !adapter.isPhantom(snap)) {
                        out.add(snap);
                    }
                    int count = node.getChildCount();
                    for (int i = count - 1; i >= 0; i--) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) {
                            stack.push(new Frame(child, f.depth + 1, i));
                        }
                    }
                } catch (RuntimeException staleNode) {
                    // A node can go stale mid-walk (common on HyperOS transitions).
                    // Skip it and keep walking the rest of the tree instead of
                    // aborting the whole attempt; the node is recycled in finally.
                } finally {
                    if (!isRoot) {
                        safeRecycle(node); // children obtained via getChild()
                    }
                }
            }
        } finally {
            // Always drain pushed-but-unpopped children (MAX_NODES hit OR an
            // exception escaped the loop) so no native child node leaks.
            while (!stack.isEmpty()) {
                AccessibilityNodeInfo node = stack.pop().node;
                if (node != root) {
                    safeRecycle(node);
                }
            }
        }
        return out;
    }

    private static NodeSnapshot toSnapshot(AccessibilityNodeInfo node, int depth, int siblingIndex, Rect r) {
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String label = text != null ? text.toString() : (desc != null ? desc.toString() : "");
        CharSequence cls = node.getClassName();
        CharSequence pkg = node.getPackageName();
        node.getBoundsInScreen(r);
        return NodeSnapshot.builder()
                .text(label)
                .role(simpleRole(cls))
                .clickable(node.isClickable())
                .focusable(node.isFocusable())
                .editable(node.isEditable())
                .scrollable(node.isScrollable())
                .visibleToUser(node.isVisibleToUser())
                .viewId(node.getViewIdResourceName())
                .packageName(pkg == null ? "" : pkg.toString())
                .bounds(r.left, r.top, r.right, r.bottom)
                .depth(depth)
                .siblingIndex(siblingIndex)
                .build();
    }

    private static String simpleRole(CharSequence className) {
        if (className == null) {
            return "";
        }
        String s = className.toString();
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    private AccessibilityNodeInfo rootFor(int windowId) {
        for (AccessibilityWindowInfo w : nativeWindows) {
            if (w.getId() == windowId) {
                return w.getRoot();
            }
        }
        return null;
    }

    private static void safeRecycle(AccessibilityNodeInfo node) {
        try {
            node.recycle();
        } catch (IllegalStateException ignored) {
            // Already recycled / no-op on API 33+.
        }
    }

    @Override
    public void close() {
        for (AccessibilityNodeInfo root : rootsToRecycle) {
            safeRecycle(root);
        }
        rootsToRecycle.clear();
        for (AccessibilityWindowInfo w : nativeWindows) {
            try {
                w.recycle();
            } catch (IllegalStateException ignored) {
                // no-op
            }
        }
        nativeWindows.clear();
    }

    private static final class Frame {
        final AccessibilityNodeInfo node;
        final int depth;
        final int siblingIndex;
        Frame(AccessibilityNodeInfo node, int depth, int siblingIndex) {
            this.node = node;
            this.depth = depth;
            this.siblingIndex = siblingIndex;
        }
    }
}

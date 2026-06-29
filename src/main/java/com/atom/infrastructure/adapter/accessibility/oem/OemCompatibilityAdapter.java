package com.atom.infrastructure.adapter.accessibility.oem;

import com.atom.infrastructure.adapter.accessibility.capture.CaptureProbe;
import com.atom.infrastructure.adapter.accessibility.capture.NodeSnapshot;
import com.atom.infrastructure.adapter.accessibility.capture.WindowSnapshot;
import java.util.List;

/** Per-OEM strategy supplying the four resilient-capture hooks. */
public interface OemCompatibilityAdapter {
    RetryPolicy retryPolicy();

    /** Quirk 1: is the tree settled enough to extract? */
    boolean isTreeReady(CaptureProbe probe);

    /** Quirk 4: choose the real foreground app window; null if none suitable. */
    WindowSnapshot selectActiveWindow(List<WindowSnapshot> windows);

    /** Quirk 2: should this node be dropped before serialization? */
    boolean isPhantom(NodeSnapshot node);

    /** Quirk 3: stable identity used for dedup/ordering (viewId fallback). */
    long synthesizeStableId(NodeSnapshot node);
}

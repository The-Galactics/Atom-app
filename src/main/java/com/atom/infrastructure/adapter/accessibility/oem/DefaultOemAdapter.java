package com.atom.infrastructure.adapter.accessibility.oem;

import com.atom.infrastructure.adapter.accessibility.capture.CaptureProbe;
import com.atom.infrastructure.adapter.accessibility.capture.NodeSnapshot;
import com.atom.infrastructure.adapter.accessibility.capture.WindowSnapshot;
import java.util.List;

/** Lenient strategy for stock and not-yet-specialized skins. */
public class DefaultOemAdapter implements OemCompatibilityAdapter {

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.lenient();
    }

    @Override
    public boolean isTreeReady(CaptureProbe probe) {
        return probe.rootPresent() && probe.topLevelChildCount() > 0;
    }

    @Override
    public WindowSnapshot selectActiveWindow(List<WindowSnapshot> windows) {
        WindowSnapshot firstApp = null;
        for (WindowSnapshot w : windows) {
            if (w.type() != WindowSnapshot.TYPE_APPLICATION) {
                continue;
            }
            if (w.active() || w.focused()) {
                return w;
            }
            if (firstApp == null) {
                firstApp = w;
            }
        }
        return firstApp;
    }

    @Override
    public boolean isPhantom(NodeSnapshot node) {
        return false;
    }

    @Override
    public long synthesizeStableId(NodeSnapshot node) {
        return StableId.of(node);
    }
}

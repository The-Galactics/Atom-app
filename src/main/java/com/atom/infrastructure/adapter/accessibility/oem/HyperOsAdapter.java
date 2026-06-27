package com.atom.infrastructure.adapter.accessibility.oem;

import com.atom.infrastructure.adapter.accessibility.capture.CaptureProbe;
import com.atom.infrastructure.adapter.accessibility.capture.NodeSnapshot;
import com.atom.infrastructure.adapter.accessibility.capture.WindowSnapshot;
import java.util.List;
import java.util.Set;

/** Xiaomi HyperOS / MIUI strategy: slow trees, overlay windows, stripped ids. */
public class HyperOsAdapter implements OemCompatibilityAdapter {

    static final long QUIET_WINDOW_MS = 120L;

    /** MIUI system packages that render overlay/phantom windows over the app. */
    private static final Set<String> OVERLAY_PACKAGES = Set.of(
            "com.android.systemui",
            "com.miui.securitycenter",
            "com.miui.contentcatcher",
            "com.mi.android.globalminusscreen");

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.aggressive();
    }

    @Override
    public boolean isTreeReady(CaptureProbe probe) {
        if (!probe.rootPresent() || !probe.rootRefreshed() || probe.topLevelChildCount() <= 0) {
            return false;
        }
        boolean quiet = probe.msSinceLastMutation() >= QUIET_WINDOW_MS;
        boolean stable = probe.prevTopLevelChildCount() >= 0
                && probe.topLevelChildCount() == probe.prevTopLevelChildCount();
        return quiet || stable;
    }

    @Override
    public WindowSnapshot selectActiveWindow(List<WindowSnapshot> windows) {
        return null; // implemented in 7.2
    }

    @Override
    public boolean isPhantom(NodeSnapshot node) {
        return false; // implemented in 7.3
    }

    @Override
    public long synthesizeStableId(NodeSnapshot node) {
        return StableId.of(node); // refined in 7.4
    }
}

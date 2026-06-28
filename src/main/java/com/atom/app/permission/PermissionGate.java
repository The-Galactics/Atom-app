package com.atom.app.permission;

/**
 * Pure first-run gating: the user may leave the permission setup screen only once the
 * three core permissions (overlay, accessibility, microphone) are granted. OEM extras
 * (notifications, battery, autostart) never block.
 */
public final class PermissionGate {

    private PermissionGate() { }

    public static int missingCoreCount(boolean overlay, boolean accessibility, boolean mic) {
        int missing = 0;
        if (!overlay) missing++;
        if (!accessibility) missing++;
        if (!mic) missing++;
        return missing;
    }

    public static boolean canContinue(boolean overlay, boolean accessibility, boolean mic) {
        return missingCoreCount(overlay, accessibility, mic) == 0;
    }
}

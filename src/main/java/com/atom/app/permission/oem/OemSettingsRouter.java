package com.atom.app.permission.oem;

import com.atom.infrastructure.adapter.accessibility.oem.OemSkin;

/**
 * Pure routing decisions: given the detected {@link OemSkin} and an {@link IntentResolver},
 * choose the best-known destination for the battery exemption and the OEM autostart screen,
 * always with a resolvable fallback. Returns string {@link OemRedirect} descriptors; the
 * Android Intent is built by {@code OemSettingsIntents}.
 */
public final class OemSettingsRouter {

    public static final String ACTION_REQUEST_IGNORE_BATTERY =
            "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS";
    public static final String ACTION_BATTERY_SETTINGS_LIST =
            "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS";
    public static final String ACTION_APP_DETAILS =
            "android.settings.APPLICATION_DETAILS_SETTINGS";

    private OemSettingsRouter() { }

    /**
     * Battery is skin-independent: the AOSP exemption is what we can actually detect, so we
     * prefer the one-tap request dialog and fall back to the full exemption list.
     */
    public static OemRedirect chooseBattery(IntentResolver resolver) {
        if (resolver.resolvesAction(ACTION_REQUEST_IGNORE_BATTERY)) {
            return OemRedirect.action(ACTION_REQUEST_IGNORE_BATTERY);
        }
        return OemRedirect.action(ACTION_BATTERY_SETTINGS_LIST);
    }
}

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

    public static boolean supportsAutostart(OemSkin skin) {
        return switch (skin) {
            case HYPEROS, MIUI, COLOROS, ORIGINOS_FUNTOUCH -> true;
            case ONEUI, STOCK -> false;
        };
    }

    /** First vendor autostart/background-management screen that resolves, else app details. */
    public static OemRedirect chooseAutostart(OemSkin skin, IntentResolver resolver) {
        for (String[] candidate : autostartCandidates(skin)) {
            if (resolver.resolvesComponent(candidate[0], candidate[1])) {
                return OemRedirect.component(candidate[0], candidate[1]);
            }
        }
        return OemRedirect.action(ACTION_APP_DETAILS);
    }

    // Ordered {package, class} candidates per skin (best-known first). Wrong/renamed
    // components simply fail to resolve and fall through — never crash.
    private static String[][] autostartCandidates(OemSkin skin) {
        return switch (skin) {
            case HYPEROS, MIUI -> new String[][] {
                {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            };
            case COLOROS -> new String[][] {
                {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
                {"com.coloros.safecenter", "com.coloros.privacypermissionsentry.PermissionTopActivity"},
                {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
                {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"},
            };
            case ORIGINOS_FUNTOUCH -> new String[][] {
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
                {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"},
            };
            case ONEUI, STOCK -> new String[][] {};
        };
    }
}

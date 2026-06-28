package com.atom.app.permission;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.app.NotificationManagerCompat;

import com.atom.app.permission.oem.OemSettingsIntents;
import com.atom.domain.action.ActionType;
import com.atom.domain.action.ResolvedAction;
import com.atom.infrastructure.adapter.accessibility.AtomAccessibilityService;

public final class PermissionCoordinator {

    private PermissionCoordinator() { }

    /** Dangerous runtime permission an action needs before it runs, or null if none. */
    public static String requiredPermission(ResolvedAction action) {
        if (action == null) {
            return null;
        }
        // MAKE_CALL with ACTION_CALL needs CALL_PHONE; other actions use
        // permissionless Intents (dialer, SMS composer, settings panels).
        return switch (action.type()) {
            case MAKE_CALL -> Manifest.permission.CALL_PHONE;
            default -> null;
        };
    }

    /**
     * All dangerous runtime permissions an action needs before it runs. MAKE_CALL
     * also reads contacts to resolve a spoken name to a number, so it requests
     * both up front.
     */
    public static String[] requiredPermissions(ResolvedAction action) {
        if (action != null && action.type() == ActionType.MAKE_CALL) {
            return new String[] {
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_CONTACTS
            };
        }
        String single = requiredPermission(action);
        return single == null ? new String[0] : new String[] { single };
    }

    /**
     * True if the action is fulfilled by {@link AtomAccessibilityService} and
     * therefore needs that service enabled before it can run.
     */
    public static boolean requiresAccessibility(ResolvedAction action) {
        if (action == null) {
            return false;
        }
        return switch (action.type()) {
            case NAVIGATE, SCROLL, READ_SCREEN, TAP_ELEMENT -> true;
            default -> false;
        };
    }

    public static boolean isGranted(Context context, String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** True if the app may draw overlays on top of other apps. */
    public static boolean canDrawOverlays(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static Intent overlaySettingsIntent(Context context) {
        return new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName()));
    }

    /** True if {@link AtomAccessibilityService} is currently enabled by the user. */
    public static boolean isAccessibilityServiceEnabled(Context context) {
        ComponentName expected =
                new ComponentName(context, AtomAccessibilityService.class);

        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName parsed = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(parsed)) {
                return true;
            }
        }
        return false;
    }

    /** Intent that opens the system Accessibility settings so the user can enable Atom. */
    public static Intent accessibilitySettingsIntent() {
        return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }

    /** True if the user currently allows Atom to post notifications (app-level toggle). */
    public static boolean notificationsEnabled(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /** Intent that opens Atom's system notification settings so the user can re-enable them. */
    public static Intent appNotificationSettingsIntent(Context context) {
        return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
    }

    /** True if Atom is exempt from Doze battery optimization (services survive background). */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /** Intent that asks for (or shows the list to grant) the battery-optimization exemption. */
    public static Intent batteryOptimizationIntent(Context context) {
        return OemSettingsIntents.batteryIntent(context);
    }
}

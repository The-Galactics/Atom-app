package com.atom.app.permission;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;

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

    // FUTURE WORK: builds the MediaProjection consent intent; capture itself is not implemented.
    public static Intent screenCaptureIntent(Context context) {
        MediaProjectionManager manager =
                (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        return manager.createScreenCaptureIntent();
    }
}

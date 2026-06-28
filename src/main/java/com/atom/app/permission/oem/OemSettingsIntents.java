package com.atom.app.permission.oem;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import com.atom.infrastructure.adapter.accessibility.oem.OemDetector;
import com.atom.infrastructure.adapter.accessibility.oem.OemSkin;
import com.atom.infrastructure.adapter.accessibility.oem.SystemPropertyReader;

/**
 * Context-facing bridge: detects the OEM skin (reusing the accessibility-layer
 * {@link OemDetector}), asks {@link OemSettingsRouter} for the best destination, and
 * builds the concrete {@link Intent}. Thin by design — all decisions are in the router.
 */
public final class OemSettingsIntents {

    private OemSettingsIntents() { }

    public static OemSkin detectSkin(Context context) {
        return OemDetector.detect(Build.MANUFACTURER, Build.BRAND, new SystemPropertyReader()).skin();
    }

    public static boolean supportsAutostart(Context context) {
        return OemSettingsRouter.supportsAutostart(detectSkin(context));
    }

    public static Intent batteryIntent(Context context) {
        return toIntent(context, OemSettingsRouter.chooseBattery(new PackageManagerIntentResolver(context)));
    }

    public static Intent autostartIntent(Context context) {
        OemRedirect redirect = OemSettingsRouter.chooseAutostart(
                detectSkin(context), new PackageManagerIntentResolver(context));
        return toIntent(context, redirect);
    }

    private static Intent toIntent(Context context, OemRedirect redirect) {
        if (redirect.isComponent()) {
            return new Intent().setClassName(redirect.packageName(), redirect.className());
        }
        Intent intent = new Intent(redirect.action());
        // These two actions are package-scoped; the list action takes no data.
        if (OemSettingsRouter.ACTION_REQUEST_IGNORE_BATTERY.equals(redirect.action())
                || OemSettingsRouter.ACTION_APP_DETAILS.equals(redirect.action())) {
            intent.setData(Uri.parse("package:" + context.getPackageName()));
        }
        return intent;
    }
}

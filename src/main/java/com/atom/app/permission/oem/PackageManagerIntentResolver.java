package com.atom.app.permission.oem;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Resolves targets against the device's installed activities. */
public final class PackageManagerIntentResolver implements IntentResolver {

    private final PackageManager pm;

    public PackageManagerIntentResolver(Context context) {
        this.pm = context.getPackageManager();
    }

    @Override
    public boolean resolvesComponent(String packageName, String className) {
        Intent intent = new Intent().setClassName(packageName, className);
        return pm.resolveActivity(intent, 0) != null;
    }

    @Override
    public boolean resolvesAction(String action) {
        return new Intent(action).resolveActivity(pm) != null;
    }
}

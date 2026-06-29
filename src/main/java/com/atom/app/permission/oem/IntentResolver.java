package com.atom.app.permission.oem;

/** Seam over PackageManager so the router can ask "does this target exist?" without Android types in tests. */
public interface IntentResolver {
    boolean resolvesComponent(String packageName, String className);
    boolean resolvesAction(String action);
}

package com.atom.app.permission.oem;

/**
 * Pure description of where to send the user, as strings only (no Android types) so
 * the routing decision is unit-testable. Either a concrete activity component
 * (packageName + className) or a settings action; {@link #action} is null for the
 * component form and vice-versa.
 */
public record OemRedirect(String packageName, String className, String action) {

    public static OemRedirect component(String packageName, String className) {
        return new OemRedirect(packageName, className, null);
    }

    public static OemRedirect action(String action) {
        return new OemRedirect(null, null, action);
    }

    public boolean isComponent() {
        return action == null;
    }
}

package com.atom.app.overlay;

/**
 * Composes the live "operating" notification body. Pure Java (no Android) so it
 * unit-tests directly. Never emits a total — the autonomous loop exposes only a
 * 1-based step index.
 */
public final class OperatingNotificationText {

    private OperatingNotificationText() {}

    /** "{stepTemplate(step)}" or "{stepTemplate(step)} · {actionLabel}" when label is non-blank. */
    public static String compose(String stepTemplate, int step, String actionLabel) {
        String base = String.format(stepTemplate, step);
        if (actionLabel == null || actionLabel.trim().isEmpty()) {
            return base;
        }
        return base + " · " + actionLabel.trim();
    }
}

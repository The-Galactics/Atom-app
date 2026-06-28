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

    /**
     * Body for the brief cross-app completion notification: the chain's final result
     * message, trimmed and truncated to {@code maxChars} (last char becomes an ellipsis
     * when it overflows), or empty when there is no message — the "Done" title then
     * stands alone. Keeps the result readable in a single notification line.
     */
    public static String completionBody(String message, int maxChars) {
        if (message == null) {
            return "";
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty() || maxChars <= 0 || trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars - 1).trim() + "…";
    }
}

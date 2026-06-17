package com.atom.domain.action;

/**
 * Result of attempting to execute a {@link ResolvedAction} on the device.
 *
 * @param success true when the action was dispatched/performed successfully
 * @param message short user-facing detail (why it failed, or a confirmation)
 */
public record ActionOutcome(boolean success, String message) {

    public static ActionOutcome ok(String message) {
        return new ActionOutcome(true, message);
    }

    public static ActionOutcome failed(String message) {
        return new ActionOutcome(false, message);
    }
}

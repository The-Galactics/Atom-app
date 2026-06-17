package com.atom.domain.action;

/**
 * On-device action catalog. Mirrors the backend action catalog
 * (single source of truth: {@code Atom-agent/domain/intent/catalog.py} and
 * {@code Atom-agent/INTENT_ACTIONS_CONTRACT.md}).
 *
 * <p>The backend returns {@code action_type} as a free-form string. Any value
 * this client does not recognise (e.g. the backend ships a new action before
 * the app is updated) maps to {@link #NONE} so older clients degrade to a
 * conversational reply instead of crashing — see {@link #fromWire(String)}.
 */
public enum ActionType {

    /** {@code {app_name}} — launch an installed app. */
    OPEN_APP,
    /** {@code {target}} — place a phone call (sensitive: confirm first). */
    MAKE_CALL,
    /** {@code {recipient, body, app?}} — send a message (sensitive: confirm first). */
    SEND_MESSAGE,
    /** {@code {time "HH:MM", label?}} — create an alarm. */
    SET_ALARM,
    /** {@code {duration_seconds, label?}} — start a countdown timer. */
    SET_TIMER,
    /** {@code {setting, state}} — toggle wifi/bluetooth/flashlight/do_not_disturb. */
    TOGGLE_SETTING,
    /** No action — just present {@code out_message}. Also the unknown/fallback value. */
    NONE;

    /**
     * Maps a backend {@code action_type} wire string to this enum.
     * Unknown or blank values are forward-compatibly treated as {@link #NONE}.
     */
    public static ActionType fromWire(String wire) {
        if (wire == null || wire.isBlank()) {
            return NONE;
        }
        try {
            return ActionType.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException unknownAction) {
            // Client older than backend: degrade to a conversational turn.
            return NONE;
        }
    }
}

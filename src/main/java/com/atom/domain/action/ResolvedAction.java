package com.atom.domain.action;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable result of interpreting a user order. This is the domain-side
 * projection of the backend {@code CommandResponse}: the gRPC adapter parses
 * {@code parameters_json} into the {@code parameters} map and maps the wire
 * {@code action_type} string into {@link ActionType} before constructing this
 * value object, so the domain stays free of JSON/transport concerns.
 *
 * @param type                 resolved action (never null; unknown ⇒ {@link ActionType#NONE})
 * @param parameters           action slots (immutable; empty for {@link ActionType#NONE})
 * @param outMessage           natural-language reply to speak/show the user
 * @param confidence           recognition confidence in [0.0, 1.0]
 * @param requiresConfirmation true ⇒ confirm with the user before executing
 */
public record ResolvedAction(
        ActionType type,
        Map<String, String> parameters,
        String outMessage,
        float confidence,
        boolean requiresConfirmation) {

    public ResolvedAction {
        if (type == null) {
            type = ActionType.NONE;
        }
        parameters = parameters == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(parameters);
        if (outMessage == null) {
            outMessage = "";
        }
    }

    /** A purely conversational result carrying only the reply text. */
    public static ResolvedAction conversation(String outMessage) {
        return new ResolvedAction(ActionType.NONE, Collections.emptyMap(), outMessage, 0.0f, false);
    }

    /** True when there is something to execute on the device. */
    public boolean isExecutable() {
        return type != ActionType.NONE;
    }

    /** Convenience accessor for a single slot; null when absent. */
    public String param(String key) {
        return parameters.get(key);
    }
}

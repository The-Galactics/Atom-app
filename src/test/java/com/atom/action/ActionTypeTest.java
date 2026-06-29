package com.atom.action;

import com.atom.domain.action.ActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ActionType#fromWire(String)}, covering the new
 * accessibility-powered actions and forward-compatible fallback.
 */
class ActionTypeTest {

    @Test
    @DisplayName("New accessibility action strings resolve to their enum value")
    void resolvesNewActions() {
        assertThat(ActionType.fromWire("NAVIGATE")).isEqualTo(ActionType.NAVIGATE);
        assertThat(ActionType.fromWire("SCROLL")).isEqualTo(ActionType.SCROLL);
        assertThat(ActionType.fromWire("READ_SCREEN")).isEqualTo(ActionType.READ_SCREEN);
        assertThat(ActionType.fromWire("TAP_ELEMENT")).isEqualTo(ActionType.TAP_ELEMENT);
        assertThat(ActionType.fromWire("TYPE_TEXT")).isEqualTo(ActionType.TYPE_TEXT);
    }

    @Test
    @DisplayName("TYPE_TEXT resolves regardless of case/whitespace")
    void resolvesTypeText() {
        assertThat(ActionType.fromWire("type_text")).isEqualTo(ActionType.TYPE_TEXT);
        assertThat(ActionType.fromWire("  TYPE_TEXT ")).isEqualTo(ActionType.TYPE_TEXT);
    }

    @Test
    @DisplayName("Wire strings are case-insensitive and trimmed")
    void normalisesCaseAndWhitespace() {
        assertThat(ActionType.fromWire("  navigate ")).isEqualTo(ActionType.NAVIGATE);
        assertThat(ActionType.fromWire("Tap_Element")).isEqualTo(ActionType.TAP_ELEMENT);
    }

    @Test
    @DisplayName("Unknown or blank action strings degrade to NONE")
    void unknownFallsBackToNone() {
        assertThat(ActionType.fromWire("WARP_DRIVE")).isEqualTo(ActionType.NONE);
        assertThat(ActionType.fromWire("")).isEqualTo(ActionType.NONE);
        assertThat(ActionType.fromWire(null)).isEqualTo(ActionType.NONE);
    }
}

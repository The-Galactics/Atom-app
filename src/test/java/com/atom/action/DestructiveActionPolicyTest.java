package com.atom.action;

import static org.assertj.core.api.Assertions.assertThat;

import com.atom.domain.action.ActionType;
import com.atom.domain.action.DestructiveActionPolicy;
import com.atom.domain.action.ResolvedAction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Unit tests for {@link DestructiveActionPolicy}: a TAP_ELEMENT whose target text
 * or params hint at a destructive operation must be confirmed; everything else runs
 * hands-free. Matching is accent- and case-insensitive.
 */
class DestructiveActionPolicyTest {

    private DestructiveActionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DestructiveActionPolicy();
    }

    private static ResolvedAction tap(Map<String, String> params) {
        return new ResolvedAction(ActionType.TAP_ELEMENT, params, "", 1.0f, false);
    }

    @Test
    @DisplayName("TAP_ELEMENT with a destructive keyword requires confirmation")
    void flagsDestructiveTap() {
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Eliminar cuenta")))).isTrue();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Delete photo")))).isTrue();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Uninstall app")))).isTrue();
    }

    @Test
    @DisplayName("Matching is accent- and case-insensitive")
    void accentAndCaseInsensitive() {
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "ELIMINÁR")))).isTrue();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Désinstalar")))).isTrue();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "BORRAR todo")))).isTrue();
    }

    @Test
    @DisplayName("A destructive keyword in any param slot is flagged, not just text")
    void scansAllParamSlots() {
        assertThat(policy.requiresConfirmation(tap(Map.of("label", "formatear tarjeta")))).isTrue();
    }

    @Test
    @DisplayName("A benign TAP_ELEMENT runs without confirmation")
    void allowsBenignTap() {
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Aceptar")))).isFalse();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Search")))).isFalse();
        assertThat(policy.requiresConfirmation(tap(Map.of()))).isFalse();
    }

    @Test
    @DisplayName("High-stakes communication/financial keywords require confirmation")
    void flagsHighStakesActions() {
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Llamar…")))).isTrue();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Call Mom")))).isTrue();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Pay Now")))).isTrue();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Transferir dinero")))).isTrue();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Subscribe")))).isTrue();
        // Accented case: "crédito" must normalize so the "comprar" token still matches.
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Comprar crédito")))).isTrue();
    }

    @Test
    @DisplayName("Short keywords use token boundaries and do not false-positive in longer words")
    void avoidsSubstringFalsePositives() {
        // "Display" contains "pay", "Recall" contains "call", "Dialog" contains "dial",
        // "Buyer" contains "buy" — token matching must keep these auto-approved.
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Display settings")))).isFalse();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Recall")))).isFalse();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Dialog")))).isFalse();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Buyer profile")))).isFalse();
        assertThat(policy.requiresConfirmation(tap(Map.of("text", "Open YouTube")))).isFalse();
    }

    @Test
    @DisplayName("MAKE_CALL is not locally gated: the backend handshake confirms it")
    void doesNotGateMakeCallByType() {
        ResolvedAction call = new ResolvedAction(
                ActionType.MAKE_CALL, Map.of("target", "Mom"), "", 1.0f, false);
        assertThat(policy.requiresConfirmation(call)).isFalse();
    }

    @Test
    @DisplayName("SEND_MESSAGE is not locally gated: the backend handshake confirms it")
    void doesNotGateSendMessageByType() {
        ResolvedAction msg = new ResolvedAction(
                ActionType.SEND_MESSAGE, Map.of("recipient", "Ana", "body", "hola"), "", 1.0f, false);
        assertThat(policy.requiresConfirmation(msg)).isFalse();
    }

    @Test
    @DisplayName("Other non-TAP actions are never flagged, even with a destructive keyword")
    void neverFlagsNonTapActions() {
        ResolvedAction openApp = new ResolvedAction(
                ActionType.OPEN_APP, Map.of("app_name", "eliminar"), "", 1.0f, false);
        ResolvedAction navigate = new ResolvedAction(
                ActionType.NAVIGATE, Map.of("direction", "delete"), "", 1.0f, false);
        ResolvedAction none = new ResolvedAction(
                ActionType.NONE, Map.of("text", "borrar"), "", 1.0f, false);

        assertThat(policy.requiresConfirmation(openApp)).isFalse();
        assertThat(policy.requiresConfirmation(navigate)).isFalse();
        assertThat(policy.requiresConfirmation(none)).isFalse();
    }

    @Test
    @DisplayName("Null action is safe (no confirmation)")
    void nullActionIsSafe() {
        assertThat(policy.requiresConfirmation(null)).isFalse();
    }
}

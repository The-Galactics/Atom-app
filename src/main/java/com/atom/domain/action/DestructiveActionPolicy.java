package com.atom.domain.action;

import java.text.Normalizer;
import java.util.Set;

/**
 * Pure safety check for the autonomous loop, with two layers (2B.1):
 * <ul>
 *   <li><b>Positive by type:</b> intrinsically sensitive actions ({@link ActionType#MAKE_CALL},
 *       {@link ActionType#SEND_MESSAGE}) ALWAYS require confirmation, regardless of params,
 *       keywords or the backend's {@code requires_confirmation} flag.</li>
 *   <li><b>Contextual:</b> a {@link ActionType#TAP_ELEMENT} whose target text/params hint at a
 *       destructive operation (delete, pay, call…) must also be confirmed.</li>
 * </ul>
 * Every other action is hands-free. No Android deps — trivially testable.
 */
public final class DestructiveActionPolicy {

    // Intrinsically sensitive action types: confirmation is mandatory on every step of the
    // loop, never auto-approved (purchases arrive as TAP_ELEMENT and are covered by keywords).
    private static final Set<ActionType> ALWAYS_CONFIRM = Set.of(
            ActionType.MAKE_CALL, ActionType.SEND_MESSAGE);

    // es/en high-stakes verbs, accent-stripped and lower-cased. Matched on whole tokens
    // (see #containsKeyword), not substrings, so "pay"/"call" don't hit "display"/"recall".
    private static final Set<String> DESTRUCTIVE_KEYWORDS = Set.of(
            // Delete-type (original gate)
            "eliminar", "borrar", "delete", "remove", "desinstalar", "uninstall", "formatear",
            // Communication
            "llamar", "call", "marcar", "dial",
            // Financial / purchases
            "pagar", "pay", "comprar", "buy", "transferir", "transfer", "suscribir", "subscribe",
            "checkout");

    public DestructiveActionPolicy() {
    }

    /** True when {@code action} is intrinsically sensitive by type, or a TAP_ELEMENT
     *  targeting a destructive keyword. */
    public boolean requiresConfirmation(ResolvedAction action) {
        if (action == null) {
            return false;
        }
        // Positive-by-type: always confirm, independent of params/keywords/backend flag.
        if (ALWAYS_CONFIRM.contains(action.type())) {
            return true;
        }
        if (action.type() != ActionType.TAP_ELEMENT) {
            return false;
        }
        String haystack = normalize(action.param("text"));
        if (containsKeyword(haystack)) {
            return true;
        }
        // Some actions carry the visible target under other slots; scan them all.
        for (String value : action.parameters().values()) {
            if (containsKeyword(normalize(value))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsKeyword(String normalized) {
        if (normalized.isEmpty()) {
            return false;
        }
        // Split on non-letters and match whole tokens, so short keywords don't hit inside
        // longer words ("pay" in "display") while "Pay Now"/"Call Mom" still match.
        for (String token : normalized.split("[^\\p{L}]+")) {
            if (!token.isEmpty() && DESTRUCTIVE_KEYWORDS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /** Lower-cases and strips diacritics so "Eliminár" matches "eliminar". */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(java.util.Locale.ROOT);
    }
}

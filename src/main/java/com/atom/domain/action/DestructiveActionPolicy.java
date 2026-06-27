package com.atom.domain.action;

import java.text.Normalizer;
import java.util.Set;

/**
 * Pure safety check for the autonomous loop — the CONTEXTUAL layer only (2B.1):
 * a {@link ActionType#TAP_ELEMENT} whose target text/params hint at a destructive
 * operation (delete, pay, call…) must be confirmed locally before it runs.
 *
 * <p>Intrinsically sensitive actions ({@link ActionType#MAKE_CALL},
 * {@link ActionType#SEND_MESSAGE}) are NO LONGER confirmed here: the backend now
 * HOLDS them and asks the user out loud via the {@code awaiting_confirmation}
 * handshake (a held NONE turn whose spoken sí/no the loop captures), so a single
 * confirmation drives the whole flow. Double-gating them locally would re-block
 * the already-confirmed action.
 *
 * <p>Every other action is hands-free. No Android deps — trivially testable.
 */
public final class DestructiveActionPolicy {

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

    /** True when {@code action} is a TAP_ELEMENT targeting a destructive keyword.
     *  Intrinsically sensitive types (call/message) are confirmed by the backend
     *  handshake, not here. */
    public boolean requiresConfirmation(ResolvedAction action) {
        if (action == null) {
            return false;
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

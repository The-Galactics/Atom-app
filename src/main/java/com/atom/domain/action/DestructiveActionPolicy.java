package com.atom.domain.action;

import java.text.Normalizer;
import java.util.Set;

/**
 * Pure, contextual-semantic safety check for the autonomous loop. A
 * {@link ActionType#TAP_ELEMENT} whose target text/params hint at a destructive
 * operation (delete, uninstall, format…) must be confirmed by the user before it
 * runs; every other action is hands-free. No Android deps — trivially testable.
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

    /** True when {@code action} is a TAP_ELEMENT targeting a destructive keyword. */
    public boolean requiresConfirmation(ResolvedAction action) {
        if (action == null || action.type() != ActionType.TAP_ELEMENT) {
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

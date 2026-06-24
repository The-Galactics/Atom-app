package com.atom.domain.utils;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Shared, accent-insensitive text folding for name matching.
 *
 * <p>Used symmetrically wherever a user-spoken name is compared against an
 * on-device label (contact display names, on-screen chat/element text). Plain
 * {@code toLowerCase} only handles ASCII case, so an uppercase accented name
 * such as {@code "MARÍA"} never matches {@code "maria"}. Folding to NFD and
 * stripping combining marks removes the accent before lowercasing, so both
 * operands collapse to the same ASCII-ish form.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /**
     * Locale-aware, accent-insensitive fold: NFD-decompose, strip combining
     * marks, trim, lowercase. {@code null} folds to {@code ""}.
     *
     * <p>{@link Locale#ROOT} is used deliberately; Turkish dotted/dotless I is
     * a known non-goal edge case for this matcher.
     */
    public static String fold(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{Mn}+", "");
        return n.trim().toLowerCase(Locale.ROOT);
    }
}

package com.atom.domain.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TextNormalizer#fold(String)}: accent-insensitive,
 * case-insensitive folding used for symmetric name matching across the
 * contact resolver and the on-screen tap pipeline.
 */
class TextNormalizerTest {

    @Test
    @DisplayName("Accented uppercase folds to the same value as plain lowercase")
    void foldsAccentedUppercaseToLowercaseAscii() {
        assertThat(TextNormalizer.fold("JOSÉ")).isEqualTo(TextNormalizer.fold("jose"));
        assertThat(TextNormalizer.fold("José")).isEqualTo(TextNormalizer.fold("jose"));
        assertThat(TextNormalizer.fold("ÁNGEL")).isEqualTo(TextNormalizer.fold("angel"));
        assertThat(TextNormalizer.fold("MÜLLER")).isEqualTo(TextNormalizer.fold("muller"));
        assertThat(TextNormalizer.fold("MARÍA")).isEqualTo(TextNormalizer.fold("maria"));
    }

    @Test
    @DisplayName("Folding trims surrounding whitespace and lowercases")
    void foldsTrimAndLowercase() {
        assertThat(TextNormalizer.fold("  Ana  ")).isEqualTo("ana");
    }

    @Test
    @DisplayName("Null folds to empty string")
    void foldsNullToEmpty() {
        assertThat(TextNormalizer.fold(null)).isEqualTo("");
    }
}

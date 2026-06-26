package com.atom.infrastructure.adapter.accessibility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AtomAccessibilityService#rankFor(String, String)}, the
 * package-private JVM entry point over the same folded ranking logic used by the
 * accessibility node DFS. Covers the word-boundary tier and the short-needle
 * substring guard that prevent a short query from tapping the wrong chat
 * (e.g. needle "ana" must not match the label "susana").
 */
class AtomAccessibilityServiceRankTest {

    // Mirrors the ordering of the private RANK_* constants (lower is better).
    private static final int RANK_EXACT = 0;
    private static final int RANK_PREFIX = 1;
    private static final int RANK_WORD = 2;
    private static final int RANK_SUBSTRING = 3;
    private static final int NO_MATCH = -1;

    @Test
    @DisplayName("Exact match (accent/case-insensitive) is the highest rank")
    void exactMatchIsBest() {
        assertThat(AtomAccessibilityService.rankFor("MARÍA", "maría")).isEqualTo(RANK_EXACT);
    }

    @Test
    @DisplayName("A non-leading whole token matches via the word-boundary tier")
    void wholeTokenMatchesAsWord() {
        // "ana" is a whole token but not the prefix, so it lands in the word tier
        // (a leading token would match higher, as RANK_PREFIX).
        assertThat(AtomAccessibilityService.rankFor("luis ana garcía", "ana"))
                .isEqualTo(RANK_WORD);
    }

    @Test
    @DisplayName("Short needle does NOT sub-word match a longer label")
    void shortNeedleNoFalsePositive() {
        assertThat(AtomAccessibilityService.rankFor("susana", "ana")).isEqualTo(NO_MATCH);
    }

    @Test
    @DisplayName("Prefix match ranks above a substring match")
    void prefixMatchesAndOutranksSubstring() {
        int prefix = AtomAccessibilityService.rankFor("josé luis", "jose");
        assertThat(prefix).isEqualTo(RANK_PREFIX);
        assertThat(prefix).isLessThan(RANK_SUBSTRING);
    }

    @Test
    @DisplayName("A long needle (>= 4 chars) is still allowed to match as a bare substring")
    void longNeedleSubstringStillMatches() {
        assertThat(AtomAccessibilityService.rankFor("luisgarcia", "garcia"))
                .isEqualTo(RANK_SUBSTRING);
    }

    @Test
    @DisplayName("Spanish 'foto de perfil' avatar description is a profile image")
    void fotoDePerfilIsProfileImage() {
        assertThat(AtomAccessibilityService.isProfileImageDesc("Foto de perfil de María")).isTrue();
    }

    @Test
    @DisplayName("Spanish 'foto del perfil' variant is a profile image")
    void fotoDelPerfilIsProfileImage() {
        assertThat(AtomAccessibilityService.isProfileImageDesc("Foto del perfil de Juan")).isTrue();
    }

    @Test
    @DisplayName("Bare 'avatar' marker is a profile image")
    void avatarIsProfileImage() {
        assertThat(AtomAccessibilityService.isProfileImageDesc("avatar")).isTrue();
    }

    @Test
    @DisplayName("English 'profile picture' marker is a profile image")
    void profilePictureIsProfileImage() {
        assertThat(AtomAccessibilityService.isProfileImageDesc("profile picture")).isTrue();
    }

    @Test
    @DisplayName("Marker matching is case/accent insensitive")
    void profileImageMarkerIsCaseInsensitive() {
        assertThat(AtomAccessibilityService.isProfileImageDesc("FOTO DE PERFIL")).isTrue();
    }

    @Test
    @DisplayName("An icon button label ('Ajustes') is NOT a profile image")
    void iconButtonIsNotProfileImage() {
        assertThat(AtomAccessibilityService.isProfileImageDesc("Ajustes")).isFalse();
    }

    @Test
    @DisplayName("A plain contact name is NOT a profile image")
    void contactNameIsNotProfileImage() {
        assertThat(AtomAccessibilityService.isProfileImageDesc("María")).isFalse();
    }

    @Test
    @DisplayName("Null and empty descriptions are NOT profile images")
    void nullAndEmptyAreNotProfileImage() {
        assertThat(AtomAccessibilityService.isProfileImageDesc(null)).isFalse();
        assertThat(AtomAccessibilityService.isProfileImageDesc("")).isFalse();
    }
}

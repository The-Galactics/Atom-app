package com.atom.domain.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenPairTest {

    @Test
    void fromExpiresIn_computesAbsoluteExpiry() {
        TokenPair pair = TokenPair.fromExpiresIn("acc", "ref", 900, 1_000);
        assertThat(pair.getAccessToken()).isEqualTo("acc");
        assertThat(pair.getRefreshToken()).isEqualTo("ref");
        assertThat(pair.getExpiresAtEpochSeconds()).isEqualTo(1_900);
    }

    @Test
    void isAccessExpired_trueWhenWithinMargin() {
        TokenPair pair = new TokenPair("acc", "ref", 1_900);
        assertThat(pair.isAccessExpired(1_850, 60)).isTrue();   // 1850 + 60 >= 1900
        assertThat(pair.isAccessExpired(1_839, 60)).isFalse();  // 1839 + 60 < 1900
    }
}

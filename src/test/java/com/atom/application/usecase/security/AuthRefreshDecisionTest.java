package com.atom.application.usecase.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AuthRefreshDecisionTest {

    @Test
    void refreshesWhenExpiryUnknown() {
        assertThat(AuthUseCase.shouldRefresh(1000L, 0L, 60L)).isTrue();
    }

    @Test
    void refreshesWithinMargin() {
        // now + margin (1000+60=1060) >= expiresAt (1050) -> refresh
        assertThat(AuthUseCase.shouldRefresh(1000L, 1050L, 60L)).isTrue();
    }

    @Test
    void refreshesExactlyAtMargin() {
        assertThat(AuthUseCase.shouldRefresh(1000L, 1060L, 60L)).isTrue();
    }

    @Test
    void skipsWhenComfortablyFresh() {
        assertThat(AuthUseCase.shouldRefresh(1000L, 2000L, 60L)).isFalse();
    }

    @Test
    void refreshesWhenExpiryNegative() {
        assertThat(AuthUseCase.shouldRefresh(1000L, -1L, 60L)).isTrue();
    }
}

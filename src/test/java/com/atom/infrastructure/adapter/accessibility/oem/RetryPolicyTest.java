package com.atom.infrastructure.adapter.accessibility.oem;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {
    @Test
    void backoffGrowsGeometricallyThenCaps() {
        RetryPolicy p = new RetryPolicy(4, 50L, 2.0, 200L, 800L);
        assertThat(p.backoff(0)).isEqualTo(50L);
        assertThat(p.backoff(1)).isEqualTo(100L);
        assertThat(p.backoff(2)).isEqualTo(200L);
        assertThat(p.backoff(3)).isEqualTo(200L); // capped at maxDelayMs
    }

    @Test
    void backoffNeverNegativeForAttemptZero() {
        assertThat(RetryPolicy.aggressive().backoff(0)).isGreaterThan(0L);
    }
}

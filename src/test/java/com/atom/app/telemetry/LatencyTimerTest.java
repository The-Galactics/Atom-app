// src/test/java/com/atom/app/telemetry/LatencyTimerTest.java
package com.atom.app.telemetry;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LatencyTimerTest {

    @Test
    void toMillis_convertsNanosToMillis() {
        assertThat(LatencyTimer.toMillis(0L, 5_000_000L)).isEqualTo(5L);
        assertThat(LatencyTimer.toMillis(1_000_000L, 4_000_000L)).isEqualTo(3L);
    }

    @Test
    void formatLine_emitsTaggedKeyValues() {
        assertThat(LatencyTimer.formatLine("recognize", 12L, 340L, 360L))
                .isEqualTo("AtomLatency tag=recognize refresh_ms=12 rpc_ms=340 total_ms=360");
    }
}

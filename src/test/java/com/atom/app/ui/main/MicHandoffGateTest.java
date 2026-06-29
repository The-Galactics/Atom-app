package com.atom.app.ui.main;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MicHandoffGateTest {

    @Test
    void firesExactlyOnce() {
        MicHandoffGate gate = new MicHandoffGate();
        assertThat(gate.fire()).isTrue();   // first wins (release signal or cap, whichever first)
        assertThat(gate.fire()).isFalse();  // the loser is a no-op
        assertThat(gate.fire()).isFalse();
    }
}

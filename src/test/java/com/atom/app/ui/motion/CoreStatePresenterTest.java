// src/test/java/com/atom/app/ui/motion/CoreStatePresenterTest.java
package com.atom.app.ui.motion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoreStatePresenterTest {

    @Test
    void idleIsCalm() {
        assertThat(CoreStatePresenter.energyFor(CoreState.IDLE)).isEqualTo(0.0f);
    }

    @Test
    void listeningIsFullEnergy() {
        assertThat(CoreStatePresenter.energyFor(CoreState.LISTENING)).isEqualTo(1.0f);
    }

    @Test
    void thinkingAndOperatingShareWorkingEnergy() {
        assertThat(CoreStatePresenter.energyFor(CoreState.THINKING)).isEqualTo(0.6f);
        assertThat(CoreStatePresenter.energyFor(CoreState.OPERATING)).isEqualTo(0.6f);
    }

    @Test
    void respondedAndErrorEaseBackToIdle() {
        assertThat(CoreStatePresenter.energyFor(CoreState.RESPONDED)).isEqualTo(0.0f);
        assertThat(CoreStatePresenter.energyFor(CoreState.ERROR)).isEqualTo(0.0f);
    }

    @Test
    void timingConstantsMatchTheBrandValues() {
        assertThat(CoreStatePresenter.TEXT_FADE_OUT_MS).isEqualTo(120L);
        assertThat(CoreStatePresenter.TEXT_FADE_IN_MS).isEqualTo(160L);
    }
}

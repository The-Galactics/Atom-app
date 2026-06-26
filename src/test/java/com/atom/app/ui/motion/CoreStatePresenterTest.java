// src/test/java/com/atom/app/ui/motion/CoreStatePresenterTest.java
package com.atom.app.ui.motion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoreStatePresenterTest {

    @Test
    void idleIsCalmWithDimGlow() {
        assertThat(CoreStatePresenter.energyFor(CoreState.IDLE)).isEqualTo(0.0f);
        assertThat(CoreStatePresenter.glowFor(CoreState.IDLE)).isEqualTo(0.35f);
    }

    @Test
    void listeningIsFullEnergyAndActiveGlow() {
        assertThat(CoreStatePresenter.energyFor(CoreState.LISTENING)).isEqualTo(1.0f);
        assertThat(CoreStatePresenter.glowFor(CoreState.LISTENING)).isEqualTo(0.7f);
    }

    @Test
    void thinkingAndOperatingShareWorkingEnergy() {
        assertThat(CoreStatePresenter.energyFor(CoreState.THINKING)).isEqualTo(0.6f);
        assertThat(CoreStatePresenter.energyFor(CoreState.OPERATING)).isEqualTo(0.6f);
        assertThat(CoreStatePresenter.glowFor(CoreState.THINKING)).isEqualTo(0.7f);
    }

    @Test
    void respondedAndErrorEaseBackToIdle() {
        assertThat(CoreStatePresenter.energyFor(CoreState.RESPONDED)).isEqualTo(0.0f);
        assertThat(CoreStatePresenter.glowFor(CoreState.RESPONDED)).isEqualTo(0.35f);
        assertThat(CoreStatePresenter.energyFor(CoreState.ERROR)).isEqualTo(0.0f);
        assertThat(CoreStatePresenter.glowFor(CoreState.ERROR)).isEqualTo(0.35f);
    }

    @Test
    void timingConstantsMatchTheBrandValues() {
        assertThat(CoreStatePresenter.TEXT_FADE_OUT_MS).isEqualTo(120L);
        assertThat(CoreStatePresenter.TEXT_FADE_IN_MS).isEqualTo(160L);
        assertThat(CoreStatePresenter.CORE_GLOW_ANIM_MS).isEqualTo(280L);
    }
}

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

    @Test
    void thinkingGathersInwardInLavender() {
        CoreStyle s = CoreStatePresenter.styleFor(CoreState.THINKING);
        assertThat(s.energy).isEqualTo(0.6f);
        assertThat(s.hueShift).isEqualTo(0.0f);
        assertThat(s.motion).isEqualTo(MotionProfile.GATHER);
        assertThat(s.oneShot).isEqualTo(Transient.NONE);
    }

    @Test
    void operatingScansInTeal() {
        CoreStyle s = CoreStatePresenter.styleFor(CoreState.OPERATING);
        assertThat(s.energy).isEqualTo(0.6f);
        assertThat(s.hueShift).isEqualTo(1.0f);
        assertThat(s.motion).isEqualTo(MotionProfile.SCAN);
    }

    @Test
    void respondedBloomsAndErrorShudders() {
        assertThat(CoreStatePresenter.styleFor(CoreState.RESPONDED).oneShot).isEqualTo(Transient.BLOOM);
        assertThat(CoreStatePresenter.styleFor(CoreState.ERROR).oneShot).isEqualTo(Transient.SHUDDER);
    }

    @Test
    void idleAndListeningKeepTheirEnergyAndAreLavender() {
        assertThat(CoreStatePresenter.styleFor(CoreState.IDLE).energy).isEqualTo(0.0f);
        assertThat(CoreStatePresenter.styleFor(CoreState.IDLE).motion).isEqualTo(MotionProfile.BREATHE);
        assertThat(CoreStatePresenter.styleFor(CoreState.LISTENING).energy).isEqualTo(1.0f);
        assertThat(CoreStatePresenter.styleFor(CoreState.LISTENING).motion).isEqualTo(MotionProfile.PULSE);
        assertThat(CoreStatePresenter.styleFor(CoreState.LISTENING).hueShift).isEqualTo(0.0f);
    }

    @Test
    void energyForStillMatchesStyleForEnergy() {
        for (CoreState state : CoreState.values()) {
            assertThat(CoreStatePresenter.energyFor(state))
                    .isEqualTo(CoreStatePresenter.styleFor(state).energy);
        }
    }
}

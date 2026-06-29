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

    @Test
    void reducedMotionIsANoOpWhenMotionAllowed() {
        CoreStyle s = CoreStatePresenter.styleFor(CoreState.OPERATING);
        assertThat(CoreStatePresenter.resolveForReducedMotion(s, false)).isSameAs(s);
    }

    @Test
    void reducedMotionKeepsEnergyAndHueButCalmsMotion() {
        CoreStyle operating = CoreStatePresenter.styleFor(CoreState.OPERATING);
        CoreStyle r = CoreStatePresenter.resolveForReducedMotion(operating, true);
        assertThat(r.energy).isEqualTo(0.6f);   // still reads as "working"
        assertThat(r.hueShift).isEqualTo(1.0f); // still teal -> still "operating"
        assertThat(r.motion).isEqualTo(MotionProfile.BREATHE);
        assertThat(r.oneShot).isEqualTo(Transient.NONE);
    }

    @Test
    void reducedMotionStripsBloomAndShudder() {
        assertThat(CoreStatePresenter.resolveForReducedMotion(
                CoreStatePresenter.styleFor(CoreState.RESPONDED), true).oneShot).isEqualTo(Transient.NONE);
        assertThat(CoreStatePresenter.resolveForReducedMotion(
                CoreStatePresenter.styleFor(CoreState.ERROR), true).oneShot).isEqualTo(Transient.NONE);
    }

    @Test
    void reducedMotionSuccessHoldsBrighterThanIdle() {
        CoreStyle r = CoreStatePresenter.resolveForReducedMotion(
                CoreStatePresenter.styleFor(CoreState.RESPONDED), true);
        assertThat(r.energy).isGreaterThan(0.0f);
        assertThat(r.desaturate).isFalse();
        assertThat(r.motion).isEqualTo(MotionProfile.BREATHE);
        assertThat(r.oneShot).isEqualTo(Transient.NONE);
    }

    @Test
    void reducedMotionErrorDesaturates() {
        CoreStyle r = CoreStatePresenter.resolveForReducedMotion(
                CoreStatePresenter.styleFor(CoreState.ERROR), true);
        assertThat(r.desaturate).isTrue();
        assertThat(r.motion).isEqualTo(MotionProfile.BREATHE);
        assertThat(r.oneShot).isEqualTo(Transient.NONE);
    }

    @Test
    void reducedMotionIdleAndWorkingAreNotDesaturated() {
        for (CoreState s : new CoreState[]{
                CoreState.IDLE, CoreState.THINKING, CoreState.OPERATING, CoreState.LISTENING}) {
            assertThat(CoreStatePresenter.resolveForReducedMotion(
                    CoreStatePresenter.styleFor(s), true).desaturate).isFalse();
        }
    }

    @Test
    void coreStyleFourArgDefaultsDesaturateFalse() {
        assertThat(new CoreStyle(0.5f, 0.2f, MotionProfile.BREATHE, Transient.NONE).desaturate).isFalse();
    }
}

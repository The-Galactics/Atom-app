package com.atom.app.ui.motion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MotionPreferencesTest {

    @Test
    void scaleZeroMeansReducedMotion() {
        assertThat(MotionPreferences.isReducedMotion(0f)).isTrue();
    }

    @Test
    void scaleOneMeansMotionEnabled() {
        assertThat(MotionPreferences.isReducedMotion(1f)).isFalse();
    }

    @Test
    void anyPositiveScaleMeansMotionEnabled() {
        assertThat(MotionPreferences.isReducedMotion(0.5f)).isFalse();
    }
}

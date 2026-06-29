package com.atom.app.ui.motion;

/**
 * Decides whether on-screen motion should be suppressed. The system exposes this
 * via Settings.Global.TRANSITION_ANIMATION_SCALE (0 == "remove animations").
 * The Android read stays at the call site; this class is a pure predicate so it
 * unit-tests without the framework.
 */
public final class MotionPreferences {

    private MotionPreferences() {}

    public static boolean isReducedMotion(float transitionAnimationScale) {
        return transitionAnimationScale == 0f;
    }
}

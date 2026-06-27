package com.atom.app.ui.motion;

/**
 * Single source of truth for how a {@link CoreState} maps to the atom core's
 * energy, plus the shared status-text crossfade timings. Pure Java (no Android
 * imports) so it unit-tests directly and both the home screen and the overlay can
 * share one motion vocabulary.
 *
 * <p>The atmosphere glow is no longer a separate value: it is owned by
 * {@code AtomCoreView}, whose breathing glow brightens directly with energy (Epic 1.3
 * folded away the standalone glow View), so the presenter maps state → energy only.
 */
public final class CoreStatePresenter {

    public static final long TEXT_FADE_OUT_MS = 120L;
    public static final long TEXT_FADE_IN_MS = 160L;

    private static final float ENERGY_IDLE = 0.0f;
    private static final float ENERGY_THINKING = 0.6f;
    private static final float ENERGY_LISTENING = 1.0f;

    private static final float HUE_LAVENDER = 0.0f;
    private static final float HUE_TEAL = 1.0f;

    private CoreStatePresenter() {}

    public static CoreStyle styleFor(CoreState state) {
        switch (state) {
            case LISTENING:
                return new CoreStyle(ENERGY_LISTENING, HUE_LAVENDER, MotionProfile.PULSE, Transient.NONE);
            case THINKING:
                return new CoreStyle(ENERGY_THINKING, HUE_LAVENDER, MotionProfile.GATHER, Transient.NONE);
            case OPERATING:
                return new CoreStyle(ENERGY_THINKING, HUE_TEAL, MotionProfile.SCAN, Transient.NONE);
            case RESPONDED:
                return new CoreStyle(ENERGY_IDLE, HUE_LAVENDER, MotionProfile.BREATHE, Transient.BLOOM);
            case ERROR:
                return new CoreStyle(ENERGY_IDLE, HUE_LAVENDER, MotionProfile.BREATHE, Transient.SHUDDER);
            case IDLE:
            default:
                return new CoreStyle(ENERGY_IDLE, HUE_LAVENDER, MotionProfile.BREATHE, Transient.NONE);
        }
    }

    public static float energyFor(CoreState state) {
        return styleFor(state).energy;
    }

    /**
     * Returns a motion-suppressed variant for users who disabled system animations.
     * Energy and hue are static properties and stay (they remain the differentiators);
     * dramatic motion profiles calm to BREATHE and one-shot transients are dropped.
     * Returns the same instance unchanged when motion is allowed.
     */
    public static CoreStyle resolveForReducedMotion(CoreStyle style, boolean reducedMotion) {
        if (!reducedMotion) {
            return style;
        }
        return new CoreStyle(style.energy, style.hueShift, MotionProfile.BREATHE, Transient.NONE);
    }
}

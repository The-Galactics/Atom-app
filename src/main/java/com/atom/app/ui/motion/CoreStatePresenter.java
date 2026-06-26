package com.atom.app.ui.motion;

/**
 * Single source of truth for how a {@link CoreState} maps to the atom core's
 * energy and the surrounding glow alpha, plus the shared status-text crossfade
 * timings. Pure Java (no Android imports) so it unit-tests directly and both the
 * home screen and the overlay can share one motion vocabulary.
 *
 * Values are copied verbatim from the former MainActivity constants so existing
 * behaviour is preserved.
 */
public final class CoreStatePresenter {

    public static final long TEXT_FADE_OUT_MS = 120L;
    public static final long TEXT_FADE_IN_MS = 160L;
    public static final long CORE_GLOW_ANIM_MS = 280L;

    private static final float ENERGY_IDLE = 0.0f;
    private static final float ENERGY_THINKING = 0.6f;
    private static final float ENERGY_LISTENING = 1.0f;
    private static final float GLOW_IDLE = 0.35f;
    private static final float GLOW_ACTIVE = 0.7f;

    private CoreStatePresenter() {}

    public static float energyFor(CoreState state) {
        switch (state) {
            case LISTENING:
                return ENERGY_LISTENING;
            case THINKING:
            case OPERATING:
                return ENERGY_THINKING;
            case IDLE:
            case RESPONDED:
            case ERROR:
            default:
                return ENERGY_IDLE;
        }
    }

    public static float glowFor(CoreState state) {
        switch (state) {
            case LISTENING:
            case THINKING:
            case OPERATING:
                return GLOW_ACTIVE;
            case IDLE:
            case RESPONDED:
            case ERROR:
            default:
                return GLOW_IDLE;
        }
    }
}

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
}

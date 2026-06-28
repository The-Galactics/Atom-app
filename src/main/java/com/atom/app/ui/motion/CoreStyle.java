package com.atom.app.ui.motion;

/**
 * Immutable description of how the atom core should look and move for a state.
 * Pure Java (no Android imports) so it unit-tests directly and both the home
 * screen and the overlay share one vocabulary.
 */
public final class CoreStyle {
    /** Engagement level, 0 = calm idle, 1 = fully engaged. */
    public final float energy;
    /** Hue blend, 0 = brand lavender, 1 = teal "operating" accent. */
    public final float hueShift;
    public final MotionProfile motion;
    public final Transient oneShot;
    /** When true, the core renders desaturated/dimmed (static "muted/error" look). */
    public final boolean desaturate;

    public CoreStyle(float energy, float hueShift, MotionProfile motion, Transient oneShot) {
        this(energy, hueShift, motion, oneShot, false);
    }

    public CoreStyle(float energy, float hueShift, MotionProfile motion, Transient oneShot,
                     boolean desaturate) {
        this.energy = energy;
        this.hueShift = hueShift;
        this.motion = motion;
        this.oneShot = oneShot;
        this.desaturate = desaturate;
    }
}

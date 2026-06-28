package com.atom.app.ui;

/** Component-wise ARGB color interpolation. Pure Java so it unit-tests without Android. */
public final class ColorBlend {

    private ColorBlend() {}

    /** Blends {@code argbA} toward {@code argbB} by {@code t} (clamped to [0,1]). */
    public static int lerp(int argbA, int argbB, float t) {
        float f = t < 0f ? 0f : (t > 1f ? 1f : t);
        int a = channel(argbA, 24, argbB, f);
        int r = channel(argbA, 16, argbB, f);
        int g = channel(argbA, 8, argbB, f);
        int b = channel(argbA, 0, argbB, f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int argbA, int shift, int argbB, float f) {
        int ca = (argbA >> shift) & 0xFF;
        int cb = (argbB >> shift) & 0xFF;
        return (int) (ca + (cb - ca) * f);
    }
}

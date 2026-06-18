package com.atom.app.ui;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Reusable microphone feedback animations shared by the main screen and the
 * floating bubble overlay so both surfaces speak the same motion vocabulary.
 *
 * <p>The press-settle is stateless and exposed as a static helper. The breathing
 * "listening" pulse is stateful (it owns the running {@link ObjectAnimator}), so
 * each surface holds its own {@code MicAnimations} instance and starts/stops the
 * pulse on it. This mirrors the original Phase 2 implementation that lived inline
 * in {@code FloatingBubbleService}; it has simply been lifted out so the logic is
 * defined once and reused, with no behavioural change.
 */
public final class MicAnimations {

    private static final float MIC_PULSE_SCALE = 1.18f;     // peak of the breathing pulse
    private static final long MIC_PULSE_DURATION_MS = 620;  // one half-cycle (grows, then reverses)
    private static final long MIC_PRESS_DURATION_MS = 90;   // tap settle dip

    private ObjectAnimator micPulse;  // infinite "listening" pulse on the mic

    // A quick scale dip-and-recover that acknowledges the tap.
    public static void playPressSettle(View mic) {
        if (mic == null) {
            return;
        }
        mic.animate()
                .scaleX(0.86f).scaleY(0.86f)
                .setDuration(MIC_PRESS_DURATION_MS)
                .withEndAction(() -> mic.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(MIC_PRESS_DURATION_MS + 30)
                        .start())
                .start();
    }

    // Infinite "breathing" pulse (scale + alpha) marking the listening state.
    public void startMicPulse(View mic) {
        if (mic == null) {
            return;
        }
        stopMicPulse(mic);
        PropertyValuesHolder scaleX =
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, MIC_PULSE_SCALE);
        PropertyValuesHolder scaleY =
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, MIC_PULSE_SCALE);
        PropertyValuesHolder alpha =
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.55f);
        micPulse = ObjectAnimator.ofPropertyValuesHolder(mic, scaleX, scaleY, alpha);
        micPulse.setDuration(MIC_PULSE_DURATION_MS);
        micPulse.setRepeatCount(ValueAnimator.INFINITE);
        micPulse.setRepeatMode(ValueAnimator.REVERSE);
        micPulse.setInterpolator(new AccelerateDecelerateInterpolator());
        micPulse.start();
    }

    // Stops the pulse and restores the mic to its resting state. The view may
    // already be detached (panel torn down mid-request); resetting it is safe.
    public void stopMicPulse(View mic) {
        if (micPulse != null) {
            micPulse.cancel();
            micPulse = null;
        }
        if (mic != null) {
            mic.setScaleX(1f);
            mic.setScaleY(1f);
            mic.setAlpha(1f);
        }
    }
}

// src/main/java/com/atom/app/ui/motion/StatusCrossfader.java
package com.atom.app.ui.motion;

import android.widget.TextView;

/**
 * Crossfades a status TextView to new text. Unlike a withEndAction-based swap,
 * this cancels any in-flight fade and commits the latest text immediately, so a
 * rapid sequence of state changes always lands on the final string instead of
 * dropping an interrupted swap.
 */
public final class StatusCrossfader {

    private StatusCrossfader() {}

    public static void swap(TextView view, CharSequence text) {
        if (view == null) {
            return;
        }
        // Cancel any in-flight fade; its withEndAction would not have run on cancel.
        view.animate().cancel();
        view.setText(text);
        view.animate()
                .alpha(0f)
                .setDuration(CoreStatePresenter.TEXT_FADE_OUT_MS)
                .withEndAction(() ->
                        view.animate()
                                .alpha(1f)
                                .setDuration(CoreStatePresenter.TEXT_FADE_IN_MS)
                                .start())
                .start();
    }
}

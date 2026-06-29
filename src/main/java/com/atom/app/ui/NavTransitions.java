package com.atom.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;

import com.atom.app.R;
import com.atom.app.ui.motion.MotionPreferences;

/**
 * On-brand fade-through for Main ↔ History ↔ Settings. Honors the system
 * "remove animations" setting (TRANSITION_ANIMATION_SCALE == 0) via MotionPreferences.
 */
public final class NavTransitions {

    private NavTransitions() {}

    public static void start(Activity from, Class<?> target) {
        from.startActivity(new Intent(from, target));
        apply(from);
    }

    public static void apply(Activity activity) {
        float scale = Settings.Global.getFloat(activity.getContentResolver(),
                Settings.Global.TRANSITION_ANIMATION_SCALE, 1f);
        if (MotionPreferences.isReducedMotion(scale)) {
            return; // respect the user's reduced-motion preference: no custom transition
        }
        activity.overridePendingTransition(R.anim.nav_fade_in, R.anim.nav_fade_out);
    }
}

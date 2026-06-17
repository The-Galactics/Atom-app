package com.atom.app.settings;

import android.content.Context;
import android.content.SharedPreferences;

/** Lightweight wrapper over the app's private SharedPreferences. */
public class AtomPreferences {

    private static final String PREFS_NAME = "atom_prefs";
    private static final String KEY_TTS_ENABLED = "tts_enabled";

    private final SharedPreferences prefs;

    public AtomPreferences(Context context) {
        // Application-scoped store; safe to hold via any Context.
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Spoken responses default to ON (TTS is automatic). */
    public boolean isTtsEnabled() {
        return prefs.getBoolean(KEY_TTS_ENABLED, true);
    }

    public void setTtsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply();
    }
}

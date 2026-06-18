package com.atom.app.settings;

import android.content.Context;
import android.content.SharedPreferences;

/** Lightweight wrapper over the app's private SharedPreferences. */
public class AtomPreferences {

    private static final String PREFS_NAME = "atom_prefs";
    private static final String KEY_TTS_ENABLED = "tts_enabled";
    private static final String KEY_REMOTE_TTS_ENABLED = "remote_tts_enabled";
    private static final String KEY_MIC_MUTED = "mic_muted";
    private static final String KEY_TTS_VOICE = "tts_voice";
    private static final String KEY_TTS_RATE = "tts_rate";

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

    /**
     * Neural backend (Kokoro) voice. Defaults to OFF: on-device TTS is instant
     * and offline, whereas the backend voice adds synthesis latency. Turn this
     * on to prefer the premium neural voice (with on-device TTS as fallback).
     */
    public boolean isRemoteTtsEnabled() {
        return prefs.getBoolean(KEY_REMOTE_TTS_ENABLED, false);
    }

    public void setRemoteTtsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REMOTE_TTS_ENABLED, enabled).apply();
    }

    /** Selected on-device TTS voice name (Voice.getName()); empty = automatic. */
    public String getTtsVoice() {
        return prefs.getString(KEY_TTS_VOICE, "");
    }

    public void setTtsVoice(String voiceName) {
        prefs.edit().putString(KEY_TTS_VOICE, voiceName == null ? "" : voiceName).apply();
    }

    /** Speech rate multiplier (1.0 = normal). Defaults to a gentle 0.9. */
    public float getTtsRate() {
        return prefs.getFloat(KEY_TTS_RATE, 0.9f);
    }

    public void setTtsRate(float rate) {
        prefs.edit().putFloat(KEY_TTS_RATE, rate).apply();
    }

    /** Voice input defaults to unmuted (mic available). */
    public boolean isMicMuted() {
        return prefs.getBoolean(KEY_MIC_MUTED, false);
    }

    public void setMicMuted(boolean muted) {
        prefs.edit().putBoolean(KEY_MIC_MUTED, muted).apply();
    }
}

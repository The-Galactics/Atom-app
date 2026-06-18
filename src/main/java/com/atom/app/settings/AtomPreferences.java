package com.atom.app.settings;

import android.content.Context;
import android.content.SharedPreferences;

/** Lightweight wrapper over the app's private SharedPreferences. */
public class AtomPreferences {

    private static final String PREFS_NAME = "atom_prefs";
    private static final String KEY_TTS_ENABLED = "tts_enabled";
    private static final String KEY_REMOTE_TTS_ENABLED = "remote_tts_enabled";
    public static final String KEY_MIC_MUTED = "mic_muted";
    private static final String KEY_TTS_VOICE = "tts_voice";
    private static final String KEY_TTS_RATE = "tts_rate";
    private static final String KEY_WAKE_ENABLED = "wake_word_enabled";
    private static final String KEY_WAKE_NAME = "wake_word_name";
    private static final String KEY_WAKE_PPN = "wake_word_ppn_path";
    private static final String KEY_WAKE_SCREEN_ON_ONLY = "wake_word_screen_on_only";

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

    /** Wake word ("Hey Atom") detection. Off until the user enables + configures it. */
    public boolean isWakeWordEnabled() {
        return prefs.getBoolean(KEY_WAKE_ENABLED, false);
    }

    public void setWakeWordEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_WAKE_ENABLED, enabled).apply();
    }

    /** Display name the user speaks to summon Atom. Defaults to "Atom". */
    public String getWakeWordName() {
        return prefs.getString(KEY_WAKE_NAME, "Atom");
    }

    public void setWakeWordName(String name) {
        prefs.edit().putString(KEY_WAKE_NAME, name == null || name.trim().isEmpty()
                ? "Atom" : name.trim()).apply();
    }

    /**
     * Absolute path to the imported Porcupine {@code .ppn} keyword for a custom
     * name; empty means use the bundled default ("Atom").
     */
    public String getWakeWordPpnPath() {
        return prefs.getString(KEY_WAKE_PPN, "");
    }

    public void setWakeWordPpnPath(String path) {
        prefs.edit().putString(KEY_WAKE_PPN, path == null ? "" : path).apply();
    }

    /** Battery saver: only listen for the wake word while the screen is on. */
    public boolean isWakeWordScreenOnOnly() {
        return prefs.getBoolean(KEY_WAKE_SCREEN_ON_ONLY, false);
    }

    public void setWakeWordScreenOnOnly(boolean screenOnOnly) {
        prefs.edit().putBoolean(KEY_WAKE_SCREEN_ON_ONLY, screenOnOnly).apply();
    }

    /** Voice input defaults to unmuted (mic available). */
    public boolean isMicMuted() {
        return prefs.getBoolean(KEY_MIC_MUTED, false);
    }

    public void setMicMuted(boolean muted) {
        prefs.edit().putBoolean(KEY_MIC_MUTED, muted).apply();
    }

    /** Observe preference changes (e.g. to keep the mute icon in sync across surfaces). */
    public void registerChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }
}

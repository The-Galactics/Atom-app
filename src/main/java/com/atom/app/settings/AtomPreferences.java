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
    private static final String KEY_WAKE_SCREEN_ON_ONLY = "wake_word_screen_on_only";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_ASSISTANT_NAME = "assistant_name";
    private static final String KEY_ONBOARDING_COMPLETE = "onboarding_complete";
    private static final String KEY_LANGUAGE = "app_language";
    private static final String KEY_BUBBLE_ON_LEFT = "bubble_on_left";
    private static final String KEY_BUBBLE_Y = "bubble_y";
    private static final String KEY_BUBBLE_ENABLED = "bubble_enabled";

    /** Language follows the system locale until the user picks a specific one. */
    public static final String LANGUAGE_SYSTEM = "system";
    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_SPANISH = "es";

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

    /** The user's display name; empty until set in Settings. */
    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "");
    }

    public void setUserName(String name) {
        prefs.edit().putString(KEY_USER_NAME, name == null ? "" : name.trim()).apply();
    }

    /** The assistant's display name. Defaults to "Atom"; also drives the wake word. */
    public String getAssistantName() {
        return prefs.getString(KEY_ASSISTANT_NAME, "Atom");
    }

    public void setAssistantName(String name) {
        prefs.edit().putString(KEY_ASSISTANT_NAME, name == null || name.trim().isEmpty()
                ? "Atom" : name.trim()).apply();
    }

    /** True once the user has finished the first-run onboarding flow. */
    public boolean isOnboardingComplete() {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false);
    }

    public void setOnboardingComplete(boolean complete) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply();
    }

    /**
     * UI language preference: {@link #LANGUAGE_SYSTEM} (follow the device),
     * {@link #LANGUAGE_ENGLISH}, or {@link #LANGUAGE_SPANISH}. Drives
     * AppCompat's per-app locale at startup and when changed in Settings.
     */
    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM);
    }

    public void setLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language == null ? LANGUAGE_SYSTEM : language).apply();
    }

    /** Edge the floating bubble last rested on; defaults to the left. */
    public boolean isBubbleOnLeft() {
        return prefs.getBoolean(KEY_BUBBLE_ON_LEFT, true);
    }

    public void setBubbleOnLeft(boolean onLeft) {
        prefs.edit().putBoolean(KEY_BUBBLE_ON_LEFT, onLeft).apply();
    }

    /** Last bubble Y in pixels; -1 = never placed (use the default anchor). */
    public int getBubbleY() {
        return prefs.getInt(KEY_BUBBLE_Y, -1);
    }

    public void setBubbleY(int y) {
        prefs.edit().putInt(KEY_BUBBLE_Y, y).apply();
    }

    /** True if the user turned the floating bubble on; gates the cross-app operating cue. */
    public boolean isBubbleEnabled() {
        return prefs.getBoolean(KEY_BUBBLE_ENABLED, false);
    }

    public void setBubbleEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BUBBLE_ENABLED, enabled).apply();
    }

    /** Observe preference changes (e.g. to keep the mute icon in sync across surfaces). */
    public void registerChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }
}

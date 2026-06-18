package com.atom.infrastructure.adapter.voice;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * Android adapter over {@link TextToSpeech}: speaks assistant replies aloud.
 * Keeps all TTS-engine details here so callers only deal with {@link #speak}.
 */
public class AndroidTextToSpeech {

    private static final String TAG = "AtomTts";
    private static final String UTTERANCE_ID = "atom_reply";

    private final TextToSpeech engine;
    private boolean ready;

    // Text requested before init completed; spoken once the engine is ready.
    private String pending;

    public AndroidTextToSpeech(Context context) {
        this.engine = new TextToSpeech(context.getApplicationContext(), this::onInit);
    }

    private void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS init failed: " + status);
            return;
        }
        // Force Spanish so replies aren't read with the device's (often English)
        // accent. Fall back to generic Spanish, then the device default.
        Locale spanish = new Locale("es", "ES");
        int result = engine.setLanguage(spanish);
        if (result == TextToSpeech.LANG_MISSING_DATA
                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = engine.setLanguage(new Locale("es"));
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Spanish TTS data unavailable; using device default locale");
                engine.setLanguage(Locale.getDefault());
            }
        }
        ready = true;
        if (pending != null) {
            speak(pending);
            pending = null;
        }
    }

    /** Speaks {@code text}, replacing anything currently being spoken. */
    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (!ready) {
            pending = text; // spoken from onInit once ready
            return;
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
    }

    public void shutdown() {
        engine.stop();
        engine.shutdown();
    }
}

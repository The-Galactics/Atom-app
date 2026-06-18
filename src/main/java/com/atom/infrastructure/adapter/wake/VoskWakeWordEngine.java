package com.atom.infrastructure.adapter.wake;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.text.Normalizer;
import java.util.Locale;

/**
 * {@link WakeWordEngine} backed by Vosk on-device speech recognition. Unlike a
 * fixed-keyword engine, the wake word is just a string matched against what Vosk
 * hears, so any name the user types works immediately — no per-word model file.
 *
 * <p>The trade-off is higher CPU/battery than a dedicated hotword net (it runs
 * continuous ASR), mitigated by the service's "only while screen on" option.
 */
public class VoskWakeWordEngine implements WakeWordEngine, RecognitionListener {

    private static final String TAG = "AtomWake";
    private static final int SAMPLE_RATE = 16000;

    private final Model model;        // owned by the caller (service)
    private final String keyword;     // normalized (lowercase, no accents)
    private final Listener listener;

    private SpeechService speechService;
    private volatile boolean running;
    private long lastHitMs;

    public VoskWakeWordEngine(Model model, String keyword, Listener listener) {
        this.model = model;
        this.keyword = normalize(keyword);
        this.listener = listener;
    }

    @Override
    public void start() throws Exception {
        if (running) {
            return;
        }
        Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);
        speechService = new SpeechService(recognizer, SAMPLE_RATE);
        speechService.startListening(this);
        running = true;
        Log.i(TAG, "Vosk listening (keyword=" + keyword + ")");
    }

    @Override
    public void stop() {
        running = false;
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
    }

    @Override
    public void release() {
        stop();
        // The Model is owned and closed by the service, not here.
    }

    // --- Vosk RecognitionListener ---

    @Override
    public void onPartialResult(String hypothesis) {
        check(hypothesis, "partial");
    }

    @Override
    public void onResult(String hypothesis) {
        check(hypothesis, "text");
    }

    @Override
    public void onFinalResult(String hypothesis) {
        check(hypothesis, "text");
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "Vosk error", e);
        listener.onError(e != null ? e.getMessage() : "vosk error");
    }

    @Override
    public void onTimeout() {
    }

    private void check(String json, String field) {
        if (!running || json == null) {
            return;
        }
        try {
            String heard = new JSONObject(json).optString(field, "");
            if (heard.isEmpty()) {
                return;
            }
            if (matches(normalize(heard))) {
                // Debounce repeated partials of the same utterance.
                long now = System.currentTimeMillis();
                if (now - lastHitMs < 2000) {
                    return;
                }
                lastHitMs = now;
                listener.onWakeWordDetected();
            }
        } catch (JSONException ignored) {
        }
    }

    /** Loose match: whole-word, substring, or shared prefix (ASR may mishear names). */
    private boolean matches(String heard) {
        if (keyword.isEmpty()) {
            return false;
        }
        if (heard.contains(keyword)) {
            return true;
        }
        int prefix = Math.min(4, keyword.length());
        if (prefix < 3) {
            return false;
        }
        String keyPrefix = keyword.substring(0, prefix);
        for (String word : heard.split("\\s+")) {
            if (word.length() >= prefix && word.startsWith(keyPrefix)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim();
    }
}

package com.atom.infrastructure.adapter.wake;

import android.util.Log;

import org.json.JSONArray;
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
    // Reject low-confidence / too-short detections — these are the noise blips
    // that cause random false triggers, while a real spoken name scores high.
    // Grammar mode inflates confidence (audio is forced onto the keyword or
    // [unk]), so the bar is set high to cut false wakes during conversation.
    private static final double MIN_CONF = 0.92;
    // A name said on purpose lasts a beat; conversational blips are shorter.
    private static final double MIN_DURATION_S = 0.30;
    // Ignore repeat hits within this window (also damps bursts of false wakes).
    private static final long DEBOUNCE_MS = 3000;

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
        Recognizer recognizer;
        try {
            // Grammar mode: constrain recognition to the wake word (+ filler), which
            // is far lighter on CPU and biases strongly toward hearing the word.
            String grammar = "[\"" + keyword + "\", \"[unk]\"]";
            recognizer = new Recognizer(model, SAMPLE_RATE, grammar);
            Log.i(TAG, "Vosk grammar mode for '" + keyword + "'");
        } catch (Exception grammarUnsupported) {
            // Keyword may be out-of-vocabulary for the grammar; fall back to full ASR.
            Log.w(TAG, "Grammar mode failed, using full ASR", grammarUnsupported);
            recognizer = new Recognizer(model, SAMPLE_RATE);
        }
        // Per-word output so we can gate on confidence + duration.
        recognizer.setWords(true);
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
        // Ignore partials: they're speculative and the main source of false
        // triggers during normal conversation. Only act on settled results.
    }

    @Override
    public void onResult(String hypothesis) {
        check(hypothesis);
    }

    @Override
    public void onFinalResult(String hypothesis) {
        check(hypothesis);
    }

    @Override
    public void onError(Exception e) {
        Log.e(TAG, "Vosk error", e);
        listener.onError(e != null ? e.getMessage() : "vosk error");
    }

    @Override
    public void onTimeout() {
    }

    /**
     * Fires only when the wake word is the single meaningful word heard AND it
     * was recognized with enough confidence and duration. Strictness avoids
     * waking mid-conversation; the confidence/duration gate kills the random
     * noise blips — while a clearly-spoken name still passes.
     */
    private void check(String json) {
        if (!running || json == null || keyword.isEmpty()) {
            return;
        }
        try {
            JSONArray words = new JSONObject(json).optJSONArray("result");
            if (words == null || words.length() == 0) {
                return; // partial / empty: no settled word-level detail
            }
            int meaningful = 0;
            double keywordConf = -1;
            double keywordDuration = 0;
            for (int i = 0; i < words.length(); i++) {
                JSONObject w = words.getJSONObject(i);
                String word = normalize(w.optString("word", ""));
                if (word.isEmpty() || word.equals("[unk]")) {
                    continue;
                }
                meaningful++;
                if (word.equals(keyword)) {
                    keywordConf = w.optDouble("conf", 0);
                    keywordDuration = w.optDouble("end", 0) - w.optDouble("start", 0);
                }
            }
            if (keywordConf < 0 || meaningful != 1) {
                return; // keyword wasn't the lone meaningful word
            }
            if (keywordConf < MIN_CONF || keywordDuration < MIN_DURATION_S) {
                return; // too weak / too short → noise, ignore
            }
            long now = System.currentTimeMillis();
            if (now - lastHitMs < DEBOUNCE_MS) {
                return; // debounce
            }
            lastHitMs = now;
            listener.onWakeWordDetected();
        } catch (JSONException ignored) {
        }
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

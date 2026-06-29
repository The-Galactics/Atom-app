package com.atom.infrastructure.adapter.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import java.util.Locale;
import java.util.Set;

/**
 * Android adapter over {@link TextToSpeech}: speaks assistant replies aloud.
 * Keeps all TTS-engine details here so callers only deal with {@link #speak}.
 */
public class AndroidTextToSpeech {

    private static final String TAG = "AtomTts";
    private static final String UTTERANCE_ID = "atom_reply";

    // Exact Google TTS voice to use when present (offline, natural Spain voice).
    // Swap this name to try a different speaker; falls back to best-quality
    // Spanish voice if the device doesn't have it.
    private static final String PREFERRED_VOICE_NAME = "es-es-x-eed-local";

    private final TextToSpeech engine;
    private final String preferredVoiceName;
    private final float speechRate;
    private boolean ready;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Text requested before init completed; spoken once the engine is ready.
    private String pending;
    // Optional one-shot callback fired (on the main thread) when the CURRENT
    // utterance finishes (or errors). Used to open the mic only AFTER speaking.
    private volatile Runnable onDoneCallback;
    private Runnable pendingOnDone;
    // Each speak() gets a unique id; a flushed previous utterance reports its OWN
    // (stale) id, so we only fire the callback when the finished id is the current one.
    private int utteranceSeq;
    private volatile String currentUtteranceId;

    public AndroidTextToSpeech(Context context, String preferredVoiceName, float speechRate) {
        this.preferredVoiceName = preferredVoiceName == null ? "" : preferredVoiceName;
        // Clamp to a sane range; 0 or negative would make the engine ignore it.
        this.speechRate = speechRate > 0f ? speechRate : 0.9f;
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
        // Pick the most natural Spanish voice instead of the default (often the
        // robotic "compact" one). A slightly slower rate + neutral pitch reads
        // more naturally.
        selectBestSpanishVoice();
        // A slightly lower pitch reads warmer; the pace is user-configurable.
        engine.setPitch(0.95f);
        engine.setSpeechRate(speechRate);
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onDone(String utteranceId) { fireDone(utteranceId); }
            @Override public void onError(String utteranceId) { fireDone(utteranceId); }
        });
        ready = true;
        if (pending != null) {
            String text = pending;
            Runnable done = pendingOnDone;
            pending = null;
            pendingOnDone = null;
            speak(text, done);
        }
    }

    /**
     * Runs (once) the current utterance-done callback on the main thread — but only
     * if {@code finishedId} is the utterance we're actually waiting on. A flushed
     * earlier utterance reports its own stale id and must NOT fire the new callback.
     */
    private void fireDone(String finishedId) {
        if (finishedId == null || !finishedId.equals(currentUtteranceId)) {
            return;
        }
        currentUtteranceId = null;
        Runnable done = onDoneCallback;
        onDoneCallback = null;
        if (done != null) {
            mainHandler.post(done);
        }
    }

    /**
     * Selects the highest-quality Spanish voice the engine offers, preferring
     * offline voices so playback stays instant. Avoids the low-quality
     * "compact" voice that sounds robotic. No-op if no Spanish voice is found.
     */
    private void selectBestSpanishVoice() {
        try {
            Set<Voice> voices = engine.getVoices();
            if (voices == null) {
                return;
            }
            String wanted = preferredVoiceName.isEmpty() ? PREFERRED_VOICE_NAME : preferredVoiceName;
            Voice best = null;
            Voice exact = null;
            for (Voice v : voices) {
                if (v == null || v.getLocale() == null
                        || !"es".equalsIgnoreCase(v.getLocale().getLanguage())) {
                    continue;
                }
                if (v.getFeatures() != null
                        && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
                    continue;
                }
                if (wanted.equalsIgnoreCase(v.getName())) {
                    exact = v;
                }
                best = preferred(best, v);
            }
            // Use the hand-picked voice when available; otherwise the best one.
            if (exact != null) {
                best = exact;
            }
            if (best != null) {
                engine.setVoice(best);
                Log.i(TAG, "TTS voice: " + best.getName() + " quality=" + best.getQuality()
                        + " network=" + best.isNetworkConnectionRequired());
            }
        } catch (Exception e) {
            Log.w(TAG, "Spanish voice selection failed; using engine default", e);
        }
    }

    /** Prefers offline voices (zero latency), then higher synthesis quality. */
    private static Voice preferred(Voice current, Voice candidate) {
        if (current == null) {
            return candidate;
        }
        if (current.isNetworkConnectionRequired() != candidate.isNetworkConnectionRequired()) {
            return candidate.isNetworkConnectionRequired() ? current : candidate;
        }
        return candidate.getQuality() > current.getQuality() ? candidate : current;
    }

    /** Speaks {@code text}, replacing anything currently being spoken. */
    public void speak(String text) {
        speak(text, null);
    }

    /**
     * Speaks {@code text} and runs {@code onDone} (on the main thread) once speech
     * finishes or errors. Used to chain mic capture after the assistant has spoken
     * so it doesn't hear its own voice. {@code onDone} also fires immediately for
     * empty text so callers never hang waiting on a no-op utterance.
     */
    public void speak(String text, Runnable onDone) {
        if (text == null || text.trim().isEmpty()) {
            currentUtteranceId = null;
            onDoneCallback = null;
            if (onDone != null) {
                mainHandler.post(onDone);
            }
            return;
        }
        if (!ready) {
            pending = text; // spoken from onInit once ready
            pendingOnDone = onDone;
            return;
        }
        // Unique id per utterance so the done-callback only fires for THIS speak,
        // never for a previous one being flushed by QUEUE_FLUSH.
        String id = UTTERANCE_ID + "_" + (++utteranceSeq);
        currentUtteranceId = id;
        onDoneCallback = onDone;
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    /** Silences any in-progress or queued speech without tearing down the engine. */
    public void stop() {
        pending = null;
        if (ready) {
            engine.stop();
        }
    }

    public void shutdown() {
        engine.stop();
        engine.shutdown();
    }
}

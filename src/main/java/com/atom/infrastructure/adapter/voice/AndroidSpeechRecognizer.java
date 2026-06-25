package com.atom.infrastructure.adapter.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import com.atom.app.settings.AtomPreferences;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Android adapter over {@link SpeechRecognizer}: captures a spoken phrase and
 * reports the final transcript via {@link Listener}. Must be created and called
 * on the main thread; keeps all recognizer details out of the UI layer.
 */
public class AndroidSpeechRecognizer implements RecognitionListener {

    /** UI callbacks for recognition lifecycle and results. */
    public interface Listener {
        void onReadyForSpeech();
        void onEndOfSpeech();
        void onResult(String text);
        void onError(String message);

        /**
         * Live (non-final) transcript as the user is still speaking. Default no-op
         * so existing implementers keep compiling; surfaces fast, frequent updates
         * for a low-latency "live listening" status line.
         */
        default void onPartialResult(String text) { }
    }

    private final Context appContext;
    private final Listener listener;
    private SpeechRecognizer recognizer;

    public AndroidSpeechRecognizer(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    /** Begins listening; surfaces an error if recognition is unavailable. */
    public void startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            listener.onError("unavailable");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext);
            recognizer.setRecognitionListener(this);
        }
        recognizer.startListening(buildIntent());
    }

    private Intent buildIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // EXTRA_LANGUAGE must be a BCP-47 String tag (e.g. "es"); passing a Locale
        // object is silently ignored and the service falls back to its default
        // (en-US), mis-transcribing the user's speech. Follow the app's own
        // language setting — the source of truth — not the device locale.
        String languageTag = resolveLanguageTag();
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag);
        // Ask for live partial hypotheses so the UI can show speech as it lands.
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        return intent;
    }

    /**
     * Recognition language from the app's language preference (the same setting
     * shown in Settings). "system" follows the device locale; otherwise the
     * chosen tag ("en"/"es") wins — so a Spanish-set app transcribes Spanish
     * even on an English phone.
     */
    private String resolveLanguageTag() {
        String preference = new AtomPreferences(appContext).getLanguage();
        if (preference == null || AtomPreferences.LANGUAGE_SYSTEM.equals(preference)) {
            return Locale.getDefault().toLanguageTag();
        }
        return preference;
    }

    public void destroy() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
    }

    // --- RecognitionListener -------------------------------------------------

    @Override
    public void onReadyForSpeech(Bundle params) {
        listener.onReadyForSpeech();
    }

    @Override
    public void onEndOfSpeech() {
        listener.onEndOfSpeech();
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches =
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            listener.onResult(matches.get(0));
        } else {
            listener.onError("empty");
        }
    }

    @Override
    public void onError(int error) {
        listener.onError("error " + error);
    }

    // Unused lifecycle callbacks.
    @Override public void onBeginningOfSpeech() { }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> matches =
                partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String top = matches.get(0);
            if (top != null && !top.trim().isEmpty()) {
                listener.onPartialResult(top);
            }
        }
    }

    @Override public void onEvent(int eventType, Bundle params) { }
}

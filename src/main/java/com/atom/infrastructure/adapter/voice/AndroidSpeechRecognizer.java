package com.atom.infrastructure.adapter.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

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
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        return intent;
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
    @Override public void onPartialResults(Bundle partialResults) { }
    @Override public void onEvent(int eventType, Bundle params) { }
}

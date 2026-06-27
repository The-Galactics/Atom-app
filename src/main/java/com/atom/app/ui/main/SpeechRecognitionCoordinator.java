package com.atom.app.ui.main;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.atom.app.settings.AtomPreferences;
import com.atom.infrastructure.adapter.voice.AndroidSpeechRecognizer;
import com.atom.infrastructure.adapter.wake.WakeWordService;

/**
 * Owns the speech-recognition lifecycle: creates/destroys the recognizer, manages
 * the wake-word mic handoff, and routes recognition events to the {@link Host}.
 * UI concerns (status text, core animations, mic pulse) stay in MainActivity via Host.
 */
public final class SpeechRecognitionCoordinator {

    /** UI callbacks driven by recognition lifecycle events. */
    public interface Host {
        /** Called when listening begins (at start()); triggers full UI setup including mic pulse. */
        void onListeningStarted();
        /** Called when the recognizer is ready for speech (onReadyForSpeech); updates status text only. */
        void onListeningReady();
        /** Called when end-of-speech is detected; UI should move to thinking state. */
        void onThinking();
        /** Live (non-final) transcript while the user is still speaking. */
        void onPartialTranscript(String text);
        /** Final transcript; host should dispatch it as an order. */
        void onFinalTranscript(String text);
        /** Recognition failed; host should show an error and offer retry. */
        void onRecognitionError(String message);
        /** User-initiated cancel; host should reset UI to idle. */
        void onListeningCancelled();
    }

    // Let the wake word release the mic before the manual recognizer grabs it.
    private static final long MIC_HANDOFF_DELAY_MS = 350;

    private final Handler handoffHandler = new Handler(Looper.getMainLooper());

    private final AppCompatActivity activity;
    private final AtomPreferences preferences;
    private final Host host;

    private AndroidSpeechRecognizer speechRecognizer;
    private boolean isListening;

    public SpeechRecognitionCoordinator(AppCompatActivity activity,
                                        AtomPreferences preferences, Host host) {
        this.activity = activity;
        this.preferences = preferences;
        this.host = host;
    }

    /** True while a recognition is in flight. */
    public boolean isListening() {
        return isListening;
    }

    /**
     * Begins speech capture. Creates the recognizer if needed, signals the UI via
     * {@link Host#onListeningStarted()}, then starts the recognizer (with a brief
     * mic-handoff delay when the wake-word service is active).
     */
    public void start() {
        if (speechRecognizer == null) {
            speechRecognizer = new AndroidSpeechRecognizer(activity, new SttListener());
        }
        isListening = true;
        host.onListeningStarted();
        // The always-on wake word holds the mic; ask it to release first, then give
        // it a moment to free the AudioRecord before we start capturing.
        if (preferences.isWakeWordEnabled()) {
            activity.sendBroadcast(new Intent(WakeWordService.ACTION_WAKE_PAUSE)
                    .setPackage(activity.getPackageName()));
            handoffHandler.postDelayed(() -> {
                if (isListening && speechRecognizer != null) {
                    speechRecognizer.startListening();
                }
            }, MIC_HANDOFF_DELAY_MS);
        } else {
            speechRecognizer.startListening();
        }
    }

    /**
     * Cancels an in-flight recognition (user-initiated). Destroys the recognizer,
     * clears the flag, and notifies the host to reset the UI.
     */
    public void cancel() {
        handoffHandler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        isListening = false;
        host.onListeningCancelled();
    }

    /** Cleans up the recognizer on Activity destroy; safe to call when not listening. */
    public void destroy() {
        handoffHandler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    /** Tells the wake-word service the mic is free so it can resume listening. */
    private void notifyWakeDone() {
        activity.sendBroadcast(new Intent(WakeWordService.ACTION_LISTEN_DONE)
                .setPackage(activity.getPackageName()));
    }

    /** Routes AndroidSpeechRecognizer callbacks to the Host. */
    private final class SttListener implements AndroidSpeechRecognizer.Listener {
        @Override
        public void onReadyForSpeech() {
            host.onListeningReady();
        }

        @Override
        public void onEndOfSpeech() {
            isListening = false;
            host.onThinking();
        }

        @Override
        public void onPartialResult(String text) {
            host.onPartialTranscript(text);
        }

        @Override
        public void onResult(String text) {
            notifyWakeDone();
            host.onFinalTranscript(text);
        }

        @Override
        public void onError(String message) {
            isListening = false;
            notifyWakeDone();
            host.onRecognitionError(message);
        }
    }
}

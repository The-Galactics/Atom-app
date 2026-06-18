package com.atom.infrastructure.adapter.wake;

/**
 * Always-on hotword detector. Implementations listen continuously for a single
 * wake word ("Atom" by default) and notify {@link Listener} on a match. Kept
 * behind this interface so the engine (Porcupine today) can be swapped without
 * touching the service that coordinates the microphone.
 */
public interface WakeWordEngine {

    interface Listener {
        /** Fired when the wake word is detected (may be off the main thread). */
        void onWakeWordDetected();

        /** Fired when the engine cannot start or fails while listening. */
        void onError(String message);
    }

    /** Begins listening; acquires the microphone. */
    void start() throws Exception;

    /** Stops listening and releases the microphone, keeping the engine reusable. */
    void stop();

    /** Fully tears down the engine and frees native resources. */
    void release();
}

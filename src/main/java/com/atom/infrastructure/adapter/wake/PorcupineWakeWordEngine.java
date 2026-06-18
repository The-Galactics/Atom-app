package com.atom.infrastructure.adapter.wake;

import android.content.Context;
import android.util.Log;

import ai.picovoice.porcupine.PorcupineManager;

/**
 * {@link WakeWordEngine} backed by Picovoice Porcupine — a tiny on-device neural
 * net that listens only for one keyword, so it can run 24/7 with minimal battery.
 *
 * <p>The keyword is a pre-trained {@code .ppn} file (generated on the Picovoice
 * console). Default is the bundled "Atom"; a user-chosen name points at an
 * imported {@code .ppn}. Requires a (free) Picovoice access key.
 */
public class PorcupineWakeWordEngine implements WakeWordEngine {

    private static final String TAG = "AtomWake";

    private final Context context;
    private final String accessKey;
    private final String keywordPath;   // absolute file path to the .ppn
    private final float sensitivity;
    private final Listener listener;

    private PorcupineManager manager;

    public PorcupineWakeWordEngine(Context context, String accessKey, String keywordPath,
                                   float sensitivity, Listener listener) {
        this.context = context.getApplicationContext();
        this.accessKey = accessKey;
        this.keywordPath = keywordPath;
        this.sensitivity = sensitivity;
        this.listener = listener;
    }

    @Override
    public void start() throws Exception {
        if (manager == null) {
            manager = new PorcupineManager.Builder()
                    .setAccessKey(accessKey)
                    .setKeywordPath(keywordPath)
                    .setSensitivity(sensitivity)
                    .build(context, keywordIndex -> listener.onWakeWordDetected());
        }
        manager.start();
        Log.i(TAG, "Porcupine listening (keyword=" + keywordPath + ")");
    }

    @Override
    public void stop() {
        if (manager == null) {
            return;
        }
        try {
            manager.stop();
        } catch (Exception e) {
            Log.w(TAG, "Porcupine stop failed", e);
        }
    }

    @Override
    public void release() {
        if (manager == null) {
            return;
        }
        try {
            manager.stop();
            manager.delete();
        } catch (Exception e) {
            Log.w(TAG, "Porcupine release failed", e);
        } finally {
            manager = null;
        }
    }
}

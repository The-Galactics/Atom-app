package com.atom.app.repository;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.atom.application.port.in.SynthesizeSpeechPortIn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Speaks Atom's replies using the backend neural voice (Kokoro via the gRPC
 * Synthesize RPC). Audio chunks are collected into a WAV file and played with
 * MediaPlayer. Any failure runs {@code onFailure} on the main thread so the
 * caller can fall back to on-device TTS — the bubble never goes silent on a
 * backend hiccup.
 */
public class VoiceRepository {

    private static final String TAG = "AtomVoice";
    private static final String DEFAULT_VOICE = "af_heart";
    private static final String DEFAULT_LANGUAGE = "es";
    private static final String DEFAULT_FORMAT = "wav";
    private static final float DEFAULT_SPEED = 1.0f;

    private final SynthesizeSpeechPortIn synthesizeUseCase;
    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private MediaPlayer player;
    private File currentFile;
    private volatile boolean released = false;
    private boolean started = false;

    public VoiceRepository(Context context, SynthesizeSpeechPortIn synthesizeUseCase) {
        this.context = context.getApplicationContext();
        this.synthesizeUseCase = synthesizeUseCase;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Synthesizes {@code text} on a background thread and plays it. On any
     * failure (network, gRPC UNAVAILABLE, empty audio, playback error),
     * {@code onFailure} runs on the main thread for local-TTS fallback.
     */
    public void speak(String text, Runnable onFailure) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        executor.execute(() -> {
            File file = null;
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (Stream<byte[]> chunks = synthesizeUseCase.synthesize(
                        text, DEFAULT_VOICE, DEFAULT_LANGUAGE, DEFAULT_FORMAT, DEFAULT_SPEED)) {
                    chunks.forEach(chunk -> {
                        if (chunk != null && chunk.length > 0) {
                            buffer.write(chunk, 0, chunk.length);
                        }
                    });
                }
                byte[] audio = buffer.toByteArray();
                if (audio.length == 0) {
                    throw new IllegalStateException("empty audio from backend");
                }
                file = File.createTempFile("atom_tts", ".wav", context.getCacheDir());
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(audio);
                }
                final File ready = file;
                mainHandler.post(() -> play(ready, onFailure));
            } catch (Exception e) {
                Log.e(TAG, "remote synthesize failed, falling back to local TTS", e);
                if (file != null) {
                    file.delete();
                }
                if (onFailure != null) {
                    mainHandler.post(onFailure);
                }
            }
        });
    }

    private void play(File file, Runnable onFailure) {
        if (released) {
            file.delete();
            return;
        }
        try {
            releasePlayer();
            started = false;
            currentFile = file;
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(mp -> releasePlayer());
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error what=" + what + " extra=" + extra);
                boolean wasStarted = started;
                releasePlayer();
                if (!wasStarted && onFailure != null) {
                    onFailure.run();
                }
                return true;
            });
            player.prepare();
            player.start();
            started = true;
        } catch (Exception e) {
            Log.e(TAG, "playback failed, falling back to local TTS", e);
            releasePlayer();
            if (onFailure != null) {
                onFailure.run();
            }
        }
    }

    private void releasePlayer() {
        started = false;
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        if (currentFile != null) {
            currentFile.delete();
            currentFile = null;
        }
    }

    /** Stops playback and releases resources. Call from the owner's onDestroy. */
    public void shutdown() {
        released = true;
        mainHandler.removeCallbacksAndMessages(null);
        releasePlayer();
        executor.shutdownNow();
    }
}

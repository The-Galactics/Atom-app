package com.atom.app.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.atom.application.port.in.ExecuteCommandPortIn;
import com.atom.application.port.out.ActionExecutorPortOut;
import com.atom.domain.action.ActionOutcome;
import com.atom.domain.action.ResolvedAction;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drives the "order" flow: send the user's utterance to the backend for intent
 * recognition (off the main thread) and run the resolved action on the device.
 *
 * <p>Recognition is remote ({@link ExecuteCommandPortIn}); execution is local
 * ({@link ActionExecutorPortOut}). Confirmation for sensitive actions is decided
 * by the caller (UI layer) between {@link #recognize} and {@link #run}.
 */
public class CommandRepository {

    private static final String TAG = "AtomCommand";

    private final ExecuteCommandPortIn executeCommandUseCase;
    private final ActionExecutorPortOut actionExecutor;
    private final ExecutorService executor;
    private final Handler mainHandler;

    // Per-session id satisfies the use-case contract (no app-side auth yet).
    private final UUID sessionUserId = UUID.randomUUID();

    public CommandRepository(ExecuteCommandPortIn executeCommandUseCase,
                             ActionExecutorPortOut actionExecutor) {
        this.executeCommandUseCase = executeCommandUseCase;
        this.actionExecutor = actionExecutor;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /** Recognize an order. The callback runs on the main thread. */
    public void recognize(String order, final CommandCallback callback) {
        executor.execute(() -> {
            try {
                ResolvedAction action = executeCommandUseCase.execute(sessionUserId, order);
                mainHandler.post(() -> callback.onResolved(action));
            } catch (Exception e) {
                Log.e(TAG, "recognize failed for order=\"" + order + "\"", e);
                String message = e.getMessage() != null ? e.getMessage() : "Error in server response";
                mainHandler.post(() -> callback.onError(message));
            }
        });
    }

    /**
     * Execute an already-resolved (and, if needed, user-confirmed) action.
     * Runs on a background thread; the outcome is posted to the main thread.
     */
    public void run(ResolvedAction action, final ExecutionCallback callback) {
        executor.execute(() -> {
            ActionOutcome outcome = actionExecutor.execute(action);
            mainHandler.post(() -> callback.onExecuted(outcome));
        });
    }

    public interface CommandCallback {
        void onResolved(ResolvedAction action);
        void onError(String error);
    }

    public interface ExecutionCallback {
        void onExecuted(ActionOutcome outcome);
    }
}

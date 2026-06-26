package com.atom.app.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.atom.application.port.in.ExecuteCommandPortIn;
import com.atom.application.port.in.security.AuthPortIn;
import com.atom.application.port.out.ActionExecutorPortOut;
import com.atom.domain.action.ActionOutcome;
import com.atom.domain.action.DestructiveActionPolicy;
import com.atom.domain.action.ResolvedAction;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drives the "order" flow. Beyond the legacy single-shot {@link #recognize}/
 * {@link #run}, it owns the multi-step autonomous loop ({@link #executeAutonomous}):
 * recognize -> confirmation gate -> run -> evaluate {@code task_complete}, repeat.
 *
 * <p>The loop lives here because this class already owns the stable
 * {@link #sessionUserId} (so the backend keeps one ReAct session across calls),
 * the background-executor / main-thread plumbing, and both ports.
 */
public class CommandRepository {

    private static final String TAG = "AtomCommand";

    /** Settle time after an action before the next recognize, sized for cold app launches. */
    private static final long DEFAULT_SETTLE_DELAY_MS = 1200L;
    /** Circuit breaker: cap steps even if {@code task_complete} never arrives. */
    private static final int DEFAULT_STEP_CAP = 20;

    private final ExecuteCommandPortIn executeCommandUseCase;
    private final ActionExecutorPortOut actionExecutor;
    private final UUID sessionUserId;   // injected; shared with ChatRepository (US-D2)
    private final AuthPortIn authUseCase;
    private final ExecutorService executor;
    private final MainThreadPoster mainPoster;

    // Decides whether a resolved action must be confirmed before it runs.
    private ConfirmationGate confirmationGate;
    private final long settleDelayMs;
    private final int stepCap;

    public CommandRepository(ExecuteCommandPortIn executeCommandUseCase,
                             ActionExecutorPortOut actionExecutor,
                             UUID sessionUserId,
                             AuthPortIn authUseCase) {
        this(executeCommandUseCase, actionExecutor, sessionUserId, authUseCase,
                new DestructiveActionGate(new DestructiveActionPolicy()),
                DEFAULT_SETTLE_DELAY_MS, DEFAULT_STEP_CAP,
                new HandlerPoster());
    }

    /** Test/extension overload: inject the gate, settle delay (0 in tests), and step cap. */
    public CommandRepository(ExecuteCommandPortIn executeCommandUseCase,
                             ActionExecutorPortOut actionExecutor,
                             UUID sessionUserId,
                             AuthPortIn authUseCase,
                             ConfirmationGate confirmationGate,
                             long settleDelayMs, int stepCap) {
        this(executeCommandUseCase, actionExecutor, sessionUserId, authUseCase, confirmationGate,
                settleDelayMs, stepCap, new HandlerPoster());
    }

    /** Full overload: also inject the main-thread poster so tests can run it synchronously. */
    @VisibleForTesting
    public CommandRepository(ExecuteCommandPortIn executeCommandUseCase,
                             ActionExecutorPortOut actionExecutor,
                             UUID sessionUserId,
                             AuthPortIn authUseCase,
                             ConfirmationGate confirmationGate,
                             long settleDelayMs, int stepCap,
                             MainThreadPoster mainPoster) {
        this.executeCommandUseCase = executeCommandUseCase;
        this.actionExecutor = actionExecutor;
        this.sessionUserId = sessionUserId;
        this.authUseCase = authUseCase;
        this.confirmationGate = confirmationGate;
        this.settleDelayMs = settleDelayMs;
        this.stepCap = stepCap;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainPoster = mainPoster;
    }

    /** Swaps in the UI's prompting gate so destructive actions can pause the loop and prompt. */
    public void setConfirmationGate(ConfirmationGate gate) {
        if (gate != null) {
            this.confirmationGate = gate;
        }
    }

    /** Recognize an order. The callback runs on the main thread. */
    public void recognize(String order, final CommandCallback callback) {
        executor.execute(() -> {
            try {
                // Routing pre-flight only: used for routing decisions, never carried into the
                // loop. Uses a throwaway session id so it doesn't pollute the backend's ReAct
                // history (a shared id would offset the loop's steps and trip a premature
                // task_complete).
                UUID preflightSessionId = UUID.randomUUID();
                ResolvedAction action = executeCommandUseCase.execute(preflightSessionId, order);
                post(() -> callback.onResolved(action));
            } catch (Exception e) {
                Log.e(TAG, "recognize failed for order=\"" + order + "\"", e);
                String message = e.getMessage() != null ? e.getMessage() : "Error in server response";
                post(() -> callback.onError(message));
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
            post(() -> callback.onExecuted(outcome));
        });
    }

    /**
     * Multi-step autonomous loop. Recognizes the order, runs the single returned
     * action (after the confirmation gate), then re-recognizes against the new
     * screen until the backend signals {@code task_complete}, an action fails,
     * confirmation is declined, or the step cap is hit. Callbacks run on the main
     * thread.
     */
    public void executeAutonomous(String order, final AutomationCallback callback) {
        executor.execute(() -> {
            try {
                // Refresh-ahead OFF the call path (US-E3): the gRPC interceptor is now
                // cache-only, so prime a fresh token here (on the background executor)
                // before the first protected RPC. A refresh failure surfaces through the
                // shared catch below as a normal aborted outcome.
                if (authUseCase != null) {
                    authUseCase.refreshIfNeeded();
                }
                for (int step = 1; step <= stepCap; step++) {
                    ResolvedAction action = executeCommandUseCase.execute(sessionUserId, order);
                    Log.i(TAG, "step " + step + " resolved: type=" + action.type()
                            + " params=" + action.parameters()
                            + " taskComplete=" + action.taskComplete());

                    if (action.isExecutable()) {
                        if (confirmationGate.requiresConfirmation(action)
                                && !confirmationGate.confirm(action)) {
                            Log.i(TAG, "step " + step + " declined at confirmation gate");
                            post(() -> callback.onAborted(action.outMessage()));
                            return;
                        }
                        final int stepNo = step;
                        post(() -> callback.onActionStarted(action, stepNo));

                        ActionOutcome outcome = actionExecutor.execute(action);
                        Log.i(TAG, "step " + step + " outcome: success=" + outcome.success()
                                + " message=\"" + outcome.message() + "\"");
                        if (!outcome.success()) {
                            post(() -> callback.onAborted(outcome.message()));
                            return;
                        }
                    }

                    if (action.taskComplete()) {
                        post(() -> callback.onComplete(action.outMessage()));
                        return;
                    }
                    sleepSettle();
                }
                // Step cap reached without task_complete: terminate the chain.
                post(() -> callback.onAborted(""));
            } catch (Exception e) {
                Log.e(TAG, "executeAutonomous failed for order=\"" + order + "\"", e);
                String message = e.getMessage() != null ? e.getMessage() : "Error in server response";
                post(() -> callback.onAborted(message));
            }
        });
    }

    private void post(Runnable r) {
        mainPoster.post(r);
    }

    private void sleepSettle() {
        if (settleDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(settleDelayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public interface CommandCallback {
        void onResolved(ResolvedAction action);
        void onError(String error);
    }

    public interface ExecutionCallback {
        void onExecuted(ActionOutcome outcome);
    }

    /** Per-step and terminal events of the autonomous loop (delivered on the main thread). */
    public interface AutomationCallback {
        /** A confirmed, executable action is about to run at 1-based {@code step}. */
        void onActionStarted(ResolvedAction action, int step);
        /** The chain finished; {@code finalMessage} is the backend reply to speak/show. */
        void onComplete(String finalMessage);
        /** The chain stopped early (failure, declined confirmation, or step cap). */
        void onAborted(String message);
    }

    /**
     * Decides whether an action needs user confirmation and, when so, blocks the
     * loop thread until the user answers. Injected so the loop is UI-free and
     * unit-testable (tests stub it).
     */
    public interface ConfirmationGate {
        boolean requiresConfirmation(ResolvedAction action);
        /** Called on the background thread; return true to proceed, false to abort. */
        boolean confirm(ResolvedAction action);
    }

    /** Default gate: detects via {@link DestructiveActionPolicy} and auto-approves (UI supplies its own). */
    private static final class DestructiveActionGate implements ConfirmationGate {
        private final DestructiveActionPolicy policy;

        DestructiveActionGate(DestructiveActionPolicy policy) {
            this.policy = policy;
        }

        @Override
        public boolean requiresConfirmation(ResolvedAction action) {
            return policy.requiresConfirmation(action);
        }

        @Override
        public boolean confirm(ResolvedAction action) {
            return true;
        }
    }

    /** Posts a Runnable to the main thread. Abstracted so tests run it synchronously. */
    public interface MainThreadPoster {
        void post(Runnable r);
    }

    /** Production poster: delegates to the main-thread {@link Handler}. */
    private static final class HandlerPoster implements MainThreadPoster {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void post(Runnable r) {
            handler.post(r);
        }
    }
}

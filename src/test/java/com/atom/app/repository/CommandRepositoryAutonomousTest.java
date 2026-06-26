package com.atom.app.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.atom.application.port.in.ExecuteCommandPortIn;
import com.atom.application.port.in.security.AuthPortIn;
import com.atom.application.port.out.ActionExecutorPortOut;
import com.atom.domain.action.ActionOutcome;
import com.atom.domain.action.ActionType;
import com.atom.domain.action.ResolvedAction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link CommandRepository#executeAutonomous}. The loop runs on the
 * repository's background executor; we inject a synchronous main-thread poster and a
 * settle delay of 0 so the chain runs to its terminal callback without an Android
 * runtime, and await that callback via a latch.
 */
class CommandRepositoryAutonomousTest {

    private ExecuteCommandPortIn useCase;
    private ActionExecutorPortOut executorPort;
    private AuthPortIn authUseCase;

    @BeforeEach
    void setUp() {
        useCase = mock(ExecuteCommandPortIn.class);
        executorPort = mock(ActionExecutorPortOut.class);
        authUseCase = mock(AuthPortIn.class);
    }

    private static ResolvedAction action(ActionType type, boolean taskComplete) {
        return action(type, Map.of(), taskComplete);
    }

    private static ResolvedAction action(ActionType type, Map<String, String> params, boolean done) {
        return new ResolvedAction(type, params, done ? "Listo" : "", 1.0f, false, done, 0);
    }

    /** Captures the terminal callback so assertions can read it after awaiting. */
    private static final class RecordingCallback implements CommandRepository.AutomationCallback {
        final CountDownLatch done = new CountDownLatch(1);
        final List<Integer> startedSteps = new CopyOnWriteArrayList<>();
        volatile String completedMessage;
        volatile String abortedMessage;
        volatile boolean completed;
        volatile boolean aborted;

        @Override
        public void onActionStarted(ResolvedAction action, int step) {
            startedSteps.add(step);
        }

        @Override
        public void onComplete(String finalMessage) {
            completed = true;
            completedMessage = finalMessage;
            done.countDown();
        }

        @Override
        public void onAborted(String message) {
            aborted = true;
            abortedMessage = message;
            done.countDown();
        }

        void await() throws InterruptedException {
            assertThat(done.await(5, TimeUnit.SECONDS)).as("loop terminated").isTrue();
        }
    }

    /** Synchronous gate that always approves (no destructive action exercised here). */
    private static CommandRepository.ConfirmationGate approveGate() {
        return new CommandRepository.ConfirmationGate() {
            @Override
            public boolean requiresConfirmation(ResolvedAction action) {
                return false;
            }

            @Override
            public boolean confirm(ResolvedAction action) {
                return true;
            }
        };
    }

    private CommandRepository repo(CommandRepository.ConfirmationGate gate) {
        // Settle delay 0; poster runs callbacks inline on the loop thread.
        // sessionUserId is a random UUID here — these tests assert behaviour, not the shared id.
        return new CommandRepository(useCase, executorPort, UUID.randomUUID(), authUseCase, gate, 0L, 20, Runnable::run);
    }

    @Test
    @DisplayName("Refreshes the token off the call path before the first command RPC")
    void refreshesTokenBeforeFirstCommandRpc() throws InterruptedException {
        when(useCase.execute(any(UUID.class), any()))
                .thenReturn(action(ActionType.NONE, true));

        RecordingCallback cb = new RecordingCallback();
        repo(approveGate()).executeAutonomous("hola", cb);
        cb.await();

        // The refresh-ahead must happen on the loop thread BEFORE the first protected RPC,
        // so the interceptor (now cache-only) reads a fresh token.
        InOrder inOrder = inOrder(authUseCase, useCase);
        inOrder.verify(authUseCase).refreshIfNeeded();
        inOrder.verify(useCase).execute(any(UUID.class), any());
    }

    @Test
    @DisplayName("Chains multiple steps and halts when task_complete becomes true")
    void chainsUntilComplete() throws InterruptedException {
        when(useCase.execute(any(UUID.class), eq("abre youtube")))
                .thenReturn(action(ActionType.OPEN_APP, false))
                .thenReturn(action(ActionType.TAP_ELEMENT, Map.of("text", "Search"), false))
                .thenReturn(action(ActionType.NONE, true));
        when(executorPort.execute(any())).thenReturn(ActionOutcome.ok("done"));

        RecordingCallback cb = new RecordingCallback();
        repo(approveGate()).executeAutonomous("abre youtube", cb);
        cb.await();

        assertThat(cb.completed).isTrue();
        assertThat(cb.completedMessage).isEqualTo("Listo");
        // Two executable actions ran; the terminal NONE/complete step did not execute.
        verify(executorPort, times(2)).execute(any());
        assertThat(cb.startedSteps).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Runs the full open->tap->type->tap chain, executing every step")
    void runsFullTypeTextChain() throws InterruptedException {
        // Models "Entra a YouTube y busca un video...": open the app, tap search,
        // type the query (submit), then tap the result; the final step is complete.
        when(useCase.execute(any(UUID.class), eq("busca rubius pokemon")))
                .thenReturn(action(ActionType.OPEN_APP, Map.of("app_name", "YouTube"), false))
                .thenReturn(action(ActionType.TAP_ELEMENT, Map.of("text", "search"), false))
                .thenReturn(action(ActionType.TYPE_TEXT,
                        Map.of("text", "rubius cartas pokemon", "submit", "true"), false))
                .thenReturn(action(ActionType.TAP_ELEMENT, Map.of("text", "Rubius"), true));
        when(executorPort.execute(any())).thenReturn(ActionOutcome.ok("done"));

        RecordingCallback cb = new RecordingCallback();
        repo(approveGate()).executeAutonomous("busca rubius pokemon", cb);
        cb.await();

        assertThat(cb.completed).isTrue();
        // All four steps are executable; the last carries task_complete, so it
        // both executes and terminates the loop -> four executor invocations.
        verify(executorPort, times(4)).execute(any());
        assertThat(cb.startedSteps).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Halts immediately when the first response is already complete")
    void haltsOnImmediateComplete() throws InterruptedException {
        when(useCase.execute(any(UUID.class), any()))
                .thenReturn(action(ActionType.NONE, true));

        RecordingCallback cb = new RecordingCallback();
        repo(approveGate()).executeAutonomous("hola", cb);
        cb.await();

        assertThat(cb.completed).isTrue();
        verify(executorPort, never()).execute(any());
    }

    @Test
    @DisplayName("Aborts the chain on an action failure")
    void abortsOnActionFailure() throws InterruptedException {
        when(useCase.execute(any(UUID.class), any()))
                .thenReturn(action(ActionType.OPEN_APP, false));
        when(executorPort.execute(any())).thenReturn(ActionOutcome.failed("no such app"));

        RecordingCallback cb = new RecordingCallback();
        repo(approveGate()).executeAutonomous("abre xyz", cb);
        cb.await();

        assertThat(cb.aborted).isTrue();
        assertThat(cb.abortedMessage).isEqualTo("no such app");
        verify(executorPort, times(1)).execute(any());
    }

    @Test
    @DisplayName("Honors the step cap when task_complete never arrives")
    void honorsStepCap() throws InterruptedException {
        when(useCase.execute(any(UUID.class), any()))
                .thenReturn(action(ActionType.SCROLL, Map.of("direction", "down"), false));
        when(executorPort.execute(any())).thenReturn(ActionOutcome.ok("scrolled"));

        RecordingCallback cb = new RecordingCallback();
        // Cap of 3: the loop must terminate after exactly 3 executed steps.
        new CommandRepository(useCase, executorPort, UUID.randomUUID(), authUseCase, approveGate(), 0L, 3, Runnable::run)
                .executeAutonomous("scroll forever", cb);
        cb.await();

        assertThat(cb.aborted).isTrue();
        verify(executorPort, times(3)).execute(any());
    }

    @Test
    @DisplayName("Triggers the confirmation gate on a destructive action; decline aborts")
    void triggersGateAndAbortsOnDecline() throws InterruptedException {
        when(useCase.execute(any(UUID.class), any()))
                .thenReturn(action(ActionType.TAP_ELEMENT, Map.of("text", "Eliminar"), false));

        final List<ResolvedAction> prompted = new CopyOnWriteArrayList<>();
        CommandRepository.ConfirmationGate decliningGate = new CommandRepository.ConfirmationGate() {
            @Override
            public boolean requiresConfirmation(ResolvedAction action) {
                return action.type() == ActionType.TAP_ELEMENT;
            }

            @Override
            public boolean confirm(ResolvedAction action) {
                prompted.add(action);
                return false; // user declines -> abort
            }
        };

        RecordingCallback cb = new RecordingCallback();
        repo(decliningGate).executeAutonomous("borra la foto", cb);
        cb.await();

        assertThat(prompted).hasSize(1);
        assertThat(cb.aborted).isTrue();
        // Declined before running: the action executor is never reached.
        verify(executorPort, never()).execute(any());
    }

    @Test
    @DisplayName("Confirmed destructive action proceeds and runs")
    void confirmedDestructiveProceeds() throws InterruptedException {
        when(useCase.execute(any(UUID.class), any()))
                .thenReturn(action(ActionType.TAP_ELEMENT, Map.of("text", "Eliminar"), false))
                .thenReturn(action(ActionType.NONE, true));
        when(executorPort.execute(any())).thenReturn(ActionOutcome.ok("tapped"));

        CommandRepository.ConfirmationGate approvingDestructive =
                new CommandRepository.ConfirmationGate() {
                    @Override
                    public boolean requiresConfirmation(ResolvedAction action) {
                        return action.type() == ActionType.TAP_ELEMENT;
                    }

                    @Override
                    public boolean confirm(ResolvedAction action) {
                        return true;
                    }
                };

        RecordingCallback cb = new RecordingCallback();
        repo(approvingDestructive).executeAutonomous("borra la foto", cb);
        cb.await();

        assertThat(cb.completed).isTrue();
        verify(executorPort, times(1)).execute(any());
    }

    @Test
    @DisplayName("Default gate is fail-closed: a sensitive action with no UI gate is denied, not run")
    void defaultGateFailsClosedOnSensitiveAction() throws InterruptedException {
        // Any surface that installs no prompting gate falls back to this default; it
        // must DENY sensitive actions (the loop aborts), never auto-approve them (2B.1).
        when(useCase.execute(any(UUID.class), any()))
                .thenReturn(action(ActionType.MAKE_CALL, Map.of("target", "Mom"), false));

        RecordingCallback cb = new RecordingCallback();
        new CommandRepository(useCase, executorPort, CommandRepository.defaultGate(), 0L, 20, Runnable::run)
                .executeAutonomous("llama a mama", cb);
        cb.await();

        assertThat(cb.aborted).isTrue();
        verify(executorPort, never()).execute(any());
    }

    /** Awaits a single onResolved/onError from #recognize. */
    private static final class RecognizeCallback implements CommandRepository.CommandCallback {
        final CountDownLatch done = new CountDownLatch(1);
        volatile ResolvedAction resolved;
        volatile String error;

        @Override
        public void onResolved(ResolvedAction action) {
            resolved = action;
            done.countDown();
        }

        @Override
        public void onError(String message) {
            error = message;
            done.countDown();
        }

        void await() throws InterruptedException {
            assertThat(done.await(5, TimeUnit.SECONDS)).as("recognize returned").isTrue();
        }
    }

    @Test
    @DisplayName("recognize pre-flight uses a fresh session id, not the loop's persistent one")
    void preflightSessionIdIsolatedFromLoop() throws InterruptedException {
        // Same repository instance: the routing pre-flight (#recognize) must not share the
        // backend ReAct session with the autonomous loop, or it pollutes step history.
        when(useCase.execute(any(UUID.class), any()))
                .thenReturn(action(ActionType.NONE, true));
        when(executorPort.execute(any())).thenReturn(ActionOutcome.ok("done"));

        CommandRepository repo = repo(approveGate());

        RecognizeCallback rc = new RecognizeCallback();
        repo.recognize("abre youtube", rc);
        rc.await();

        RecordingCallback cb = new RecordingCallback();
        repo.executeAutonomous("abre youtube", cb);
        cb.await();

        ArgumentCaptor<UUID> ids = ArgumentCaptor.forClass(UUID.class);
        verify(useCase, atLeastOnce()).execute(ids.capture(), any());

        UUID preflightId = ids.getAllValues().get(0); // first call is the recognize pre-flight
        UUID loopId = ids.getAllValues().get(1);      // second call is the loop's first step
        assertThat(preflightId).isNotNull();
        assertThat(loopId).isNotNull();
        assertThat(preflightId).isNotEqualTo(loopId);
    }

    @Test
    @DisplayName("Two autonomous runs on the same instance reuse the persistent session id")
    void autonomousRunsSharePersistentSessionId() throws InterruptedException {
        when(useCase.execute(any(UUID.class), any()))
                .thenReturn(action(ActionType.NONE, true));

        CommandRepository repo = repo(approveGate());

        RecordingCallback first = new RecordingCallback();
        repo.executeAutonomous("orden uno", first);
        first.await();

        RecordingCallback second = new RecordingCallback();
        repo.executeAutonomous("orden dos", second);
        second.await();

        ArgumentCaptor<UUID> ids = ArgumentCaptor.forClass(UUID.class);
        verify(useCase, times(2)).execute(ids.capture(), any());

        assertThat(ids.getAllValues().get(0)).isEqualTo(ids.getAllValues().get(1));
    }
}

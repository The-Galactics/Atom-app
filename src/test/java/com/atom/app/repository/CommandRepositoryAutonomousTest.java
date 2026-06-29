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
        when(authUseCase.shouldRefresh()).thenReturn(true);
    }

    private static ResolvedAction action(ActionType type, boolean taskComplete) {
        return action(type, Map.of(), taskComplete);
    }

    private static ResolvedAction action(ActionType type, Map<String, String> params, boolean done) {
        return new ResolvedAction(type, params, done ? "Listo" : "", 1.0f, false, done, 0, false);
    }

    /** A held sensitive action: NONE + awaiting_confirmation, carrying the spoken question. */
    private static ResolvedAction awaiting(String question) {
        return new ResolvedAction(ActionType.NONE, Map.of(), question, 1.0f, false, false, 0, true);
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

    /** Gate that never flags a local confirmation (no destructive action exercised here). */
    private static CommandRepository.ConfirmationGate approveGate() {
        return new CommandRepository.ConfirmationGate() {
            @Override public boolean requiresConfirmation(ResolvedAction action) { return false; }
            @Override public String ask(String question) { return ""; }
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
        when(useCase.execute(any(), any(), any()))
                .thenReturn(action(ActionType.NONE, true));

        RecordingCallback cb = new RecordingCallback();
        repo(approveGate()).executeAutonomous("hola", cb);
        cb.await();

        InOrder inOrder = inOrder(authUseCase, useCase);
        inOrder.verify(authUseCase).refreshIfNeeded();
        inOrder.verify(useCase).execute(any(), any(), any());
    }

    @Test
    @DisplayName("Chains multiple steps and halts when task_complete becomes true")
    void chainsUntilComplete() throws InterruptedException {
        when(useCase.execute(any(), any(), eq("abre youtube")))
                .thenReturn(action(ActionType.OPEN_APP, false))
                .thenReturn(action(ActionType.TAP_ELEMENT, Map.of("text", "Search"), false))
                .thenReturn(action(ActionType.NONE, true));
        when(executorPort.execute(any())).thenReturn(ActionOutcome.ok("done"));

        RecordingCallback cb = new RecordingCallback();
        repo(approveGate()).executeAutonomous("abre youtube", cb);
        cb.await();

        assertThat(cb.completed).isTrue();
        assertThat(cb.completedMessage).isEqualTo("Listo");
        verify(executorPort, times(2)).execute(any());
        assertThat(cb.startedSteps).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Runs the full open->tap->type->tap chain, executing every step")
    void runsFullTypeTextChain() throws InterruptedException {
        when(useCase.execute(any(), any(), eq("busca rubius pokemon")))
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
        verify(executorPort, times(4)).execute(any());
        assertThat(cb.startedSteps).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Halts immediately when the first response is already complete")
    void haltsOnImmediateComplete() throws InterruptedException {
        when(useCase.execute(any(), any(), any()))
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
        when(useCase.execute(any(), any(), any()))
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
        when(useCase.execute(any(), any(), any()))
                .thenReturn(action(ActionType.SCROLL, Map.of("direction", "down"), false));
        when(executorPort.execute(any())).thenReturn(ActionOutcome.ok("scrolled"));

        RecordingCallback cb = new RecordingCallback();
        new CommandRepository(useCase, executorPort, UUID.randomUUID(), authUseCase, approveGate(), 0L, 3, Runnable::run)
                .executeAutonomous("scroll forever", cb);
        cb.await();

        assertThat(cb.aborted).isTrue();
        verify(executorPort, times(3)).execute(any());
    }

    @Test
    @DisplayName("Held sensitive action: speaks the question, resends the spoken 'sí' with the same order id, then executes")
    void heldActionConfirmedByVoiceExecutes() throws InterruptedException {
        // Turn 1 holds (awaiting); after the spoken reply the backend emits the action; then complete.
        when(useCase.execute(any(), any(), eq("llama a mamá")))
                .thenReturn(awaiting("¿Confirmas que llame a mamá?"));
        // The confirmed call is the final action (task_complete), so the loop ends here.
        when(useCase.execute(any(), any(), eq("sí")))
                .thenReturn(action(ActionType.MAKE_CALL, Map.of("target", "mamá"), true));
        when(executorPort.execute(any())).thenReturn(ActionOutcome.ok("calling"));

        final List<String> askedQuestions = new CopyOnWriteArrayList<>();
        CommandRepository.ConfirmationGate voiceYes = new CommandRepository.ConfirmationGate() {
            @Override public boolean requiresConfirmation(ResolvedAction action) { return false; }
            @Override public String ask(String question) { askedQuestions.add(question); return "sí"; }
        };

        RecordingCallback cb = new RecordingCallback();
        repo(voiceYes).executeAutonomous("llama a mamá", cb);
        cb.await();

        assertThat(askedQuestions).containsExactly("¿Confirmas que llame a mamá?");
        assertThat(cb.completed).isTrue();
        verify(executorPort, times(1)).execute(any());

        // The held question, the spoken reply, and the follow-up turns all share ONE order id.
        ArgumentCaptor<UUID> ids = ArgumentCaptor.forClass(UUID.class);
        verify(useCase, atLeastOnce()).execute(any(), ids.capture(), any());
        assertThat(ids.getAllValues()).allMatch(id -> id.equals(ids.getAllValues().get(0)));
    }

    @Test
    @DisplayName("Asked but heard nothing ('') re-asks exactly once, then aborts")
    void heldActionBlankReplyReAsksOnceThenAborts() throws InterruptedException {
        when(useCase.execute(any(), any(), any()))
                .thenReturn(awaiting("¿Confirmas?"));

        final java.util.concurrent.atomic.AtomicInteger asks = new java.util.concurrent.atomic.AtomicInteger();
        CommandRepository.ConfirmationGate blank = new CommandRepository.ConfirmationGate() {
            @Override public boolean requiresConfirmation(ResolvedAction action) { return false; }
            @Override public String ask(String question) { asks.incrementAndGet(); return ""; }
        };

        RecordingCallback cb = new RecordingCallback();
        repo(blank).executeAutonomous("llama a mamá", cb);
        cb.await();

        assertThat(cb.aborted).isTrue();
        assertThat(asks.get()).isEqualTo(2);  // asked, then one re-ask, then give up
        verify(executorPort, never()).execute(any());
    }

    @Test
    @DisplayName("Cannot ask at all (null) aborts immediately WITHOUT re-asking")
    void heldActionCannotAskAbortsImmediately() throws InterruptedException {
        when(useCase.execute(any(), any(), any()))
                .thenReturn(awaiting("¿Confirmas?"));

        final java.util.concurrent.atomic.AtomicInteger asks = new java.util.concurrent.atomic.AtomicInteger();
        CommandRepository.ConfirmationGate cantAsk = new CommandRepository.ConfirmationGate() {
            @Override public boolean requiresConfirmation(ResolvedAction action) { return false; }
            @Override public String ask(String question) { asks.incrementAndGet(); return null; }
        };

        RecordingCallback cb = new RecordingCallback();
        repo(cantAsk).executeAutonomous("llama a mamá", cb);
        cb.await();

        assertThat(cb.aborted).isTrue();
        assertThat(asks.get()).isEqualTo(1);  // no re-ask when we couldn't ask at all
        verify(executorPort, never()).execute(any());
    }

    @Test
    @DisplayName("Local destructive net: a 'no' spoken reply aborts before executing")
    void localDestructiveDeclinedByVoiceAborts() throws InterruptedException {
        when(useCase.execute(any(), any(), any()))
                .thenReturn(action(ActionType.TAP_ELEMENT, Map.of("text", "Eliminar"), false));

        final List<String> asked = new CopyOnWriteArrayList<>();
        CommandRepository.ConfirmationGate decline = new CommandRepository.ConfirmationGate() {
            @Override public boolean requiresConfirmation(ResolvedAction action) {
                return action.type() == ActionType.TAP_ELEMENT;
            }
            @Override public String ask(String question) { asked.add(question); return "no"; }
        };

        RecordingCallback cb = new RecordingCallback();
        repo(decline).executeAutonomous("borra la foto", cb);
        cb.await();

        assertThat(asked).hasSize(1);
        assertThat(cb.aborted).isTrue();
        verify(executorPort, never()).execute(any());
    }

    @Test
    @DisplayName("Local destructive net: a 'sí' spoken reply proceeds and runs")
    void localDestructiveConfirmedByVoiceProceeds() throws InterruptedException {
        when(useCase.execute(any(), any(), any()))
                .thenReturn(action(ActionType.TAP_ELEMENT, Map.of("text", "Eliminar"), false))
                .thenReturn(action(ActionType.NONE, true));
        when(executorPort.execute(any())).thenReturn(ActionOutcome.ok("tapped"));

        CommandRepository.ConfirmationGate approve = new CommandRepository.ConfirmationGate() {
            @Override public boolean requiresConfirmation(ResolvedAction action) {
                return action.type() == ActionType.TAP_ELEMENT;
            }
            @Override public String ask(String question) { return "sí, dale"; }
        };

        RecordingCallback cb = new RecordingCallback();
        repo(approve).executeAutonomous("borra la foto", cb);
        cb.await();

        assertThat(cb.completed).isTrue();
        verify(executorPort, times(1)).execute(any());
    }

    @Test
    @DisplayName("Default gate is fail-closed: a local destructive action with no UI gate is denied, not run")
    void defaultGateFailsClosedOnDestructiveAction() throws InterruptedException {
        // The default gate's ask() returns null (no UI) -> not affirmative -> abort.
        when(useCase.execute(any(), any(), any()))
                .thenReturn(action(ActionType.TAP_ELEMENT, Map.of("text", "Pagar"), false));

        RecordingCallback cb = new RecordingCallback();
        new CommandRepository(useCase, executorPort, UUID.randomUUID(), authUseCase,
                CommandRepository.defaultGate(), 0L, 20, Runnable::run)
                .executeAutonomous("paga la factura", cb);
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
    @DisplayName("recognize pre-flight uses a fresh order id, distinct from the loop's order id")
    void preflightOrderIdIsolatedFromLoop() throws InterruptedException {
        when(useCase.execute(any(), any(), any()))
                .thenReturn(action(ActionType.NONE, true));
        when(executorPort.execute(any())).thenReturn(ActionOutcome.ok("done"));

        CommandRepository repo = repo(approveGate());

        RecognizeCallback rc = new RecognizeCallback();
        repo.recognize("abre youtube", rc);
        rc.await();

        RecordingCallback cb = new RecordingCallback();
        repo.executeAutonomous("abre youtube", cb);
        cb.await();

        // Capture the order id (2nd arg) of each execute call.
        ArgumentCaptor<UUID> orderIds = ArgumentCaptor.forClass(UUID.class);
        verify(useCase, atLeastOnce()).execute(any(), orderIds.capture(), any());

        UUID preflightId = orderIds.getAllValues().get(0); // recognize pre-flight
        UUID loopId = orderIds.getAllValues().get(1);       // loop's first step
        assertThat(preflightId).isNotNull();
        assertThat(loopId).isNotNull();
        assertThat(preflightId).isNotEqualTo(loopId);
    }

    @Test
    @DisplayName("user id is the injected shared session id across runs; each run gets a fresh order id")
    void sharedUserIdButPerRunOrderId() throws InterruptedException {
        when(useCase.execute(any(), any(), any()))
                .thenReturn(action(ActionType.NONE, true));

        UUID shared = UUID.randomUUID();
        CommandRepository repo = new CommandRepository(
                useCase, executorPort, shared, authUseCase, approveGate(), 0L, 20, Runnable::run);

        RecordingCallback first = new RecordingCallback();
        repo.executeAutonomous("orden uno", first);
        first.await();

        RecordingCallback second = new RecordingCallback();
        repo.executeAutonomous("orden dos", second);
        second.await();

        ArgumentCaptor<UUID> userIds = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> orderIds = ArgumentCaptor.forClass(UUID.class);
        verify(useCase, times(2)).execute(userIds.capture(), orderIds.capture(), any());

        // Same user id both runs; the two order ids differ (one per run).
        assertThat(userIds.getAllValues().get(0)).isEqualTo(shared);
        assertThat(userIds.getAllValues().get(1)).isEqualTo(shared);
        assertThat(orderIds.getAllValues().get(0)).isNotEqualTo(orderIds.getAllValues().get(1));
    }
}

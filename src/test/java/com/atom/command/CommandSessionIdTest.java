package com.atom.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atom.app.repository.CommandRepository;
import com.atom.application.port.in.ExecuteCommandPortIn;
import com.atom.application.port.in.security.AuthPortIn;
import com.atom.application.port.out.ActionExecutorPortOut;
import com.atom.domain.action.ActionType;
import com.atom.domain.action.ResolvedAction;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Contract test for US-D2: {@link CommandRepository} must use the injected shared
 * session id (not a self-generated random) when it calls
 * {@link ExecuteCommandPortIn#execute(UUID, String)} inside the autonomous loop.
 */
class CommandSessionIdTest {

    /** Synchronous gate — no action requires confirmation in these tests. */
    private static CommandRepository.ConfirmationGate approveAll() {
        return new CommandRepository.ConfirmationGate() {
            @Override public boolean requiresConfirmation(ResolvedAction a) { return false; }
            @Override public boolean confirm(ResolvedAction a) { return true; }
        };
    }

    @Test
    void autonomousLoop_usesTheInjectedSharedSessionId() throws InterruptedException {
        ExecuteCommandPortIn useCase = mock(ExecuteCommandPortIn.class);
        // Return a task_complete action so the loop terminates after one step.
        when(useCase.execute(any(UUID.class), anyString()))
                .thenReturn(new ResolvedAction(
                        ActionType.NONE, Map.of(), "done", 1.0f, false, true, 1));

        ActionExecutorPortOut executor = mock(ActionExecutorPortOut.class);
        AuthPortIn authMock = mock(AuthPortIn.class);

        UUID shared = UUID.randomUUID();
        // Full @VisibleForTesting constructor: sessionUserId injected, synchronous poster.
        CommandRepository repo = new CommandRepository(
                useCase, executor, shared, authMock,
                approveAll(), 0L, 20, Runnable::run);

        CountDownLatch latch = new CountDownLatch(1);
        repo.executeAutonomous("abre spotify", new CommandRepository.AutomationCallback() {
            @Override public void onActionStarted(ResolvedAction a, int s) {}
            @Override public void onComplete(String m)  { latch.countDown(); }
            @Override public void onAborted(String m)   { latch.countDown(); }
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).as("loop terminated").isTrue();

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(useCase, atLeastOnce()).execute(idCaptor.capture(), eq("abre spotify"));
        // The loop must forward the injected shared id, not a self-generated random.
        assertThat(idCaptor.getValue()).isEqualTo(shared);
    }
}

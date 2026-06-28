package com.atom.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.atom.app.data.ConversationRepository;
import com.atom.app.overlay.OperatingCueBus;
import com.atom.app.repository.ChatRepository;
import com.atom.app.repository.CommandRepository;
import com.atom.domain.action.ActionType;
import com.atom.domain.action.ResolvedAction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

class ChatViewModelOperatingCueTest {

    private final ChatRepository chat = mock(ChatRepository.class);
    private final CommandRepository commands = mock(CommandRepository.class);
    private final ConversationRepository conversation = mock(ConversationRepository.class);
    private final BooleanSupplier accessibilityEnabled = () -> true;
    private final OperatingCueBus bus = new OperatingCueBus();

    @BeforeEach
    void setup() {
        androidx.arch.core.executor.ArchTaskExecutor.getInstance().setDelegate(
                new androidx.arch.core.executor.TaskExecutor() {
                    @Override public void executeOnDiskIO(Runnable r) { r.run(); }
                    @Override public void postToMainThread(Runnable r) { r.run(); }
                    @Override public boolean isMainThread() { return true; }
                });
    }

    @AfterEach
    void tearDown() {
        androidx.arch.core.executor.ArchTaskExecutor.getInstance().setDelegate(null);
    }

    private static ResolvedAction openApp() {
        return new ResolvedAction(ActionType.OPEN_APP, Map.of("app_name", "WhatsApp"), "", 1.0f, false);
    }

    @Test
    void runAutonomous_publishesStartedThenFinishedToBus() {
        // Drive the callback synchronously: started(step 2) then complete.
        doAnswer(inv -> {
            CommandRepository.AutomationCallback cb = inv.getArgument(1);
            cb.onActionStarted(openApp(), 2);
            cb.onComplete("All set");
            return null;
        }).when(commands).executeAutonomous(eq("open whatsapp"), any());

        ChatViewModel vm = new ChatViewModel(chat, commands, conversation, accessibilityEnabled, bus);

        List<OperatingCueBus.Cue> seen = new ArrayList<>();
        bus.cues().observeForever(e -> {
            OperatingCueBus.Cue c = e.getContentIfNotHandled();
            if (c != null) {
                seen.add(c);
            }
        });

        vm.runAutonomous("open whatsapp");

        assertThat(seen).hasSize(2);
        assertThat(seen.get(0).kind).isEqualTo(OperatingCueBus.Cue.Kind.STARTED);
        assertThat(seen.get(0).actionType).isEqualTo(ActionType.OPEN_APP);
        assertThat(seen.get(0).step).isEqualTo(2);
        assertThat(seen.get(1).kind).isEqualTo(OperatingCueBus.Cue.Kind.FINISHED);
        assertThat(seen.get(1).message).isEqualTo("All set");
        assertThat(seen.get(1).aborted).isFalse();
    }

    @Test
    void runAutonomous_abort_publishesFinishedAborted() {
        doAnswer(inv -> {
            CommandRepository.AutomationCallback cb = inv.getArgument(1);
            cb.onAborted("Cancelled");
            return null;
        }).when(commands).executeAutonomous(eq("do thing"), any());

        ChatViewModel vm = new ChatViewModel(chat, commands, conversation, accessibilityEnabled, bus);

        List<OperatingCueBus.Cue> seen = new ArrayList<>();
        bus.cues().observeForever(e -> {
            OperatingCueBus.Cue c = e.getContentIfNotHandled();
            if (c != null) {
                seen.add(c);
            }
        });

        vm.runAutonomous("do thing");

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).kind).isEqualTo(OperatingCueBus.Cue.Kind.FINISHED);
        assertThat(seen.get(0).aborted).isTrue();
    }
}

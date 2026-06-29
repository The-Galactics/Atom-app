package com.atom.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import com.atom.app.data.ConversationRepository;
import com.atom.app.overlay.OperatingCueBus;
import com.atom.app.repository.ChatRepository;
import com.atom.app.repository.CommandRepository;
import com.atom.domain.action.ActionType;
import com.atom.domain.action.ResolvedAction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.BooleanSupplier;

class ChatViewModelRoutingTest {

    private final ChatRepository chat = mock(ChatRepository.class);
    private final CommandRepository commands = mock(CommandRepository.class);
    private final ConversationRepository conversation = mock(ConversationRepository.class);
    private final BooleanSupplier accessibilityEnabled = () -> true;
    private final OperatingCueBus bus = new OperatingCueBus();
    private ChatViewModel vm;

    @BeforeEach
    void setUp() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable r) { r.run(); }
            @Override public void postToMainThread(Runnable r) { r.run(); }
            @Override public boolean isMainThread() { return true; }
        });
        vm = new ChatViewModel(chat, commands, conversation, accessibilityEnabled, bus);
    }

    @AfterEach
    void tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    void conversationalTurn_surfacesOutMessage_andSkipsStreamChat() {
        doAnswer(inv -> {
            CommandRepository.CommandCallback cb = inv.getArgument(1);
            cb.onResolved(ResolvedAction.conversation("Hola, respuesta real."));
            return null;
        }).when(commands).recognize(eq("hola"), any());

        vm.sendOrder("hola");

        assertThat(vm.getChatResponse().getValue()).isEqualTo("Hola, respuesta real.");
        verify(conversation).saveAssistantMessage("Hola, respuesta real.");
        verify(chat, never()).askAtom(any(), any());
    }

    @Test
    void awaitingConfirmationTurn_doesNotTakeChatShortcut() {
        ResolvedAction held = new ResolvedAction(
                ActionType.NONE, Map.of(), "¿Confirmas?", 0f, false, false, 0, true);
        doAnswer(inv -> {
            CommandRepository.CommandCallback cb = inv.getArgument(1);
            cb.onResolved(held);
            return null;
        }).when(commands).recognize(eq("borra todo"), any());

        vm.sendOrder("borra todo");

        verify(chat, never()).askAtom(any(), any());
        assertThat(vm.getChatResponse().getValue()).isNull();
    }

    @Test
    void conversationalTurn_emptyOutMessage_fallsBackToStreamChat() {
        doAnswer(inv -> {
            CommandRepository.CommandCallback cb = inv.getArgument(1);
            cb.onResolved(ResolvedAction.conversation(""));
            return null;
        }).when(commands).recognize(eq("hola"), any());

        vm.sendOrder("hola");

        verify(chat).askAtom(eq("hola"), any());
        assertThat(vm.getChatResponse().getValue()).isNotEqualTo("");
    }
}

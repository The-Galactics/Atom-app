package com.atom.app.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atom.app.model.ResponseModel;
import com.atom.application.port.in.StreamChatPortIn;
import com.atom.application.port.in.security.AuthPortIn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Verifies the refresh-ahead contract on the chat path (US-E3 follow-up):
 * {@link ChatRepository#askAtom} must call {@link AuthPortIn#refreshIfNeeded()}
 * BEFORE it calls {@link StreamChatPortIn#messageChat(UUID, UUID, String)}, so
 * that a chat message sent immediately after token expiry refreshes inline instead
 * of sending a stale cached token to the backend.
 *
 * <p>Mirrors the ordering test in {@link CommandRepositoryAutonomousTest}.
 */
class ChatRepositoryRefreshTest {

    @Test
    @DisplayName("Refreshes the token before the chat RPC so a post-expiry send does not use a stale token")
    void refreshesTokenBeforeChatRpc() throws InterruptedException {
        AuthPortIn authUseCase = mock(AuthPortIn.class);
        StreamChatPortIn streamChat = mock(StreamChatPortIn.class);

        // Use a latch triggered inside the messageChat mock so we can await the
        // background executor's work without relying on mainHandler.post (a no-op
        // on JVM unit tests with returnDefaultValues = true).
        CountDownLatch messageChatCalled = new CountDownLatch(1);
        when(streamChat.messageChat(any(UUID.class), any(UUID.class), any(String.class)))
                .thenAnswer(inv -> {
                    messageChatCalled.countDown();
                    return Stream.empty();
                });

        ChatRepository repo = new ChatRepository(
                streamChat,
                UUID.randomUUID(),
                UUID.randomUUID(),
                authUseCase);

        repo.askAtom("hola", new ChatRepository.ChatCallback() {
            @Override public void onSuccess(ResponseModel response) {}
            @Override public void onError(String error) {}
        });

        // Wait until the background executor reaches messageChat; both mocked calls
        // have been recorded by then, so InOrder can verify their sequence.
        assertThat(messageChatCalled.await(5, TimeUnit.SECONDS))
                .as("streamChat.messageChat was called within 5 s").isTrue();

        InOrder inOrder = inOrder(authUseCase, streamChat);
        inOrder.verify(authUseCase).refreshIfNeeded();
        inOrder.verify(streamChat).messageChat(any(UUID.class), any(UUID.class), any(String.class));
    }
}

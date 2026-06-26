package com.atom.app.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.atom.app.model.ResponseModel;
import com.atom.application.port.in.StreamChatPortIn;
import com.atom.application.port.in.security.AuthPortIn;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChatRepository {

    private static final String TAG = "AtomChat";

    private final StreamChatPortIn streamChatUseCase;
    private final AuthPortIn authUseCase;
    private final ExecutorService executor;
    private final Handler mainHandler;

    // Process-scoped conversation identity, injected from AppContainer so every
    // ChatRepository shares the same session and the backend keeps in-session context.
    private final UUID sessionUserId;
    private final UUID sessionChatId;

    public ChatRepository(StreamChatPortIn streamChatUseCase, UUID sessionUserId, UUID sessionChatId,
                          AuthPortIn authUseCase) {
        this.streamChatUseCase = streamChatUseCase;
        this.authUseCase = authUseCase;
        this.sessionUserId = sessionUserId;
        this.sessionChatId = sessionChatId;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void askAtom(String prompt, final ChatCallback callback) {
        executor.execute(() -> {
            try {
                // Refresh-ahead off the call path (US-E3 chat follow-up): the gRPC
                // interceptor is cache-only, so prime a fresh token here (on the
                // background executor) before the protected chat RPC. Mirrors the same
                // pattern used in CommandRepository#executeAutonomous.
                authUseCase.refreshIfNeeded();
                String text;
                try (Stream<String> tokens =
                             streamChatUseCase.messageChat(sessionUserId, sessionChatId, prompt)) {
                    text = tokens.collect(Collectors.joining());
                }

                ResponseModel response = new ResponseModel();
                response.setResponseText(text);
                response.setStatus("OK");
                mainHandler.post(() -> callback.onSuccess(response));
            } catch (Exception e) {
                // Full stack trace (incl. gRPC StatusRuntimeException code + cause) -> Logcat tag "AtomChat".
                Log.e(TAG, "askAtom failed for prompt=\"" + prompt + "\"", e);
                String message = e.getMessage() != null ? e.getMessage() : "Error in server response";
                mainHandler.post(() -> callback.onError(message));
            }
        });
    }

    public interface ChatCallback {
        void onSuccess(ResponseModel response);
        void onError(String error);
    }
}

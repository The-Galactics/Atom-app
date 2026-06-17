package com.atom.app.repository;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.atom.app.model.ResponseModel;
import com.atom.application.port.in.StreamChatPortIn;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChatRepository {

    private static final String TAG = "AtomChat";

    private final StreamChatPortIn streamChatUseCase;
    private final ExecutorService executor;
    private final Handler mainHandler;

    // Per-session id satisfies the use-case contract (no app-side auth yet). See ADR-001.
    private final UUID sessionUserId = UUID.randomUUID();
    private final UUID sessionChatId = UUID.randomUUID();

    public ChatRepository(StreamChatPortIn streamChatUseCase) {
        this.streamChatUseCase = streamChatUseCase;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void askAtom(String prompt, final ChatCallback callback) {
        executor.execute(() -> {
            try {
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

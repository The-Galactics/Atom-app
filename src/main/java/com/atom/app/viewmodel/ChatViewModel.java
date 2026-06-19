package com.atom.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.atom.app.data.ConversationRepository;
import com.atom.app.model.ResponseModel;
import com.atom.app.repository.ChatRepository;
import com.atom.app.repository.CommandRepository;
import com.atom.domain.action.ResolvedAction;

public class ChatViewModel extends ViewModel {
    private final ChatRepository repository;
    private final CommandRepository commandRepository;
    // Transcript store. Turns are persisted here, at the point each backend event
    // actually occurs, rather than in a UI observer — a retained LiveData replays
    // its last value to every new observer, so persisting on the UI side would
    // re-insert the last turn on each Activity re-creation (rotation, theme, and
    // notably the locale switch that recreates Activities). Persisting at the
    // event source makes each turn save exactly once.
    private final ConversationRepository conversationRepository;
    private MutableLiveData<String> chatResponse = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    // Emitted for sensitive actions awaiting user confirmation.
    private MutableLiveData<ResolvedAction> pendingConfirmation = new MutableLiveData<>();

    public ChatViewModel(ChatRepository repository, CommandRepository commandRepository,
                         ConversationRepository conversationRepository) {
        this.repository = repository;
        this.commandRepository = commandRepository;
        this.conversationRepository = conversationRepository;
    }

    public LiveData<String> getChatResponse() { return chatResponse; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<ResolvedAction> getPendingConfirmation() { return pendingConfirmation; }

    /** Free-form conversational message (token-streamed via StreamChat). */
    public void sendMessage(String prompt) {
        isLoading.setValue(true);
        repository.askAtom(prompt, new ChatRepository.ChatCallback() {
            @Override
            public void onSuccess(ResponseModel response) {
                isLoading.setValue(false);
                // Record Atom's conversational reply once, as it arrives.
                conversationRepository.saveAssistantMessage(response.getResponseText());
                chatResponse.setValue(response.getResponseText());
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    /**
     * Treat the input as an ORDER: recognize the intent on the backend, then
     * either run it, ask for confirmation, or (for conversation) show the reply.
     */
    public void sendOrder(String order) {
        // Record the user turn here, at the single entry point for typed and spoken
        // orders, so it persists exactly once regardless of how the order resolves
        // (executed action, confirmation, or conversational reply).
        conversationRepository.saveUserMessage(order);
        isLoading.setValue(true);
        commandRepository.recognize(order, new CommandRepository.CommandCallback() {
            @Override
            public void onResolved(ResolvedAction action) {
                if (!action.isExecutable()) {
                    // Not a command -> route to the conversational chat path
                    // (StreamChat), which keeps in-session context, instead of the
                    // stateless intent reply. isLoading stays true until it returns.
                    sendMessage(order);
                    return;
                }
                isLoading.setValue(false);
                if (action.requiresConfirmation()) {
                    pendingConfirmation.setValue(action);
                } else {
                    runAction(action);
                }
            }

            @Override
            public void onError(String error) {
                isLoading.setValue(false);
                errorMessage.setValue(error);
            }
        });
    }

    /** Execute an action the user has confirmed (or one that needs no confirmation). */
    public void runAction(ResolvedAction action) {
        commandRepository.run(action, outcome -> {
            if (outcome.success()) {
                // Record the executed-action outcome once, as it completes.
                conversationRepository.saveAssistantMessage(outcome.message());
                chatResponse.setValue(outcome.message());
            } else {
                errorMessage.setValue(outcome.message());
            }
        });
    }
}

package com.atom.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.atom.app.model.ResponseModel;
import com.atom.app.repository.ChatRepository;
import com.atom.app.repository.CommandRepository;
import com.atom.domain.action.ResolvedAction;

public class ChatViewModel extends ViewModel {
    private final ChatRepository repository;
    private final CommandRepository commandRepository;
    private MutableLiveData<String> chatResponse = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    // Emitted for sensitive actions awaiting user confirmation.
    private MutableLiveData<ResolvedAction> pendingConfirmation = new MutableLiveData<>();

    public ChatViewModel(ChatRepository repository, CommandRepository commandRepository) {
        this.repository = repository;
        this.commandRepository = commandRepository;
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
        isLoading.setValue(true);
        commandRepository.recognize(order, new CommandRepository.CommandCallback() {
            @Override
            public void onResolved(ResolvedAction action) {
                isLoading.setValue(false);
                if (!action.isExecutable()) {
                    // Conversational turn — just present the reply.
                    chatResponse.setValue(action.outMessage());
                    return;
                }
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
                chatResponse.setValue(outcome.message());
            } else {
                errorMessage.setValue(outcome.message());
            }
        });
    }
}

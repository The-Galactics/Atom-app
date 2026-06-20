package com.atom.app.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.atom.app.di.AppContainer;
import com.atom.app.repository.ChatRepository;
import com.atom.app.repository.CommandRepository;

public class ChatViewModelFactory implements ViewModelProvider.Factory {

    private final AppContainer appContainer;

    public ChatViewModelFactory(AppContainer appContainer) {
        this.appContainer = appContainer;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ChatViewModel.class)) {
            ChatRepository repository =
                    new ChatRepository(
                            appContainer.getExternalMessageUseCase(),
                            appContainer.getSessionUserId(),
                            appContainer.getSessionChatId());
            CommandRepository commandRepository = new CommandRepository(
                    appContainer.getExternalCommandUseCase(),
                    appContainer.getActionExecutor());
            return (T) new ChatViewModel(repository, commandRepository,
                    appContainer.getConversationRepository(),
                    appContainer::isAccessibilityEnabled);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}

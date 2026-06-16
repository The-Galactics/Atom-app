package com.atom.app.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.atom.app.di.AppContainer;
import com.atom.app.repository.ChatRepository;

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
                    new ChatRepository(appContainer.getExternalMessageUseCase());
            return (T) new ChatViewModel(repository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}

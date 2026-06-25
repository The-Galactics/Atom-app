package com.atom.app.viewmodel;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.atom.application.port.in.security.AuthPortIn;

public class AuthViewModelFactory implements ViewModelProvider.Factory {

    private final AuthPortIn auth;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public AuthViewModelFactory(AuthPortIn auth) {
        this.auth = auth;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new AuthViewModel(auth, executor);
    }
}

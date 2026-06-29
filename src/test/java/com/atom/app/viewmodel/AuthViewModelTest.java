package com.atom.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atom.application.port.in.security.AuthPortIn;
import com.atom.domain.security.AuthException;
import com.atom.domain.security.TokenPair;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthViewModelTest {

    private final AuthPortIn auth = mock(AuthPortIn.class);
    // Run tasks synchronously on the calling thread for deterministic assertions.
    private final AuthViewModel vm = new AuthViewModel(auth, Runnable::run);

    @BeforeEach
    void setup() {
        androidx.arch.core.executor.ArchTaskExecutor.getInstance().setDelegate(
                new androidx.arch.core.executor.TaskExecutor() {
                    @Override
                    public void executeOnDiskIO(Runnable r) { r.run(); }
                    @Override
                    public void postToMainThread(Runnable r) { r.run(); }
                    @Override
                    public boolean isMainThread() { return true; }
                });
    }

    @AfterEach
    void tearDown() {
        androidx.arch.core.executor.ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    void login_success_emitsSuccess() {
        when(auth.login("u@b.com", "pw")).thenReturn(new TokenPair("a", "r", 1));

        vm.login("u@b.com", "pw");

        assertThat(vm.state().getValue().getStatus()).isEqualTo(AuthViewModel.Status.SUCCESS);
    }

    @Test
    void login_authException_emitsErrorWithMappedMessage() {
        when(auth.login("u@b.com", "bad"))
                .thenThrow(new AuthException(AuthException.Reason.INVALID_CREDENTIALS, "x"));

        vm.login("u@b.com", "bad");

        assertThat(vm.state().getValue().getStatus()).isEqualTo(AuthViewModel.Status.ERROR);
        assertThat(vm.state().getValue().getErrorMessage()).contains("incorrectos");
    }
}

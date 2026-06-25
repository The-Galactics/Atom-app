package com.atom.app.viewmodel;

import java.util.concurrent.Executor;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.atom.application.port.in.security.AuthPortIn;
import com.atom.domain.security.AuthException;

public class AuthViewModel extends ViewModel {

    public enum Status { IDLE, LOADING, SUCCESS, ERROR }

    public static final class AuthUiState {
        private final Status status;
        private final String errorMessage;

        AuthUiState(Status status, String errorMessage) {
            this.status = status;
            this.errorMessage = errorMessage;
        }

        public Status getStatus() {
            return status;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private final AuthPortIn auth;
    private final Executor executor;
    private final MutableLiveData<AuthUiState> state =
            new MutableLiveData<>(new AuthUiState(Status.IDLE, null));

    public AuthViewModel(AuthPortIn auth, Executor executor) {
        this.auth = auth;
        this.executor = executor;
    }

    public LiveData<AuthUiState> state() {
        return state;
    }

    public void login(String email, String password) {
        run(() -> auth.login(email, password));
    }

    public void register(String email, String password, String displayName) {
        run(() -> auth.register(email, password, displayName));
    }

    public void loginWithGoogle(String idToken) {
        run(() -> auth.loginWithGoogle(idToken));
    }

    private void run(Runnable authCall) {
        state.setValue(new AuthUiState(Status.LOADING, null));
        executor.execute(() -> {
            try {
                authCall.run();
                state.postValue(new AuthUiState(Status.SUCCESS, null));
            } catch (AuthException e) {
                state.postValue(new AuthUiState(Status.ERROR, messageFor(e.getReason())));
            } catch (RuntimeException e) {
                state.postValue(new AuthUiState(Status.ERROR, messageFor(AuthException.Reason.UNKNOWN)));
            }
        });
    }

    /** Maps a reason to a Spanish, user-safe message. Mirrors res/values/strings.xml. */
    private String messageFor(AuthException.Reason reason) {
        switch (reason) {
            case INVALID_CREDENTIALS: return "Email o contraseña incorrectos.";
            case EMAIL_ALREADY_EXISTS: return "Ese email ya está registrado.";
            case INVALID_INPUT: return "Revisa los datos ingresados.";
            case SESSION_EXPIRED: return "Tu sesión expiró, inicia sesión de nuevo.";
            case NETWORK: return "Sin conexión con el servidor. Intenta de nuevo.";
            default: return "Ocurrió un error. Intenta de nuevo.";
        }
    }
}

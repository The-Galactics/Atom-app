package com.atom.application.usecase.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atom.application.port.out.security.AuthGatewayPortOut;
import com.atom.application.port.out.security.SessionListener;
import com.atom.application.port.out.security.TokenStore;
import com.atom.domain.security.AuthException;
import com.atom.domain.security.TokenPair;

import org.junit.jupiter.api.Test;

class AuthUseCaseTest {

    private final AuthGatewayPortOut gateway = mock(AuthGatewayPortOut.class);
    private final TokenStore store = mock(TokenStore.class);
    private long now = 1_000L;
    private final AuthUseCase useCase = new AuthUseCase(gateway, store, () -> now);

    @Test
    void login_success_savesTokenPair() {
        when(gateway.login("u@b.com", "pw")).thenReturn(new TokenPair("acc", "ref", 1_900));

        TokenPair pair = useCase.login("u@b.com", "pw");

        assertThat(pair.getAccessToken()).isEqualTo("acc");
        verify(store).save("acc", "ref", 1_900);
    }

    @Test
    void login_failure_doesNotSave() {
        when(gateway.login("u@b.com", "bad"))
                .thenThrow(new AuthException(AuthException.Reason.INVALID_CREDENTIALS, "x"));

        catchThrowableOfType(() -> useCase.login("u@b.com", "bad"), AuthException.class);

        verify(store, never()).save(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void getValidAccessToken_returnsStoredWhenFresh() {
        when(store.getAccessToken()).thenReturn("acc");
        when(store.getRefreshToken()).thenReturn("ref");
        when(store.getExpiresAtEpochSeconds()).thenReturn(2_000L); // far future vs now=1000

        assertThat(useCase.getValidAccessToken()).isEqualTo("acc");
        verify(gateway, never()).refresh(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getValidAccessToken_refreshesWhenExpired() {
        when(store.getAccessToken()).thenReturn("old");
        when(store.getRefreshToken()).thenReturn("ref");
        when(store.getExpiresAtEpochSeconds()).thenReturn(1_030L); // 1000 + 60 margin >= 1030
        when(gateway.refresh("ref")).thenReturn(new TokenPair("new", "ref2", 1_900));

        assertThat(useCase.getValidAccessToken()).isEqualTo("new");
        verify(store).save("new", "ref2", 1_900);
    }

    @Test
    void getValidAccessToken_refreshFails_clearsAndReturnsNull() {
        when(store.getAccessToken()).thenReturn("old");
        when(store.getRefreshToken()).thenReturn("ref");
        when(store.getExpiresAtEpochSeconds()).thenReturn(1_030L);
        when(gateway.refresh("ref"))
                .thenThrow(new AuthException(AuthException.Reason.SESSION_EXPIRED, "x"));

        assertThat(useCase.getValidAccessToken()).isNull();
        verify(store).clear();
    }

    @Test
    void getValidAccessToken_sessionExpired_clearsAndNotifiesListener() {
        SessionListener listener = mock(SessionListener.class);
        AuthUseCase uc = new AuthUseCase(gateway, store, () -> now, listener);
        when(store.getAccessToken()).thenReturn("old");
        when(store.getRefreshToken()).thenReturn("ref");
        when(store.getExpiresAtEpochSeconds()).thenReturn(1_030L);
        when(gateway.refresh("ref"))
                .thenThrow(new AuthException(AuthException.Reason.SESSION_EXPIRED, "x"));

        assertThat(uc.getValidAccessToken()).isNull();
        verify(store).clear();
        verify(listener).onSessionExpired();
    }

    @Test
    void getValidAccessToken_networkError_doesNotNotifySessionExpired() {
        SessionListener listener = mock(SessionListener.class);
        AuthUseCase uc = new AuthUseCase(gateway, store, () -> now, listener);
        when(store.getAccessToken()).thenReturn("old");
        when(store.getRefreshToken()).thenReturn("ref");
        when(store.getExpiresAtEpochSeconds()).thenReturn(1_030L);
        when(gateway.refresh("ref"))
                .thenThrow(new AuthException(AuthException.Reason.NETWORK, "x"));

        assertThat(uc.getValidAccessToken()).isNull();
        verify(listener, never()).onSessionExpired();
    }

    @Test
    void getValidAccessToken_noSession_returnsNullWithoutRefresh() {
        when(store.getAccessToken()).thenReturn(null);
        when(store.getRefreshToken()).thenReturn(null);

        assertThat(useCase.getValidAccessToken()).isNull();
        verify(gateway, never()).refresh(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void isAuthenticated_trueWhenRefreshTokenPresent() {
        when(store.getRefreshToken()).thenReturn("ref");
        assertThat(useCase.isAuthenticated()).isTrue();
    }

    @Test
    void isAuthenticated_falseWhenNoRefreshToken() {
        when(store.getRefreshToken()).thenReturn(null);
        assertThat(useCase.isAuthenticated()).isFalse();
    }

    @Test
    void register_success_savesTokenPair() {
        when(gateway.register("u@b.com", "pw", "Name")).thenReturn(new TokenPair("acc", "ref", 1_900));
        useCase.register("u@b.com", "pw", "Name");
        verify(store).save("acc", "ref", 1_900);
    }

    @Test
    void loginWithGoogle_success_savesTokenPair() {
        when(gateway.authenticateWithGoogle("idtok")).thenReturn(new TokenPair("acc", "ref", 1_900));
        useCase.loginWithGoogle("idtok");
        verify(store).save("acc", "ref", 1_900);
    }

    @Test
    void logout_clearsStore() {
        useCase.logout();
        verify(store).clear();
    }
}

package com.atom.security;

import com.atom.application.port.out.security.AuthGatewayPortOut;
import com.atom.application.port.out.security.TokenStore;
import com.atom.application.usecase.security.AuthUseCase;
import com.atom.domain.security.TokenPair;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TokenRefreshOffPathTest {

    @Test
    void cachedAccessToken_neverCallsGateway_evenWhenExpired() {
        TokenStore store = mock(TokenStore.class);
        when(store.getAccessToken()).thenReturn("expired");
        when(store.getRefreshToken()).thenReturn("r");
        when(store.getExpiresAtEpochSeconds()).thenReturn(0L);  // long expired
        AuthGatewayPortOut gateway = mock(AuthGatewayPortOut.class);

        AuthUseCase uc = new AuthUseCase(gateway, store, () -> 9_999_999_999L);

        String token = uc.getCachedAccessToken();

        assertThat(token).isEqualTo("expired");          // returns the cache as-is
        verifyNoInteractions(gateway);                   // NEVER refreshes on read
    }
}

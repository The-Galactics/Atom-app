package com.atom.security;

import com.atom.application.port.out.security.AuthGatewayPortOut;
import com.atom.application.port.out.security.TokenStore;
import com.atom.application.usecase.security.AuthUseCase;
import com.atom.domain.security.TokenPair;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ServerUserIdTest {

    @Test
    void login_persistsServerUserId_andExposesIt() {
        TokenStore store = mock(TokenStore.class);
        AuthGatewayPortOut gateway = mock(AuthGatewayPortOut.class);
        when(gateway.login("e", "p"))
                .thenReturn(new TokenPair("a", "r", 1000L, "server-user-123"));
        when(store.getUserId()).thenReturn("server-user-123");

        AuthUseCase uc = new AuthUseCase(gateway, store, () -> 0L);
        uc.login("e", "p");

        verify(store).saveUserId("server-user-123");
        assertThat(uc.getServerUserId()).isEqualTo("server-user-123");
    }
}

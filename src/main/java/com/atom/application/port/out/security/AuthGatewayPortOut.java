package com.atom.application.port.out.security;

import com.atom.domain.security.TokenPair;

/**
 * Driven port for the backend auth RPCs (public — no Bearer required).
 * Implementations throw {@link com.atom.domain.security.AuthException} on failure
 * and never touch the {@link TokenStore} (the use case persists the pair).
 */
public interface AuthGatewayPortOut {

    TokenPair register(String email, String password, String displayName);

    TokenPair login(String email, String password);

    TokenPair authenticateWithGoogle(String idToken);

    TokenPair refresh(String refreshToken);
}

package com.atom.application.usecase.security;

import java.util.function.LongSupplier;

import com.atom.application.port.in.security.AuthPortIn;
import com.atom.application.port.out.security.AuthGatewayPortOut;
import com.atom.application.port.out.security.TokenStore;
import com.atom.domain.security.AuthException;
import com.atom.domain.security.TokenPair;

public class AuthUseCase implements AuthPortIn {

    private static final long REFRESH_MARGIN_SECONDS = 60;

    private final AuthGatewayPortOut gateway;
    private final TokenStore tokenStore;
    private final LongSupplier clockEpochSeconds;

    public AuthUseCase(AuthGatewayPortOut gateway, TokenStore tokenStore,
                       LongSupplier clockEpochSeconds) {
        this.gateway = gateway;
        this.tokenStore = tokenStore;
        this.clockEpochSeconds = clockEpochSeconds;
    }

    @Override
    public TokenPair register(String email, String password, String displayName) {
        return persist(gateway.register(email, password, displayName));
    }

    @Override
    public TokenPair login(String email, String password) {
        return persist(gateway.login(email, password));
    }

    @Override
    public TokenPair loginWithGoogle(String idToken) {
        return persist(gateway.authenticateWithGoogle(idToken));
    }

    @Override
    public void logout() {
        tokenStore.clear();
    }

    @Override
    public boolean isAuthenticated() {
        String refresh = tokenStore.getRefreshToken();
        return refresh != null && !refresh.isEmpty();
    }

    /** Cache-only read for the call path: returns the stored access token without
     *  ever issuing an RPC. The interceptor uses this so a call never blocks on a
     *  refresh under a lock. May return an expired/near-expiry token; refreshing
     *  is the job of {@link #refreshIfNeeded()} off the call path. */
    public String getCachedAccessToken() {
        String access = tokenStore.getAccessToken();
        return (access == null || access.isEmpty()) ? null : access;
    }

    /** Refreshes the access token when it is missing or within the margin. Safe to
     *  call off the call path (app start, a background tick, just before a batch of
     *  calls). Returns the valid access token, or null if there's no session. */
    public synchronized String refreshIfNeeded() {
        return getValidAccessToken();  // existing blocking logic, now OFF the hot path
    }

    @Override
    public synchronized String getValidAccessToken() {
        String access = tokenStore.getAccessToken();
        String refresh = tokenStore.getRefreshToken();
        if (refresh == null || refresh.isEmpty()) {
            return null; // no session
        }
        long now = clockEpochSeconds.getAsLong();
        long expiresAt = tokenStore.getExpiresAtEpochSeconds();
        if (access != null && !access.isEmpty()
                && !new TokenPair(access, refresh, expiresAt).isAccessExpired(now, REFRESH_MARGIN_SECONDS)) {
            return access; // still fresh
        }
        try {
            TokenPair refreshed = persist(gateway.refresh(refresh));
            return refreshed.getAccessToken();
        } catch (AuthException e) {
            tokenStore.clear();
            return null; // refresh rejected -> re-login
        }
    }

    private TokenPair persist(TokenPair pair) {
        tokenStore.save(pair.getAccessToken(), pair.getRefreshToken(),
                pair.getExpiresAtEpochSeconds());
        return pair;
    }
}

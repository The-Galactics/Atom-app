package com.atom.application.usecase.security;

import java.util.function.LongSupplier;

import com.atom.application.port.in.security.AuthPortIn;
import com.atom.application.port.out.security.AuthGatewayPortOut;
import com.atom.application.port.out.security.SessionListener;
import com.atom.application.port.out.security.TokenStore;
import com.atom.domain.security.AuthException;
import com.atom.domain.security.TokenPair;

public class AuthUseCase implements AuthPortIn {

    private static final long REFRESH_MARGIN_SECONDS = 60;

    private final AuthGatewayPortOut gateway;
    private final TokenStore tokenStore;
    private final LongSupplier clockEpochSeconds;
    private final SessionListener sessionListener;

    // In-memory mirror of the token expiry so shouldRefresh() needs no crypto read.
    private volatile long cachedExpiresAtSeconds;

    public AuthUseCase(AuthGatewayPortOut gateway, TokenStore tokenStore,
                       LongSupplier clockEpochSeconds) {
        this(gateway, tokenStore, clockEpochSeconds, SessionListener.NONE);
    }

    public AuthUseCase(AuthGatewayPortOut gateway, TokenStore tokenStore,
                       LongSupplier clockEpochSeconds, SessionListener sessionListener) {
        this.gateway = gateway;
        this.tokenStore = tokenStore;
        this.clockEpochSeconds = clockEpochSeconds;
        this.sessionListener = sessionListener;
        this.cachedExpiresAtSeconds = tokenStore.getExpiresAtEpochSeconds();
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
    @Override
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
            this.cachedExpiresAtSeconds = 0L;
            // The session is gone server-side (UNAUTHENTICATED): signal the UI to
            // redirect to Login now, not only on the next launch (US-10.3).
            if (e.getReason() == AuthException.Reason.SESSION_EXPIRED) {
                sessionListener.onSessionExpired();
            }
            return null; // refresh rejected -> re-login
        }
    }

    private TokenPair persist(TokenPair pair) {
        tokenStore.save(pair.getAccessToken(), pair.getRefreshToken(),
                pair.getExpiresAtEpochSeconds());
        this.cachedExpiresAtSeconds = pair.getExpiresAtEpochSeconds();
        if (pair.getUserId() != null && !pair.getUserId().isEmpty()) {
            tokenStore.saveUserId(pair.getUserId());
        }
        return pair;
    }

    /** Pure decision: refresh when expiry is unknown (&lt;= 0) or within the margin. */
    public static boolean shouldRefresh(long nowSeconds, long cachedExpiresAtSeconds, long marginSeconds) {
        return cachedExpiresAtSeconds <= 0L
                || nowSeconds + marginSeconds >= cachedExpiresAtSeconds;
    }

    /** In-memory only (no EncryptedSharedPreferences read, no RPC). */
    @Override
    public synchronized boolean shouldRefresh() {
        return shouldRefresh(clockEpochSeconds.getAsLong(), cachedExpiresAtSeconds, REFRESH_MARGIN_SECONDS);
    }

    /** The server-verified user id from the last successful auth, or null pre-auth. */
    @Override
    public String getServerUserId() {
        return tokenStore.getUserId();
    }
}

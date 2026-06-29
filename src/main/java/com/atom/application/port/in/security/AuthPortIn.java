package com.atom.application.port.in.security;

import com.atom.domain.security.TokenPair;

/** Driving port for authentication. Implementations persist tokens on success. */
public interface AuthPortIn {

    TokenPair register(String email, String password, String displayName);

    TokenPair login(String email, String password);

    TokenPair loginWithGoogle(String idToken);

    void logout();

    boolean isAuthenticated();

    /** A non-expired access token, refreshing if needed; {@code null} when re-login is required. */
    String getValidAccessToken();

    /** Refreshes the access token off the call path when missing or near-expiry. Call this
     *  before starting an authenticated flow so the (cache-only) interceptor reads a fresh
     *  token. Returns the valid access token, or {@code null} when re-login is required. */
    String refreshIfNeeded();

    /** The server-verified user id from the last successful auth, or {@code null} pre-auth. */
    String getServerUserId();

    /** In-memory fast-path: true when the token is unknown or within the refresh margin.
     *  No EncryptedSharedPreferences read, no RPC. */
    boolean shouldRefresh();
}

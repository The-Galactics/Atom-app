package com.atom.application.port.out.security;

/**
 * Secure storage for the user's session tokens (HU-27).
 *
 * <p>The access token is attached to every protected gRPC call; the refresh token
 * obtains a new pair when the access token expires. Implementations persist them
 * encrypted at rest (see {@code EncryptedTokenStore}).
 */
public interface TokenStore {

    /** Persists the token pair and the access-token expiry (overwrites any previous one). */
    void save(String accessToken, String refreshToken, long expiresAtEpochSeconds);

    /** The current access token, or {@code null} when not signed in. */
    String getAccessToken();

    /** The current refresh token, or {@code null} when not signed in. */
    String getRefreshToken();

    /** Absolute access-token expiry (epoch seconds), or {@code 0} when not signed in. */
    long getExpiresAtEpochSeconds();

    /** Clears the stored tokens (logout). */
    void clear();
}

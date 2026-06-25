package com.atom.application.port.out.security;

/**
 * Secure storage for the user's session tokens (HU-27).
 *
 * <p>The access token is attached to every gRPC call; the refresh token is used to
 * obtain a new pair when the access token expires. Implementations must persist
 * them encrypted at rest (see {@code EncryptedTokenStore}).
 */
public interface TokenStore {

    /** Persists the token pair (overwrites any previous one). */
    void save(String accessToken, String refreshToken);

    /** The current access token, or {@code null} when not signed in. */
    String getAccessToken();

    /** The current refresh token, or {@code null} when not signed in. */
    String getRefreshToken();

    /** Clears the stored tokens (logout). */
    void clear();
}

package com.atom.domain.security;

/** Immutable session tokens with an absolute access-token expiry (epoch seconds). */
public final class TokenPair {

    private final String accessToken;
    private final String refreshToken;
    private final long expiresAtEpochSeconds;
    private final String userId;

    public TokenPair(String accessToken, String refreshToken, long expiresAtEpochSeconds) {
        this(accessToken, refreshToken, expiresAtEpochSeconds, null);
    }

    public TokenPair(String accessToken, String refreshToken,
                     long expiresAtEpochSeconds, String userId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAtEpochSeconds = expiresAtEpochSeconds;
        this.userId = userId;
    }

    /** Builds a pair from the server's relative {@code expires_in}, anchored to {@code now}. */
    public static TokenPair fromExpiresIn(String access, String refresh,
                                          long expiresInSeconds, long nowEpochSeconds) {
        return new TokenPair(access, refresh, nowEpochSeconds + expiresInSeconds);
    }

    /** Builds a pair from the server's relative {@code expires_in}, anchored to {@code now},
     *  capturing the server-verified {@code userId}. */
    public static TokenPair fromExpiresIn(String access, String refresh,
                                          long expiresInSeconds, long nowEpochSeconds,
                                          String userId) {
        return new TokenPair(access, refresh, nowEpochSeconds + expiresInSeconds, userId);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getExpiresAtEpochSeconds() {
        return expiresAtEpochSeconds;
    }

    /** The server-verified user id, or {@code null} when not available. */
    public String getUserId() {
        return userId;
    }

    /** True when the access token is already expired or within {@code marginSeconds} of it. */
    public boolean isAccessExpired(long nowEpochSeconds, long marginSeconds) {
        return nowEpochSeconds + marginSeconds >= expiresAtEpochSeconds;
    }
}

package com.atom.domain.security;

/** Immutable session tokens with an absolute access-token expiry (epoch seconds). */
public final class TokenPair {

    private final String accessToken;
    private final String refreshToken;
    private final long expiresAtEpochSeconds;

    public TokenPair(String accessToken, String refreshToken, long expiresAtEpochSeconds) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAtEpochSeconds = expiresAtEpochSeconds;
    }

    /** Builds a pair from the server's relative {@code expires_in}, anchored to {@code now}. */
    public static TokenPair fromExpiresIn(String access, String refresh,
                                          long expiresInSeconds, long nowEpochSeconds) {
        return new TokenPair(access, refresh, nowEpochSeconds + expiresInSeconds);
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

    /** True when the access token is already expired or within {@code marginSeconds} of it. */
    public boolean isAccessExpired(long nowEpochSeconds, long marginSeconds) {
        return nowEpochSeconds + marginSeconds >= expiresAtEpochSeconds;
    }
}

package com.atom.domain.security;

/** Domain-level authentication failure with a UI-mappable reason. */
public class AuthException extends RuntimeException {

    public enum Reason {
        INVALID_CREDENTIALS,
        EMAIL_ALREADY_EXISTS,
        INVALID_INPUT,
        SESSION_EXPIRED,
        NETWORK,
        UNKNOWN
    }

    private final Reason reason;

    public AuthException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}

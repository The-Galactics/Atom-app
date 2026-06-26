package com.atom.application.port.out.security;

/**
 * Notified when the user's session ends server-side — i.e. a token refresh is
 * rejected as UNAUTHENTICATED (US-10.3). Lets the UI layer wipe to the Login
 * screen in real time, not only on the next app launch.
 */
public interface SessionListener {

    void onSessionExpired();

    /** No-op default for contexts that don't observe session expiry (e.g. tests). */
    SessionListener NONE = () -> { };
}

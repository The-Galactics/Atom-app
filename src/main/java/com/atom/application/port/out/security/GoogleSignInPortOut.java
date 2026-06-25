package com.atom.application.port.out.security;

import android.app.Activity;

/** Obtains a Google OIDC ID token to exchange with the backend's AuthenticateWithGoogle RPC. */
public interface GoogleSignInPortOut {

    interface Callback {
        void onIdToken(String idToken);
        void onError(String message);
    }

    void requestIdToken(Activity host, Callback callback);
}

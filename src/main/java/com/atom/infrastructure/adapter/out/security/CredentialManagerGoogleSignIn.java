package com.atom.infrastructure.adapter.out.security;

import android.app.Activity;
import android.os.CancellationSignal;

import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.atom.application.port.out.security.GoogleSignInPortOut;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.util.concurrent.Executors;

/** Credential Manager + Google Identity Services implementation. */
public class CredentialManagerGoogleSignIn implements GoogleSignInPortOut {

    private final String serverClientId;

    public CredentialManagerGoogleSignIn(String serverClientId) {
        this.serverClientId = serverClientId;
    }

    @Override
    public void requestIdToken(Activity host, Callback callback) {
        CredentialManager credentialManager = CredentialManager.create(host);
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        credentialManager.getCredentialAsync(
                host, request, new CancellationSignal(), Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        try {
                            GoogleIdTokenCredential cred = GoogleIdTokenCredential
                                    .createFrom(response.getCredential().getData());
                            callback.onIdToken(cred.getIdToken());
                        } catch (Exception e) {
                            callback.onError("google credential parse failed");
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        callback.onError("google sign-in failed");
                    }
                });
    }
}

package com.atom.infrastructure.adapter.grpc;

import java.util.function.LongSupplier;

import com.atom.application.port.out.security.AuthGatewayPortOut;
import com.atom.domain.security.AuthException;
import com.atom.domain.security.TokenPair;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/** Calls the public auth RPCs over the raw (non-intercepted) channel's blocking stub. */
public class AuthGrpcAdapter implements AuthGatewayPortOut {

    private final AtomAgentServiceGrpc.AtomAgentServiceBlockingStub stub;
    private final LongSupplier clockEpochSeconds;

    public AuthGrpcAdapter(AtomAgentServiceGrpc.AtomAgentServiceBlockingStub stub,
                           LongSupplier clockEpochSeconds) {
        this.stub = stub;
        this.clockEpochSeconds = clockEpochSeconds;
    }

    @Override
    public TokenPair register(String email, String password, String displayName) {
        try {
            return toPair(stub
                    .withDeadlineAfter(15, java.util.concurrent.TimeUnit.SECONDS)
                    .register(RegisterRequest.newBuilder()
                    .setEmail(email).setPassword(password)
                    .setDisplayName(displayName == null ? "" : displayName)
                    .build()));
        } catch (StatusRuntimeException e) {
            throw translate(e, false);
        }
    }

    @Override
    public TokenPair login(String email, String password) {
        try {
            return toPair(stub
                    .withDeadlineAfter(15, java.util.concurrent.TimeUnit.SECONDS)
                    .login(LoginRequest.newBuilder()
                    .setEmail(email).setPassword(password).build()));
        } catch (StatusRuntimeException e) {
            throw translate(e, false);
        }
    }

    @Override
    public TokenPair authenticateWithGoogle(String idToken) {
        try {
            return toPair(stub
                    .withDeadlineAfter(15, java.util.concurrent.TimeUnit.SECONDS)
                    .authenticateWithGoogle(GoogleAuthRequest.newBuilder()
                    .setIdToken(idToken).build()));
        } catch (StatusRuntimeException e) {
            throw translate(e, false);
        }
    }

    @Override
    public TokenPair refresh(String refreshToken) {
        try {
            return toPair(stub
                    .withDeadlineAfter(15, java.util.concurrent.TimeUnit.SECONDS)
                    .refreshToken(RefreshRequest.newBuilder()
                    .setRefreshToken(refreshToken).build()));
        } catch (StatusRuntimeException e) {
            throw translate(e, true);
        }
    }

    private TokenPair toPair(AuthResponse r) {
        return TokenPair.fromExpiresIn(
                r.getAccessToken(), r.getRefreshToken(), r.getExpiresIn(),
                clockEpochSeconds.getAsLong());
    }

    /** Maps a gRPC status to an {@link AuthException.Reason}. {@code refreshFlow}
     *  reinterprets UNAUTHENTICATED as a stale session rather than bad credentials. */
    private AuthException translate(StatusRuntimeException e, boolean refreshFlow) {
        Status.Code code = e.getStatus().getCode();
        AuthException.Reason reason;
        switch (code) {
            case UNAUTHENTICATED:
                reason = refreshFlow
                        ? AuthException.Reason.SESSION_EXPIRED
                        : AuthException.Reason.INVALID_CREDENTIALS;
                break;
            case ALREADY_EXISTS:
                reason = AuthException.Reason.EMAIL_ALREADY_EXISTS;
                break;
            case INVALID_ARGUMENT:
                reason = AuthException.Reason.INVALID_INPUT;
                break;
            case UNAVAILABLE:
            case DEADLINE_EXCEEDED:
                reason = AuthException.Reason.NETWORK;
                break;
            default:
                reason = AuthException.Reason.UNKNOWN;
        }
        return new AuthException(reason, "auth rpc failed: " + code);
    }
}

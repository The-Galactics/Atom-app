package com.atom.infrastructure.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atom.domain.security.AuthException;
import com.atom.domain.security.TokenPair;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;

class AuthGrpcAdapterTest {

    private final AtomAgentServiceGrpc.AtomAgentServiceBlockingStub stub =
            mock(AtomAgentServiceGrpc.AtomAgentServiceBlockingStub.class);
    private final AuthGrpcAdapter adapter = new AuthGrpcAdapter(stub, () -> 1_000L);

    @Test
    void login_mapsAuthResponseToTokenPair() {
        AuthResponse response = AuthResponse.newBuilder()
                .setAccessToken("acc").setRefreshToken("ref").setExpiresIn(900).build();
        when(stub.login(LoginRequest.newBuilder().setEmail("u@b.com").setPassword("pw").build()))
                .thenReturn(response);

        TokenPair pair = adapter.login("u@b.com", "pw");

        assertThat(pair.getAccessToken()).isEqualTo("acc");
        assertThat(pair.getRefreshToken()).isEqualTo("ref");
        assertThat(pair.getExpiresAtEpochSeconds()).isEqualTo(1_900); // 1000 + 900
    }

    @Test
    void login_unauthenticated_mapsToInvalidCredentials() {
        when(stub.login(LoginRequest.newBuilder().setEmail("u@b.com").setPassword("bad").build()))
                .thenThrow(new StatusRuntimeException(Status.UNAUTHENTICATED));

        AuthException ex = catchThrowableOfType(
                () -> adapter.login("u@b.com", "bad"), AuthException.class);

        assertThat(ex.getReason()).isEqualTo(AuthException.Reason.INVALID_CREDENTIALS);
    }

    @Test
    void register_alreadyExists_mapsToEmailAlreadyExists() {
        when(stub.register(RegisterRequest.newBuilder()
                .setEmail("u@b.com").setPassword("pw").setDisplayName("U").build()))
                .thenThrow(new StatusRuntimeException(Status.ALREADY_EXISTS));

        AuthException ex = catchThrowableOfType(
                () -> adapter.register("u@b.com", "pw", "U"), AuthException.class);

        assertThat(ex.getReason()).isEqualTo(AuthException.Reason.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void refresh_unauthenticated_mapsToSessionExpired() {
        when(stub.refreshToken(RefreshRequest.newBuilder().setRefreshToken("ref").build()))
                .thenThrow(new StatusRuntimeException(Status.UNAUTHENTICATED));

        AuthException ex = catchThrowableOfType(
                () -> adapter.refresh("ref"), AuthException.class);

        assertThat(ex.getReason()).isEqualTo(AuthException.Reason.SESSION_EXPIRED);
    }

    @Test
    void login_invalidArgument_mapsToInvalidInput() {
        when(stub.login(LoginRequest.newBuilder().setEmail("u@b.com").setPassword("pw").build()))
                .thenThrow(new StatusRuntimeException(Status.INVALID_ARGUMENT));
        AuthException ex = catchThrowableOfType(() -> adapter.login("u@b.com", "pw"), AuthException.class);
        assertThat(ex.getReason()).isEqualTo(AuthException.Reason.INVALID_INPUT);
    }

    @Test
    void login_unavailable_mapsToNetwork() {
        when(stub.login(LoginRequest.newBuilder().setEmail("u@b.com").setPassword("pw").build()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
        AuthException ex = catchThrowableOfType(() -> adapter.login("u@b.com", "pw"), AuthException.class);
        assertThat(ex.getReason()).isEqualTo(AuthException.Reason.NETWORK);
    }

    @Test
    void login_deadlineExceeded_mapsToNetwork() {
        when(stub.login(LoginRequest.newBuilder().setEmail("u@b.com").setPassword("pw").build()))
                .thenThrow(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));
        AuthException ex = catchThrowableOfType(() -> adapter.login("u@b.com", "pw"), AuthException.class);
        assertThat(ex.getReason()).isEqualTo(AuthException.Reason.NETWORK);
    }

    @Test
    void login_internalError_mapsToUnknown() {
        when(stub.login(LoginRequest.newBuilder().setEmail("u@b.com").setPassword("pw").build()))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));
        AuthException ex = catchThrowableOfType(() -> adapter.login("u@b.com", "pw"), AuthException.class);
        assertThat(ex.getReason()).isEqualTo(AuthException.Reason.UNKNOWN);
    }
}

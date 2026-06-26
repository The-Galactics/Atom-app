package com.atom.infrastructure.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

import org.junit.jupiter.api.Test;

class BearerTokenClientInterceptorTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Metadata headersAfterStart(String token) {
        Channel channel = mock(Channel.class);
        ClientCall delegate = mock(ClientCall.class);
        when(channel.newCall(any(), any())).thenReturn(delegate);

        ClientCall call = new BearerTokenClientInterceptor(() -> token)
                .interceptCall(mock(MethodDescriptor.class), CallOptions.DEFAULT, channel);

        Metadata headers = new Metadata();
        call.start(mock(ClientCall.Listener.class), headers);
        return headers;
    }

    @Test
    void attachesBearerHeaderWhenTokenPresent() {
        Metadata headers = headersAfterStart("abc123");
        assertThat(headers.get(BearerTokenClientInterceptor.AUTHORIZATION))
                .isEqualTo("Bearer abc123");
    }

    @Test
    void omitsHeaderWhenTokenNull() {
        assertThat(headersAfterStart(null)
                .containsKey(BearerTokenClientInterceptor.AUTHORIZATION)).isFalse();
    }

    @Test
    void omitsHeaderWhenTokenEmpty() {
        assertThat(headersAfterStart("")
                .containsKey(BearerTokenClientInterceptor.AUTHORIZATION)).isFalse();
    }
}

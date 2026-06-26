package com.atom.infrastructure.adapter.grpc;

import java.util.function.Supplier;

import com.atom.app.BuildConfig;

import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

/**
 * Owns the single gRPC {@link ManagedChannel} and exposes two views:
 * a raw channel for the public auth RPCs, and a Bearer-intercepted channel for
 * protected RPCs. TLS in release builds; plaintext in debug builds.
 */
public class GrpcChannelProvider {

    private final ManagedChannel channel;
    private final Channel authedChannel;

    public GrpcChannelProvider(String host, int port, Supplier<String> accessTokenSupplier) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        // Debug builds use plaintext (dev agent is insecure); release keeps TLS.
        if (BuildConfig.DEBUG) {
            builder.usePlaintext();
        }
        this.channel = builder.build();
        this.authedChannel = ClientInterceptors.intercept(
                channel, new BearerTokenClientInterceptor(accessTokenSupplier));
    }

    /** Raw channel (no Bearer) — for public auth RPCs. */
    public Channel getRawChannel() {
        return channel;
    }

    /** Bearer-intercepted channel — for protected RPCs. */
    public Channel getAuthedChannel() {
        return authedChannel;
    }

    public void shutdown() {
        if (!channel.isShutdown()) {
            channel.shutdown();
        }
    }
}

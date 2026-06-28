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
 * protected RPCs. TLS is controlled by the GRPC_TLS flag (local.properties ->
 * BuildConfig.GRPC_TLS), independent of the debug/release build type.
 */
public class GrpcChannelProvider {

    private final ManagedChannel channel;
    private final Channel authedChannel;

    public GrpcChannelProvider(String host, int port, Supplier<String> accessTokenSupplier) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        // TLS is driven by the GRPC_TLS flag from local.properties (wired into
        // BuildConfig), not the build type: a debug build can still talk to the
        // TLS production endpoint, and a release build can talk plaintext locally.
        if (!BuildConfig.GRPC_TLS) {
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

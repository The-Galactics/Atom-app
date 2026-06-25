package com.atom.infrastructure.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class TokenSupplierHolderTest {

    @Test
    void supplier_returnsNullBeforeWiring_thenDelegates() {
        AtomicReference<Supplier<String>> holder = new AtomicReference<>(() -> null);
        Supplier<String> deferred = () -> holder.get().get();

        assertThat(deferred.get()).isNull();

        holder.set(() -> "live-token");
        assertThat(deferred.get()).isEqualTo("live-token");
    }
}

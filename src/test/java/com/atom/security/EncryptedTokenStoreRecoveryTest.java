package com.atom.security;

import com.atom.infrastructure.adapter.out.security.EncryptedTokenStore;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptedTokenStoreRecoveryTest {

    @Test
    void recoversOnceWhenFirstAttemptFails() {
        int[] calls = {0};
        EncryptedTokenStore.PrefsFactory factory = (attempt) -> {
            calls[0]++;
            if (attempt == 0) throw new IllegalStateException("master key invalidated");
            return new Object();  // second attempt "succeeds"
        };
        boolean[] wiped = {false};
        Object prefs = EncryptedTokenStore.openWithRecovery(factory, () -> wiped[0] = true);
        assertThat(calls[0]).isEqualTo(2);
        assertThat(wiped[0]).isTrue();
        assertThat(prefs).isNotNull();
    }

    @Test
    void rethrowsWhenRecoveryAlsoFails() {
        EncryptedTokenStore.PrefsFactory alwaysFails = (attempt) -> {
            throw new IllegalStateException("still broken");
        };
        assertThrows(IllegalStateException.class,
                () -> EncryptedTokenStore.openWithRecovery(alwaysFails, () -> {}));
    }
}

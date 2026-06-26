package com.atom.infrastructure.adapter.action;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Intent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-11.1 / 2B.5: a fuzzy contact match must open the dialer for confirmation —
 * even when CALL_PHONE is granted — while an exact match (or a typed number) may
 * dial directly. {@code Intent.ACTION_*} are compile-time String constants, so
 * this is testable without an Android runtime.
 */
class CallActionChoiceTest {

    @Test
    @DisplayName("Exact match with CALL_PHONE dials directly")
    void exactWithPermissionDialsDirect() {
        assertThat(AndroidActionExecutor.chooseCallAction(true, true))
                .isEqualTo(Intent.ACTION_CALL);
    }

    @Test
    @DisplayName("Fuzzy match opens the dialer even with CALL_PHONE granted")
    void fuzzyWithPermissionOpensDialer() {
        assertThat(AndroidActionExecutor.chooseCallAction(true, false))
                .isEqualTo(Intent.ACTION_DIAL);
    }

    @Test
    @DisplayName("Without CALL_PHONE, always opens the dialer")
    void withoutPermissionOpensDialer() {
        assertThat(AndroidActionExecutor.chooseCallAction(false, true))
                .isEqualTo(Intent.ACTION_DIAL);
        assertThat(AndroidActionExecutor.chooseCallAction(false, false))
                .isEqualTo(Intent.ACTION_DIAL);
    }
}

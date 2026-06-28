package com.atom.app.permission;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PermissionGateTest {

    @Test
    void missingCoreCount_countsOnlyUngrantedCorePerms() {
        assertThat(PermissionGate.missingCoreCount(true, true, true)).isZero();
        assertThat(PermissionGate.missingCoreCount(false, true, true)).isEqualTo(1);
        assertThat(PermissionGate.missingCoreCount(false, false, false)).isEqualTo(3);
    }

    @Test
    void canContinue_onlyWhenAllCoreGranted() {
        assertThat(PermissionGate.canContinue(true, true, true)).isTrue();
        assertThat(PermissionGate.canContinue(true, false, true)).isFalse();
    }
}

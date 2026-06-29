package com.atom.validationSecurity;

import com.atom.application.port.in.security.DeviceSecurityPort;
import com.atom.application.usecase.security.DeviceSecurityGuard;
import com.atom.domain.model.security.DeviceSecurityStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios para DeviceSecurityGuard.
 * Mockea DeviceSecurityPort para verificar la política fail-closed y el verdicto opaco.
 */
@ExtendWith(MockitoExtension.class)
class DeviceSecurityGuardTest {

    @Mock
    private DeviceSecurityPort security;

    @Test
    @DisplayName("Safe (LOW) device is reported as safe")
    void safeWhenLowRisk() {
        when(security.verifyDeviceIntegrity()).thenReturn(DeviceSecurityStatus.safe());

        assertThat(new DeviceSecurityGuard(security).isEnvironmentSafe()).isTrue();
    }

    @Test
    @DisplayName("MEDIUM risk (emulator only) is allowed — policy blocks only HIGH")
    void safeWhenMediumRisk() {
        when(security.verifyDeviceIntegrity())
                .thenReturn(DeviceSecurityStatus.risky(false, true, false, "EMULATOR_ENVIRONMENT"));

        assertThat(new DeviceSecurityGuard(security).isEnvironmentSafe()).isTrue();
    }

    @Test
    @DisplayName("HIGH risk (rooted) is rejected as unsafe")
    void unsafeWhenHighRisk() {
        when(security.verifyDeviceIntegrity())
                .thenReturn(DeviceSecurityStatus.risky(true, false, false, "ROOT_ACCESS"));

        assertThat(new DeviceSecurityGuard(security).isEnvironmentSafe()).isFalse();
    }

    @Test
    @DisplayName("Fail-closed: a detection that throws is treated as unsafe, not clean")
    void unsafeWhenDetectionThrows() {
        when(security.verifyDeviceIntegrity()).thenThrow(new RuntimeException("probe failure"));

        assertThat(new DeviceSecurityGuard(security).isEnvironmentSafe()).isFalse();
    }

    @Test
    @DisplayName("refresh() caches the verdict — later reads do not re-run the blocking probes")
    void cachesVerdictAfterRefresh() {
        when(security.verifyDeviceIntegrity()).thenReturn(DeviceSecurityStatus.safe());
        DeviceSecurityGuard guard = new DeviceSecurityGuard(security);

        guard.refresh();
        assertThat(guard.isEnvironmentSafe()).isTrue();
        assertThat(guard.isEnvironmentSafe()).isTrue();

        verify(security, times(1)).verifyDeviceIntegrity();
    }
}

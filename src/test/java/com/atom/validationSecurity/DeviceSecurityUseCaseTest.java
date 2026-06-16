package com.atom.validationSecurity;


import com.atom.application.exception.DeviceSecurityException;
import com.atom.application.port.out.security.DeviceInspectorPort;
import com.atom.application.usecase.security.DeviceSecurityUseCase;
import com.atom.domain.model.security.DeviceSecurityStatus;
import com.atom.domain.utils.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para DeviceSecurityUseCase.
 * El DeviceInspectorPort es mockeado para aislar el caso de uso del hardware.
 */
@ExtendWith(MockitoExtension.class)
class DeviceSecurityUseCaseTest {

    @Mock
    private DeviceInspectorPort deviceInspector;

    @InjectMocks
    private DeviceSecurityUseCase useCase;

    @Test
    @DisplayName("Should return LOW risk when device is clean")
    void shouldReturnSafeStatusOnCleanDevice() {
        when(deviceInspector.isRooted()).thenReturn(false);
        when(deviceInspector.isRunningOnEmulator()).thenReturn(false);
        when(deviceInspector.isProxyActive()).thenReturn(false);

        DeviceSecurityStatus status = useCase.verifyDeviceIntegrity();

        assertThat(status.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(status.isHighRisk()).isFalse();
    }

    @Test
    @DisplayName("Should return HIGH risk on rooted device")
    void shouldReturnHighRiskOnRootedDevice() {
        when(deviceInspector.isRooted()).thenReturn(true);
        when(deviceInspector.isRunningOnEmulator()).thenReturn(false);
        when(deviceInspector.isProxyActive()).thenReturn(false);

        DeviceSecurityStatus status = useCase.verifyDeviceIntegrity();

        assertThat(status.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(status.rooted()).isTrue();
        assertThat(status.detail()).contains("ROOT_ACCESS");
    }

    @Test
    @DisplayName("Should return HIGH risk when rooted + emulator + proxy")
    void shouldReturnHighRiskOnAllFactors() {
        when(deviceInspector.isRooted()).thenReturn(true);
        when(deviceInspector.isRunningOnEmulator()).thenReturn(true);
        when(deviceInspector.isProxyActive()).thenReturn(true);

        DeviceSecurityStatus status = useCase.verifyDeviceIntegrity();

        assertThat(status.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(status.detail()).contains("ROOT_ACCESS", "EMULATOR_ENVIRONMENT", "ACTIVE_PROXY");
    }

    @Test
    @DisplayName("Should return MEDIUM risk on emulator only")
    void shouldReturnMediumRiskOnEmulatorOnly() {
        when(deviceInspector.isRooted()).thenReturn(false);
        when(deviceInspector.isRunningOnEmulator()).thenReturn(true);
        when(deviceInspector.isProxyActive()).thenReturn(false);

        DeviceSecurityStatus status = useCase.verifyDeviceIntegrity();

        assertThat(status.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    @DisplayName("Should throw DeviceSecurityException when device is HIGH risk")
    void shouldThrowOnHighRiskDevice() {
        when(deviceInspector.isRooted()).thenReturn(true);
        when(deviceInspector.isRunningOnEmulator()).thenReturn(false);
        when(deviceInspector.isProxyActive()).thenReturn(false);

        DeviceSecurityStatus status = useCase.verifyDeviceIntegrity();

        assertThatThrownBy(() -> useCase.shouldBlockOperation(status))
                .isInstanceOf(DeviceSecurityException.class)
                .satisfies(ex -> {
                    DeviceSecurityStatus s = ((DeviceSecurityException) ex).getStatus();
                    assertThat(s.riskLevel()).isEqualTo(RiskLevel.HIGH);
                });
    }

    @Test
    @DisplayName("Should not block on LOW risk device")
    void shouldNotBlockOnLowRiskDevice() {
        when(deviceInspector.isRooted()).thenReturn(false);
        when(deviceInspector.isRunningOnEmulator()).thenReturn(false);
        when(deviceInspector.isProxyActive()).thenReturn(false);

        DeviceSecurityStatus status = useCase.verifyDeviceIntegrity();
        boolean blocked = useCase.shouldBlockOperation(status);

        assertThat(blocked).isFalse();
    }
}

package com.atom.application.usecase.security;


import com.atom.application.exception.DeviceSecurityException;
import com.atom.application.port.in.security.DeviceSecurityPort;
import com.atom.application.port.out.security.DeviceInspectorPort;
import com.atom.domain.model.security.DeviceSecurityStatus;

import java.util.StringJoiner;

public class DeviceSecurityUseCase implements DeviceSecurityPort {

    private final DeviceInspectorPort deviceInspector;

    public DeviceSecurityUseCase(DeviceInspectorPort deviceInspector) {
        this.deviceInspector = deviceInspector;
    }

    @Override
    public DeviceSecurityStatus verifyDeviceIntegrity() {
        boolean rooted   = deviceInspector.isRooted();
        boolean emulator = deviceInspector.isRunningOnEmulator();
        boolean proxy    = deviceInspector.isProxyActive();

        if (!rooted && !emulator && !proxy) {
            return DeviceSecurityStatus.safe();
        }

        String detail = buildDetail(rooted, emulator, proxy);
        return DeviceSecurityStatus.risky(rooted, emulator, proxy, detail);
    }

    @Override
    public boolean shouldBlockOperation(DeviceSecurityStatus status) {
        if (status.isHighRisk()) {
            throw new DeviceSecurityException(status);
        }
        return false;
    }

    private String buildDetail(boolean rooted, boolean emulator, boolean proxy) {
        StringJoiner sj = new StringJoiner(", ", "Risk factors detected: [", "]");
        if (rooted)   sj.add("ROOT_ACCESS");
        if (emulator) sj.add("EMULATOR_ENVIRONMENT");
        if (proxy)    sj.add("ACTIVE_PROXY");
        return sj.toString();
    }
}

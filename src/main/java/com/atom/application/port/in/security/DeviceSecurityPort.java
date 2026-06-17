package com.atom.application.port.in.security;

import com.atom.domain.model.security.DeviceSecurityStatus;

public interface DeviceSecurityPort {
    DeviceSecurityStatus verifyDeviceIntegrity();
    boolean shouldBlockOperation(DeviceSecurityStatus status);
}

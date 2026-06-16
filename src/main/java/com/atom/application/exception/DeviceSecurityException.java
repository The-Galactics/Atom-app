package com.atom.application.exception;

import com.atom.domain.model.security.DeviceSecurityStatus;

public class DeviceSecurityException extends RuntimeException{
    private final DeviceSecurityStatus status;

    public DeviceSecurityException(DeviceSecurityStatus status) {
        super("Operation blocked. High-risk device environment detected: " + status.detail());
        this.status = status;
    }

    public DeviceSecurityStatus getStatus() {
        return status;
    }
}

package com.atom.application.exception;

import com.atom.domain.model.security.DeviceSecurityStatus;

public class DeviceSecurityException extends RuntimeException{
    private final DeviceSecurityStatus status;

    public DeviceSecurityException(DeviceSecurityStatus status) {
        // Generic message only: the verdict detail (ROOT_ACCESS, binary paths, …) must
        // never reach getMessage()/logs/UI in release, or it leaks the detector internals
        // to an attacker. Internal callers read the factors via getStatus().
        super("Operation blocked: high-risk device environment.");
        this.status = status;
    }

    public DeviceSecurityStatus getStatus() {
        return status;
    }
}

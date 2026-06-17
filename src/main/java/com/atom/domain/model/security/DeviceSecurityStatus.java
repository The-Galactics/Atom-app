package com.atom.domain.model.security;

import com.atom.domain.utils.RiskLevel;

public record DeviceSecurityStatus(
        boolean rooted,
        boolean runningOnEmulator,
        boolean proxyDetected,
        RiskLevel riskLevel,
        String detail
) {


    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH;
    }

    public static DeviceSecurityStatus safe() {
        return new DeviceSecurityStatus(false, false, false, RiskLevel.LOW, "Device environment is clean.");
    }

    public static DeviceSecurityStatus risky(boolean rooted, boolean emulator, boolean proxy, String detail) {
        RiskLevel level = resolveRisk(rooted, emulator, proxy);
        return new DeviceSecurityStatus(rooted, emulator, proxy, level, detail);
    }

    private static RiskLevel resolveRisk(boolean rooted, boolean emulator, boolean proxy) {
        int riskScore = 0;
        if (rooted)    riskScore += 3;
        if (emulator)  riskScore += 2;
        if (proxy)     riskScore += 1;

        if (riskScore >= 3) return RiskLevel.HIGH;
        if (riskScore >= 1) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
}

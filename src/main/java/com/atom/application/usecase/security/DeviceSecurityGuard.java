package com.atom.application.usecase.security;

import com.atom.application.port.in.security.DeviceSecurityPort;
import com.atom.domain.model.security.DeviceSecurityStatus;

/**
 * Fail-closed, opaque gate over {@link DeviceSecurityPort}.
 *
 * <p>Callers in the presentation layer only learn <em>whether</em> the runtime
 * environment is safe — never <em>why</em> it was rejected. The verdict detail
 * ({@code ROOT_ACCESS}, binary paths, …) stays inside the use case and is never
 * surfaced through this boolean, so an attacker cannot probe the detector by
 * reading an error message.
 *
 * <p><b>Threading.</b> The underlying integrity probes do disk I/O and spawn a
 * subprocess ({@code which su}), so they must not run on the main thread. Call
 * {@link #refresh()} once at startup from a background thread; the protected
 * services then read the cached verdict via {@link #isEnvironmentSafe()} with no
 * blocking work on their start path.
 *
 * <p>Pure (no Android dependencies) so it is unit-testable on the JVM, mirroring
 * {@code DeviceSecurityUseCase}.
 */
public final class DeviceSecurityGuard {

    private final DeviceSecurityPort security;

    // Verdict computed off the main thread. null = not evaluated yet.
    private volatile Boolean cachedSafe;

    public DeviceSecurityGuard(DeviceSecurityPort security) {
        this.security = security;
    }

    /**
     * Computes and caches the verdict. Intended to run on a background thread.
     * Idempotent and safe to call repeatedly (e.g. to re-attest).
     *
     * @return the freshly computed verdict.
     */
    public boolean refresh() {
        boolean safe = evaluate();
        cachedSafe = safe;
        return safe;
    }

    /**
     * @return {@code true} only when the environment is verifiably safe. Reads the
     *         cached verdict when {@link #refresh()} has already run (the fast,
     *         non-blocking path); on a cold cache it evaluates synchronously rather
     *         than failing open. Any failure — a HIGH-risk verdict <em>or</em> a
     *         detection that throws — resolves to {@code false} (default deny),
     *         compensating for the fail-open {@code catch} in
     *         {@code DeviceInspectorAdapter}.
     */
    public boolean isEnvironmentSafe() {
        Boolean cached = cachedSafe;
        return cached != null ? cached : refresh();
    }

    private boolean evaluate() {
        try {
            DeviceSecurityStatus status = security.verifyDeviceIntegrity();
            // Single source of truth for the block policy: DeviceSecurityStatus.isHighRisk().
            return !status.isHighRisk();
        } catch (Throwable failClosed) {
            return false;
        }
    }
}

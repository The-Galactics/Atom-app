package com.atom.app.security;

import android.content.Context;
import android.content.Intent;

import com.atom.app.overlay.FloatingBubbleService;
import com.atom.infrastructure.adapter.accessibility.AtomAccessibilityService;
import com.atom.infrastructure.adapter.wake.WakeWordService;

/**
 * Silent, idempotent teardown of every component that grants Atom elevated reach
 * over the device — the overlay window ({@code SYSTEM_ALERT_WINDOW}), the wake-word
 * microphone service, and the accessibility service ({@code BIND_ACCESSIBILITY_SERVICE},
 * which can drive the autonomous action loop).
 *
 * <p>Invoked when {@link com.atom.application.usecase.security.DeviceSecurityGuard}
 * rejects the runtime environment, from any of the three protected services. Every
 * step is wrapped so a failure to stop one component never aborts stopping the
 * others, and never crashes the app. No dialog or detail is shown: the verdict stays
 * opaque to the user/attacker.
 *
 * <p>Uses {@code stopService} (not {@code startService(ACTION_STOP)}): it reliably
 * stops a running service without tripping background-start limits on Android 8+,
 * and is a no-op when the service is not running.
 */
public final class SecureShutdownCoordinator {

    private SecureShutdownCoordinator() {
    }

    /** Tears down the overlay, wake-word and accessibility services. Safe to call repeatedly. */
    public static void shutdownProtectedComponents(Context ctx) {
        Context app = ctx.getApplicationContext();

        // 1) Overlay window.
        try {
            app.stopService(new Intent(app, FloatingBubbleService.class));
        } catch (Throwable ignored) {
            // Stopping must never crash the app.
        }

        // 2) Wake-word microphone service.
        try {
            app.stopService(new Intent(app, WakeWordService.class));
        } catch (Throwable ignored) {
        }

        // 3) Accessibility service — self-disable if the user had it enabled (API 24+).
        try {
            AtomAccessibilityService a11y = AtomAccessibilityService.getInstance();
            if (a11y != null) {
                a11y.disableSelf();
            }
        } catch (Throwable ignored) {
        }
    }
}

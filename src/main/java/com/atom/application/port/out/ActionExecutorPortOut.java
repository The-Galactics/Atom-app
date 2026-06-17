package com.atom.application.port.out;

import com.atom.domain.action.ActionOutcome;
import com.atom.domain.action.ResolvedAction;

/**
 * Out-port for executing a resolved action on the host device.
 *
 * <p>The recognition (what the user wants) is a remote concern handled by the
 * gRPC backend; the execution (doing it) is a local Android concern. Keeping it
 * behind this port lets the application/orchestration layer stay free of
 * Android APIs and makes the dispatcher mockable in tests.
 */
public interface ActionExecutorPortOut {

    /**
     * Performs {@code action} on the device. Implementations must be safe to
     * call for {@link com.atom.domain.action.ActionType#NONE} (no-op success).
     */
    ActionOutcome execute(ResolvedAction action);
}

package com.atom.app.ui.main;

/** One-shot latch: the first of {mic-released signal, cap timeout} to fire wins;
 *  the loser is a no-op, so STT starts exactly once. */
public final class MicHandoffGate {

    private boolean fired = false;

    public synchronized boolean fire() {
        if (fired) {
            return false;
        }
        fired = true;
        return true;
    }
}

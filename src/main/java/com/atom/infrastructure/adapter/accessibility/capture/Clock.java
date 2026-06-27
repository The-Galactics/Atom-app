package com.atom.infrastructure.adapter.accessibility.capture;

/** Time + sleep seam so the capture loop is deterministic in tests. */
public interface Clock {
    long nowMs();
    void sleep(long ms) throws InterruptedException;
}

package com.atom.infrastructure.adapter.accessibility.capture;

/** Supplies attempt-scoped window sessions; implemented by the accessibility service. */
public interface WindowSource {
    boolean isConnected();
    long lastMutationAtMs();
    WindowSession openSession();
}

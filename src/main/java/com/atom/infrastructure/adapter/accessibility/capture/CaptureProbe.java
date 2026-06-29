package com.atom.infrastructure.adapter.accessibility.capture;

/** Cheap O(1) readiness reads for one attempt. Holds only primitives. */
public record CaptureProbe(boolean rootPresent, boolean rootRefreshed,
                           boolean activeWindowPresent, int topLevelChildCount,
                           int prevTopLevelChildCount, long msSinceLastMutation) {
}

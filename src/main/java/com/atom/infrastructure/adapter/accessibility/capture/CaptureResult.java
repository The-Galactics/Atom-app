package com.atom.infrastructure.adapter.accessibility.capture;

import com.atom.infrastructure.adapter.accessibility.AtomAccessibilityService.ScreenNode;
import java.util.List;

/** Capture outcome: a status plus the (possibly empty) cleaned node list. */
public record CaptureResult(CaptureStatus status, List<ScreenNode> nodes) {
    public static CaptureResult ready(List<ScreenNode> nodes) {
        return new CaptureResult(CaptureStatus.READY, nodes);
    }
    public static CaptureResult timeout() {
        return new CaptureResult(CaptureStatus.TIMEOUT, List.of());
    }
    public static CaptureResult unavailable() {
        return new CaptureResult(CaptureStatus.UNAVAILABLE, List.of());
    }
}

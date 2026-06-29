// src/main/java/com/atom/app/telemetry/LatencyTimer.java
package com.atom.app.telemetry;

/** Pure helpers for latency instrumentation; callers log the line under tag "AtomLatency". */
public final class LatencyTimer {

    private LatencyTimer() { }

    public static long toMillis(long startNanos, long endNanos) {
        return (endNanos - startNanos) / 1_000_000L;
    }

    public static String formatLine(String tag, long refreshMs, long rpcMs, long totalMs) {
        return "AtomLatency tag=" + tag
                + " refresh_ms=" + refreshMs
                + " rpc_ms=" + rpcMs
                + " total_ms=" + totalMs;
    }
}

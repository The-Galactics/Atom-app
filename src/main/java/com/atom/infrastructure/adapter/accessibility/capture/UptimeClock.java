package com.atom.infrastructure.adapter.accessibility.capture;

import android.os.SystemClock;

/** Production clock backed by the monotonic uptime clock. */
public final class UptimeClock implements Clock {
    @Override
    public long nowMs() {
        return SystemClock.uptimeMillis();
    }

    @Override
    public void sleep(long ms) throws InterruptedException {
        if (ms > 0) {
            Thread.sleep(ms);
        }
    }
}

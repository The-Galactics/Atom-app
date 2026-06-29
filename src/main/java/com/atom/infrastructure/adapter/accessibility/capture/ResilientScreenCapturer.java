package com.atom.infrastructure.adapter.accessibility.capture;

import com.atom.infrastructure.adapter.accessibility.oem.OemCompatibilityAdapter;
import com.atom.infrastructure.adapter.accessibility.oem.RetryPolicy;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Synchronous, attempt-scoped, exponential-backoff capture loop. Never throws. */
public final class ResilientScreenCapturer {

    private static final Logger LOG = Logger.getLogger(ResilientScreenCapturer.class.getName());

    private final WindowSource source;
    private final ScreenCleaningPipeline pipeline;
    private final Clock clock;

    public ResilientScreenCapturer(WindowSource source, ScreenCleaningPipeline pipeline, Clock clock) {
        this.source = source;
        this.pipeline = pipeline;
        this.clock = clock;
    }

    public CaptureResult capture(OemCompatibilityAdapter adapter) {
        RetryPolicy policy = adapter.retryPolicy();
        long start = clock.nowMs();
        int prevCount = -1;

        for (int attempt = 0; attempt < policy.maxAttempts(); attempt++) {
            if (clock.nowMs() - start >= policy.totalBudgetMs()) {
                break;
            }
            if (!source.isConnected()) {
                return CaptureResult.unavailable();
            }
            try (WindowSession session = source.openSession()) {
                WindowSnapshot target = adapter.selectActiveWindow(session.windows());
                if (target != null) {
                    long sinceMutation = clock.nowMs() - source.lastMutationAtMs();
                    CaptureProbe probe = session.probe(target, prevCount, sinceMutation);
                    prevCount = probe.topLevelChildCount();
                    if (adapter.isTreeReady(probe)) {
                        List<NodeSnapshot> raw = session.extract(target, adapter);
                        return CaptureResult.ready(pipeline.clean(raw, adapter));
                    }
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "capture attempt failed; retrying", e);
            }

            if (attempt + 1 >= policy.maxAttempts()) {
                break;
            }
            try {
                clock.sleep(policy.backoff(attempt));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return CaptureResult.timeout();
    }
}

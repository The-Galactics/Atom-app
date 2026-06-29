package com.atom.infrastructure.adapter.accessibility.capture;

import com.atom.infrastructure.adapter.accessibility.AtomAccessibilityService.ScreenNode;
import com.atom.infrastructure.adapter.accessibility.oem.DefaultOemAdapter;
import com.atom.infrastructure.adapter.accessibility.oem.OemCompatibilityAdapter;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ResilientScreenCapturerTest {

    /** Deterministic clock: sleep advances virtual time, never blocks. */
    private static final class FakeClock implements Clock {
        long now = 0;
        public long nowMs() { return now; }
        public void sleep(long ms) { now += ms; }
    }

    /** Scripted session: each attempt pops the next probe; extract returns fixed nodes. */
    private static final class FakeSession implements WindowSession {
        private final CaptureProbe probe;
        private final List<NodeSnapshot> nodes;
        boolean closed = false;
        FakeSession(CaptureProbe probe, List<NodeSnapshot> nodes) {
            this.probe = probe; this.nodes = nodes;
        }
        public List<WindowSnapshot> windows() {
            return List.of(new WindowSnapshot(1, WindowSnapshot.TYPE_APPLICATION, 1,
                    true, true, "app", 0, 0, 100, 200));
        }
        public CaptureProbe probe(WindowSnapshot t, int prev, long since) { return probe; }
        public List<NodeSnapshot> extract(WindowSnapshot t, OemCompatibilityAdapter a) {
            return nodes;
        }
        public void close() { closed = true; }
    }

    private static final class FakeSource implements WindowSource {
        private final Deque<FakeSession> sessions;
        private final boolean connected;
        int opened = 0;
        FakeSource(boolean connected, Deque<FakeSession> sessions) {
            this.connected = connected; this.sessions = sessions;
        }
        public boolean isConnected() { return connected; }
        public long lastMutationAtMs() { return 0; }
        public WindowSession openSession() { opened++; return sessions.pop(); }
    }

    private final OemCompatibilityAdapter adapter = new DefaultOemAdapter();
    private final ScreenCleaningPipeline pipeline = new ScreenCleaningPipeline();

    @Test
    void returnsReadyOnFirstSettledAttempt() {
        CaptureProbe ready = new CaptureProbe(true, true, true, 2, 0, 999L);
        List<NodeSnapshot> nodes = List.of(
                NodeSnapshot.builder().text("Hi").bounds(0, 0, 10, 10).build());
        Deque<FakeSession> q = new ArrayDeque<>();
        q.add(new FakeSession(ready, nodes));
        FakeSource source = new FakeSource(true, q);

        CaptureResult r = new ResilientScreenCapturer(source, pipeline, new FakeClock())
                .capture(adapter);

        assertThat(r.status()).isEqualTo(CaptureStatus.READY);
        assertThat(r.nodes()).extracting(n -> n.text).containsExactly("Hi");
        assertThat(source.opened).isEqualTo(1);
    }

    @Test
    void retriesThenSucceeds() {
        CaptureProbe notReady = new CaptureProbe(false, false, false, 0, 0, 0L);
        CaptureProbe ready = new CaptureProbe(true, true, true, 1, 0, 999L);
        Deque<FakeSession> q = new ArrayDeque<>();
        q.add(new FakeSession(notReady, List.of()));
        q.add(new FakeSession(ready,
                List.of(NodeSnapshot.builder().text("ok").bounds(0, 0, 5, 5).build())));
        FakeSource source = new FakeSource(true, q);

        CaptureResult r = new ResilientScreenCapturer(source, pipeline, new FakeClock())
                .capture(adapter);

        assertThat(r.status()).isEqualTo(CaptureStatus.READY);
        assertThat(source.opened).isEqualTo(2);
    }

    @Test
    void timesOutWhenNeverReady() {
        Deque<FakeSession> q = new ArrayDeque<>();
        for (int i = 0; i < 5; i++) {
            q.add(new FakeSession(new CaptureProbe(false, false, false, 0, 0, 0L), List.of()));
        }
        FakeSource source = new FakeSource(true, q);

        CaptureResult r = new ResilientScreenCapturer(source, pipeline, new FakeClock())
                .capture(adapter);

        assertThat(r.status()).isEqualTo(CaptureStatus.TIMEOUT);
        assertThat(r.nodes()).isEmpty();
    }

    @Test
    void reportsUnavailableWhenServiceDisconnected() {
        FakeSource source = new FakeSource(false, new ArrayDeque<>());
        CaptureResult r = new ResilientScreenCapturer(source, pipeline, new FakeClock())
                .capture(adapter);
        assertThat(r.status()).isEqualTo(CaptureStatus.UNAVAILABLE);
    }

    @Test
    void closesEverySessionItOpens() {
        FakeSession s = new FakeSession(new CaptureProbe(true, true, true, 1, 0, 999L),
                List.of(NodeSnapshot.builder().text("x").bounds(0, 0, 4, 4).build()));
        Deque<FakeSession> q = new ArrayDeque<>();
        q.add(s);
        new ResilientScreenCapturer(new FakeSource(true, q), pipeline, new FakeClock())
                .capture(adapter);
        assertThat(s.closed).isTrue();
    }
}

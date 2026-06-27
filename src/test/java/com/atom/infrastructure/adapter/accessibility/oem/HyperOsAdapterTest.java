package com.atom.infrastructure.adapter.accessibility.oem;

import com.atom.infrastructure.adapter.accessibility.capture.CaptureProbe;
import com.atom.infrastructure.adapter.accessibility.capture.NodeSnapshot;
import com.atom.infrastructure.adapter.accessibility.capture.WindowSnapshot;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class HyperOsAdapterTest {
    private final HyperOsAdapter adapter = new HyperOsAdapter();

    @Test
    void notReadyWhileTreeStillMutating() {
        // root present & refreshed, children present, but a mutation just happened
        CaptureProbe p = new CaptureProbe(true, true, true, 5, 3, 10L);
        assertThat(adapter.isTreeReady(p)).isFalse();
    }

    @Test
    void readyAfterQuietWindowElapses() {
        CaptureProbe p = new CaptureProbe(true, true, true, 5, 3, 200L);
        assertThat(adapter.isTreeReady(p)).isTrue();
    }

    @Test
    void readyWhenChildCountStableEvenIfRecentMutation() {
        CaptureProbe p = new CaptureProbe(true, true, true, 5, 5, 10L);
        assertThat(adapter.isTreeReady(p)).isTrue();
    }

    @Test
    void notReadyWhenRootNotRefreshed() {
        CaptureProbe p = new CaptureProbe(true, false, true, 5, 5, 500L);
        assertThat(adapter.isTreeReady(p)).isFalse();
    }
}

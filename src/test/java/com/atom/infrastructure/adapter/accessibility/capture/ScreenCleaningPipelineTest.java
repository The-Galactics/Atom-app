package com.atom.infrastructure.adapter.accessibility.capture;

import com.atom.infrastructure.adapter.accessibility.AtomAccessibilityService.ScreenNode;
import com.atom.infrastructure.adapter.accessibility.oem.OemCompatibilityAdapter;
import com.atom.infrastructure.adapter.accessibility.oem.RetryPolicy;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ScreenCleaningPipelineTest {
    private final ScreenCleaningPipeline pipeline = new ScreenCleaningPipeline();

    /** Adapter that flags zero-area nodes as phantom and ids by text. */
    private static final class TestAdapter implements OemCompatibilityAdapter {
        public RetryPolicy retryPolicy() { return RetryPolicy.lenient(); }
        public boolean isTreeReady(CaptureProbe p) { return true; }
        public WindowSnapshot selectActiveWindow(List<WindowSnapshot> w) { return null; }
        public boolean isPhantom(NodeSnapshot n) { return n.area() == 0; }
        public long synthesizeStableId(NodeSnapshot n) { return n.text().hashCode(); }
    }

    @Test
    void dropsPhantomsAndReindexesInOrder() {
        List<NodeSnapshot> raw = List.of(
                NodeSnapshot.builder().text("A").role("Button").clickable(true)
                        .bounds(0, 0, 10, 10).build(),
                NodeSnapshot.builder().text("ghost").bounds(5, 5, 5, 5).build(), // zero area
                NodeSnapshot.builder().text("B").role("TextView")
                        .bounds(0, 20, 10, 30).build());
        List<ScreenNode> out = pipeline.clean(raw, new TestAdapter());
        assertThat(out).hasSize(2);
        assertThat(out.get(0).text).isEqualTo("A");
        assertThat(out.get(0).index).isEqualTo(0);
        assertThat(out.get(1).text).isEqualTo("B");
        assertThat(out.get(1).index).isEqualTo(1);
    }

    @Test
    void dedupsNodesSharingAStableId() {
        List<NodeSnapshot> raw = List.of(
                NodeSnapshot.builder().text("dup").bounds(0, 0, 10, 10).build(),
                NodeSnapshot.builder().text("dup").bounds(0, 0, 10, 10).build());
        List<ScreenNode> out = pipeline.clean(raw, new TestAdapter());
        assertThat(out).hasSize(1);
    }
}

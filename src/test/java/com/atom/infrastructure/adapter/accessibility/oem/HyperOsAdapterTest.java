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

    @Test
    void skipsMiuiOverlayAndPicksAppWindow() {
        WindowSnapshot overlay = new WindowSnapshot(1, WindowSnapshot.TYPE_APPLICATION, 9,
                true, true, "com.android.systemui", 0, 0, 100, 100);
        WindowSnapshot app = new WindowSnapshot(2, WindowSnapshot.TYPE_APPLICATION, 1,
                true, true, "com.whatsapp", 0, 0, 100, 300);
        assertThat(adapter.selectActiveWindow(List.of(overlay, app)).windowId()).isEqualTo(2);
    }

    @Test
    void prefersLargestActiveAppWindowInSplitScreen() {
        WindowSnapshot small = new WindowSnapshot(1, WindowSnapshot.TYPE_APPLICATION, 1,
                true, true, "com.app.a", 0, 0, 100, 100);   // area 10_000
        WindowSnapshot large = new WindowSnapshot(2, WindowSnapshot.TYPE_APPLICATION, 1,
                true, true, "com.app.b", 0, 0, 100, 300);   // area 30_000
        assertThat(adapter.selectActiveWindow(List.of(small, large)).windowId()).isEqualTo(2);
    }

    @Test
    void zeroAreaNodeIsPhantom() {
        NodeSnapshot n = NodeSnapshot.builder().text("x").bounds(10, 10, 10, 10)
                .visibleToUser(true).build();
        assertThat(adapter.isPhantom(n)).isTrue();
    }

    @Test
    void invisibleNodeWithoutSignalIsPhantom() {
        NodeSnapshot n = NodeSnapshot.builder().bounds(0, 0, 50, 50)
                .visibleToUser(false).build(); // no text, not actionable
        assertThat(adapter.isPhantom(n)).isTrue();
    }

    @Test
    void overlayPackageNodeIsPhantom() {
        NodeSnapshot n = NodeSnapshot.builder().text("ad").bounds(0, 0, 50, 50)
                .visibleToUser(true).packageName("com.miui.contentcatcher").build();
        assertThat(adapter.isPhantom(n)).isTrue();
    }

    @Test
    void realVisibleButtonIsNotPhantom() {
        NodeSnapshot n = NodeSnapshot.builder().text("Send").role("Button").clickable(true)
                .bounds(0, 0, 80, 40).visibleToUser(true).packageName("com.whatsapp").build();
        assertThat(adapter.isPhantom(n)).isFalse();
    }

    @Test
    void stableIdSurvivesStrippedViewId() {
        NodeSnapshot withId = NodeSnapshot.builder().viewId("com.app:id/send")
                .role("Button").text("Send").bounds(0, 0, 80, 40).siblingIndex(2).build();
        NodeSnapshot stripped = NodeSnapshot.builder().viewId(null)
                .role("Button").text("Send").bounds(0, 0, 80, 40).siblingIndex(2).build();
        // stripped id is stable across re-captures of the same stripped node
        NodeSnapshot strippedAgain = NodeSnapshot.builder().viewId("")
                .role("Button").text("Send").bounds(0, 0, 80, 40).siblingIndex(2).build();
        assertThat(adapter.synthesizeStableId(stripped))
                .isEqualTo(adapter.synthesizeStableId(strippedAgain));
        // and a node that DID keep its id is identified differently
        assertThat(adapter.synthesizeStableId(withId))
                .isNotEqualTo(adapter.synthesizeStableId(stripped));
    }

    @Test
    void differentSiblingIndexYieldsDifferentStrippedId() {
        NodeSnapshot a = NodeSnapshot.builder().role("Button").text("OK")
                .bounds(0, 0, 10, 10).siblingIndex(0).build();
        NodeSnapshot b = NodeSnapshot.builder().role("Button").text("OK")
                .bounds(0, 0, 10, 10).siblingIndex(1).build();
        assertThat(adapter.synthesizeStableId(a)).isNotEqualTo(adapter.synthesizeStableId(b));
    }
}

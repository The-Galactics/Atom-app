package com.atom.infrastructure.adapter.accessibility.oem;

import com.atom.infrastructure.adapter.accessibility.capture.CaptureProbe;
import com.atom.infrastructure.adapter.accessibility.capture.NodeSnapshot;
import com.atom.infrastructure.adapter.accessibility.capture.WindowSnapshot;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultOemAdapterTest {
    private final DefaultOemAdapter adapter = new DefaultOemAdapter();

    @Test
    void readyWhenRootPresentWithChildren() {
        CaptureProbe p = new CaptureProbe(true, true, true, 3, 0, 0L);
        assertThat(adapter.isTreeReady(p)).isTrue();
    }

    @Test
    void notReadyWhenRootAbsent() {
        CaptureProbe p = new CaptureProbe(false, false, false, 0, 0, 0L);
        assertThat(adapter.isTreeReady(p)).isFalse();
    }

    @Test
    void picksApplicationWindowOverInputMethod() {
        WindowSnapshot ime = new WindowSnapshot(1, WindowSnapshot.TYPE_INPUT_METHOD, 5,
                false, false, "ime", 0, 0, 100, 100);
        WindowSnapshot app = new WindowSnapshot(2, WindowSnapshot.TYPE_APPLICATION, 1,
                true, true, "app", 0, 0, 100, 200);
        assertThat(adapter.selectActiveWindow(List.of(ime, app)).windowId()).isEqualTo(2);
    }

    @Test
    void returnsNullWhenNoApplicationWindow() {
        WindowSnapshot ime = new WindowSnapshot(1, WindowSnapshot.TYPE_INPUT_METHOD, 5,
                false, false, "ime", 0, 0, 100, 100);
        assertThat(adapter.selectActiveWindow(List.of(ime))).isNull();
    }

    @Test
    void neverTreatsNodesAsPhantom() {
        NodeSnapshot n = NodeSnapshot.builder().bounds(0, 0, 0, 0).build();
        assertThat(adapter.isPhantom(n)).isFalse();
    }

    @Test
    void stableIdIsDeterministicForSameNode() {
        NodeSnapshot a = NodeSnapshot.builder().role("Button").text("OK")
                .bounds(0, 0, 10, 10).siblingIndex(1).build();
        NodeSnapshot b = NodeSnapshot.builder().role("Button").text("OK")
                .bounds(0, 0, 10, 10).siblingIndex(1).build();
        assertThat(adapter.synthesizeStableId(a)).isEqualTo(adapter.synthesizeStableId(b));
    }

    @Test
    void stableIdDistinguishesSiblingsSharingAViewId() {
        NodeSnapshot row1 = NodeSnapshot.builder().viewId("com.app:id/title")
                .role("TextView").text("A").bounds(0, 0, 100, 40).siblingIndex(0).build();
        NodeSnapshot row2 = NodeSnapshot.builder().viewId("com.app:id/title")
                .role("TextView").text("B").bounds(0, 40, 100, 80).siblingIndex(1).build();
        assertThat(adapter.synthesizeStableId(row1)).isNotEqualTo(adapter.synthesizeStableId(row2));
    }
}

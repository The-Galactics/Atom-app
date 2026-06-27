package com.atom.infrastructure.adapter.accessibility.capture;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NodeSnapshotTest {
    @Test
    void builderPopulatesFieldsAndComputesArea() {
        NodeSnapshot n = NodeSnapshot.builder()
                .text("OK").role("Button")
                .clickable(true).visibleToUser(true)
                .bounds(0, 0, 100, 40).siblingIndex(2).depth(3)
                .build();
        assertThat(n.text()).isEqualTo("OK");
        assertThat(n.clickable()).isTrue();
        assertThat(n.area()).isEqualTo(4000);
        assertThat(n.siblingIndex()).isEqualTo(2);
    }

    @Test
    void zeroBoundsHaveZeroArea() {
        NodeSnapshot n = NodeSnapshot.builder().bounds(10, 10, 10, 10).build();
        assertThat(n.area()).isEqualTo(0);
    }

    @Test
    void invertedBoundsHaveZeroArea() {
        NodeSnapshot n = NodeSnapshot.builder().bounds(50, 0, 10, 40).build();
        assertThat(n.area()).isEqualTo(0);
    }
}

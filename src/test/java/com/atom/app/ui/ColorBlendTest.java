package com.atom.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ColorBlendTest {

    @Test
    void tZeroReturnsFirstColor() {
        assertThat(ColorBlend.lerp(0xFF112233, 0xFFAABBCC, 0f)).isEqualTo(0xFF112233);
    }

    @Test
    void tOneReturnsSecondColor() {
        assertThat(ColorBlend.lerp(0xFF112233, 0xFFAABBCC, 1f)).isEqualTo(0xFFAABBCC);
    }

    @Test
    void halfwayBlendsEachChannel() {
        // 0x00 -> 0xFF at t=0.5 is 0x7F (127); alpha stays 0xFF.
        assertThat(ColorBlend.lerp(0xFF000000, 0xFFFFFFFF, 0.5f)).isEqualTo(0xFF7F7F7F);
    }

    @Test
    void clampsTBelowZeroAndAboveOne() {
        assertThat(ColorBlend.lerp(0xFF112233, 0xFFAABBCC, -1f)).isEqualTo(0xFF112233);
        assertThat(ColorBlend.lerp(0xFF112233, 0xFFAABBCC, 2f)).isEqualTo(0xFFAABBCC);
    }
}

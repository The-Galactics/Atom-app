package com.atom.app.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OperatingNotificationTextTest {

    @Test
    void stepOnlyWhenNoLabel() {
        assertThat(OperatingNotificationText.compose("Step %d", 2, null)).isEqualTo("Step 2");
        assertThat(OperatingNotificationText.compose("Step %d", 3, "  ")).isEqualTo("Step 3");
    }

    @Test
    void appendsLabelWhenPresent() {
        assertThat(OperatingNotificationText.compose("Step %d", 1, "Tapping"))
                .isEqualTo("Step 1 · Tapping");
    }

    @Test
    void trimsLabel() {
        assertThat(OperatingNotificationText.compose("Step %d", 4, "  Scrolling  "))
                .isEqualTo("Step 4 · Scrolling");
    }
}

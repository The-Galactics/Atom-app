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

    @Test
    void completionBodyIsEmptyWhenNoMessage() {
        assertThat(OperatingNotificationText.completionBody(null, 40)).isEmpty();
        assertThat(OperatingNotificationText.completionBody("   ", 40)).isEmpty();
    }

    @Test
    void completionBodyReturnsTrimmedMessageWhenShort() {
        assertThat(OperatingNotificationText.completionBody("  La calculadora ya está abierta ", 40))
                .isEqualTo("La calculadora ya está abierta");
    }

    @Test
    void completionBodyTruncatesLongMessageWithEllipsis() {
        String body = OperatingNotificationText.completionBody(
                "Listo: he abierto la calculadora y configurado el temporizador", 20);
        assertThat(body).hasSize(20);
        assertThat(body).endsWith("…");
        assertThat(body).startsWith("Listo: he abierto");
    }
}

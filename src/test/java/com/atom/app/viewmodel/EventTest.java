package com.atom.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventTest {

    @Test
    @DisplayName("getContentIfNotHandled returns the content on the first call")
    void returnsContentOnFirstCall() {
        Event<String> event = new Event<>("payload");

        assertThat(event.getContentIfNotHandled()).isEqualTo("payload");
    }

    @Test
    @DisplayName("getContentIfNotHandled returns null on every call after the first")
    void returnsNullAfterFirstCall() {
        Event<String> event = new Event<>("payload");

        assertThat(event.getContentIfNotHandled()).isEqualTo("payload");
        assertThat(event.getContentIfNotHandled()).isNull();
        assertThat(event.getContentIfNotHandled()).isNull();
    }

    @Test
    @DisplayName("peekContent returns the content without marking the event handled")
    void peekDoesNotConsume() {
        Event<String> event = new Event<>("payload");

        assertThat(event.peekContent()).isEqualTo("payload");
        assertThat(event.peekContent()).isEqualTo("payload");
        // Still consumable after peeking.
        assertThat(event.getContentIfNotHandled()).isEqualTo("payload");
        assertThat(event.getContentIfNotHandled()).isNull();
    }
}

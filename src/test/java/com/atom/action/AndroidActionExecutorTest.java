package com.atom.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.atom.domain.action.ActionOutcome;
import com.atom.domain.action.ActionType;
import com.atom.domain.action.ResolvedAction;
import com.atom.infrastructure.adapter.action.AndroidActionExecutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

/**
 * Unit tests for {@link AndroidActionExecutor} routing of the accessibility
 * actions. The {@link com.atom.infrastructure.adapter.accessibility.AtomAccessibilityService}
 * singleton is null in a plain JVM (no service bound), so these verify the
 * graceful "enable accessibility" failure path without an Android runtime.
 */
class AndroidActionExecutorTest {

    private AndroidActionExecutor executor;

    @BeforeEach
    void setUp() {
        Context context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        // The graceful path only formats strings; return a stable marker.
        lenient().when(context.getString(anyInt())).thenReturn("disabled");
        lenient().when(context.getString(anyInt(), any())).thenReturn("disabled");
        executor = new AndroidActionExecutor(context);
    }

    private static ResolvedAction action(ActionType type, Map<String, String> params) {
        return new ResolvedAction(type, params, "", 1.0f, false);
    }

    @Test
    @DisplayName("Accessibility actions fail gracefully when the service is disabled")
    void failsWhenServiceDisabled() {
        ActionOutcome navigate = executor.execute(action(ActionType.NAVIGATE, Map.of("direction", "back")));
        ActionOutcome scroll = executor.execute(action(ActionType.SCROLL, Map.of("direction", "down")));
        ActionOutcome read = executor.execute(action(ActionType.READ_SCREEN, Collections.emptyMap()));
        ActionOutcome tap = executor.execute(action(ActionType.TAP_ELEMENT, Map.of("text", "OK")));
        ActionOutcome type = executor.execute(
                action(ActionType.TYPE_TEXT, Map.of("text", "hola", "submit", "true")));

        assertThat(navigate.success()).isFalse();
        assertThat(scroll.success()).isFalse();
        assertThat(read.success()).isFalse();
        assertThat(tap.success()).isFalse();
        assertThat(type.success()).isFalse();
    }

    @Test
    @DisplayName("TYPE_TEXT routes to the accessibility service; disabled -> graceful failure")
    void typeTextDispatchesAndFailsGracefullyWhenDisabled() {
        // Service singleton is null in plain JVM, so the dispatch reaches the
        // null-service guard and returns the disabled failure (not a crash).
        ActionOutcome withSubmit = executor.execute(
                action(ActionType.TYPE_TEXT, Map.of("text", "rubius pokemon", "submit", "false")));
        // Default submit: param absent -> dispatch still resolves the case branch.
        ActionOutcome defaultSubmit = executor.execute(
                action(ActionType.TYPE_TEXT, Map.of("text", "rubius pokemon")));

        assertThat(withSubmit.success()).isFalse();
        assertThat(defaultSubmit.success()).isFalse();
    }

    @Test
    @DisplayName("TYPE_TEXT with blank text fails before reaching the service")
    void typeTextBlankTextFails() {
        ActionOutcome blank = executor.execute(
                action(ActionType.TYPE_TEXT, Map.of("text", "  ")));
        assertThat(blank.success()).isFalse();
    }

    @Test
    @DisplayName("Non-executable action returns a no-op success")
    void noopForNone() {
        ActionOutcome outcome = executor.execute(action(ActionType.NONE, Collections.emptyMap()));
        assertThat(outcome.success()).isTrue();
    }

}

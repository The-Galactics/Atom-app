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

        assertThat(navigate.success()).isFalse();
        assertThat(scroll.success()).isFalse();
        assertThat(read.success()).isFalse();
        assertThat(tap.success()).isFalse();
    }

    @Test
    @DisplayName("Non-executable action returns a no-op success")
    void noopForNone() {
        ActionOutcome outcome = executor.execute(action(ActionType.NONE, Collections.emptyMap()));
        assertThat(outcome.success()).isTrue();
    }
}

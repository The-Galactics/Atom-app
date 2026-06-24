package com.atom.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;

import com.atom.app.R;
import com.atom.application.port.out.ContactResolverPortOut;
import com.atom.domain.action.ActionOutcome;
import com.atom.domain.action.ActionType;
import com.atom.domain.action.ResolvedAction;
import com.atom.infrastructure.adapter.action.AndroidActionExecutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Unit tests for {@link AndroidActionExecutor} routing of the accessibility
 * actions. The {@link com.atom.infrastructure.adapter.accessibility.AtomAccessibilityService}
 * singleton is null in a plain JVM (no service bound), so these verify the
 * graceful "enable accessibility" failure path without an Android runtime.
 */
class AndroidActionExecutorTest {

    private Context context;
    private AndroidActionExecutor executor;

    @BeforeEach
    void setUp() {
        context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        // The graceful path only formats strings; return a stable marker.
        lenient().when(context.getString(anyInt())).thenReturn("disabled");
        lenient().when(context.getString(anyInt(), any())).thenReturn("disabled");
        executor = new AndroidActionExecutor(context, mock(ContactResolverPortOut.class));
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

    @Test
    void makeCall_resolvesContactName_whenTargetIsNotNumeric() {
        ContactResolverPortOut resolver = mock(ContactResolverPortOut.class);
        when(resolver.resolveNumber("Mom")).thenReturn(Optional.of("+15551234567"));
        when(context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        AndroidActionExecutor executor = new AndroidActionExecutor(context, resolver);

        ResolvedAction action = new ResolvedAction(
                ActionType.MAKE_CALL, Map.of("target", "Mom"), "", 1.0f, false);
        ActionOutcome outcome = executor.execute(action);

        verify(resolver).resolveNumber("Mom");
        assertThat(outcome.success()).isTrue();
    }

    @Test
    void makeCall_returnsContactNotFound_whenResolutionMisses() {
        ContactResolverPortOut resolver = mock(ContactResolverPortOut.class);
        when(resolver.resolveNumber("Nobody")).thenReturn(Optional.empty());
        when(context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(context.getString(R.string.contact_not_found)).thenReturn("not found");
        AndroidActionExecutor executor = new AndroidActionExecutor(context, resolver);

        ResolvedAction action = new ResolvedAction(
                ActionType.MAKE_CALL, Map.of("target", "Nobody"), "", 1.0f, false);
        ActionOutcome outcome = executor.execute(action);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).isEqualTo("not found");
    }

    @Test
    void makeCall_bypassesResolver_whenTargetIsNumeric() {
        ContactResolverPortOut resolver = mock(ContactResolverPortOut.class);
        AndroidActionExecutor executor = new AndroidActionExecutor(context, resolver);

        ResolvedAction action = new ResolvedAction(
                ActionType.MAKE_CALL, Map.of("target", "+1 555 123 4567"), "", 1.0f, false);
        executor.execute(action);

        verifyNoInteractions(resolver);
    }

    @Test
    void sendMessage_withWhatsappApp_routesThroughContactResolver_notSms() {
        ContactResolverPortOut resolver = mock(ContactResolverPortOut.class);
        when(resolver.resolveNumber("Mom")).thenReturn(Optional.of("+15551234567"));
        AndroidActionExecutor executor = new AndroidActionExecutor(context, resolver);

        ResolvedAction action = new ResolvedAction(
                ActionType.SEND_MESSAGE,
                Map.of("recipient", "Mom", "body", "hola", "app", "whatsapp"),
                "", 1.0f, false);
        ActionOutcome outcome = executor.execute(action);

        // The SMS branch never touches the resolver; reaching it proves WhatsApp routing.
        verify(resolver).resolveNumber("Mom");
        assertThat(outcome.success()).isTrue();
    }

    @Test
    void sendMessage_withSmsApp_doesNotUseResolver() {
        ContactResolverPortOut resolver = mock(ContactResolverPortOut.class);
        AndroidActionExecutor executor = new AndroidActionExecutor(context, resolver);

        ResolvedAction action = new ResolvedAction(
                ActionType.SEND_MESSAGE,
                Map.of("recipient", "+15551234567", "body", "hi", "app", "sms"),
                "", 1.0f, false);
        executor.execute(action);

        verifyNoInteractions(resolver);
    }

    @Test
    void sendMessage_withWhatsappApp_andDialableRecipient_skipsResolverLookup() {
        ContactResolverPortOut resolver = mock(ContactResolverPortOut.class);
        AndroidActionExecutor executor = new AndroidActionExecutor(context, resolver);

        ResolvedAction action = new ResolvedAction(
                ActionType.SEND_MESSAGE,
                Map.of("recipient", "+1 555 123 4567", "body", "hi", "app", "whatsapp"),
                "", 1.0f, false);
        ActionOutcome outcome = executor.execute(action);

        verifyNoInteractions(resolver);
        assertThat(outcome.success()).isTrue();
    }

    // --- B2: Telegram opens the share flow without faking number targeting ---

    @Test
    @DisplayName("Telegram with a plain name opens the share flow and succeeds (no number resolution)")
    void sendTelegram_plainName_opensShareFlowAndSucceeds() {
        ContactResolverPortOut resolver = mock(ContactResolverPortOut.class);
        AndroidActionExecutor executor = new AndroidActionExecutor(context, resolver);

        ResolvedAction action = new ResolvedAction(
                ActionType.SEND_MESSAGE,
                Map.of("recipient", "Susana", "body", "hola", "app", "telegram"),
                "", 1.0f, false);
        ActionOutcome outcome = executor.execute(action);

        // The Telegram path carries the body via the share flow; it never tries
        // to resolve a number to fake chat targeting.
        verifyNoInteractions(resolver);
        assertThat(outcome.success()).isTrue();
    }

    @Test
    @DisplayName("Telegram with a null body is null-safe (no NPE) and still succeeds")
    void sendTelegram_nullBody_isNullSafe() {
        ContactResolverPortOut resolver = mock(ContactResolverPortOut.class);
        AndroidActionExecutor executor = new AndroidActionExecutor(context, resolver);

        // No "body" key -> ResolvedAction.param returns null; the encode must not NPE.
        ResolvedAction action = new ResolvedAction(
                ActionType.SEND_MESSAGE,
                Map.of("recipient", "Susana", "app", "telegram"),
                "", 1.0f, false);
        ActionOutcome outcome = executor.execute(action);

        assertThat(outcome.success()).isTrue();
    }

    // --- B4: explicit numeric validation for alarms and timers ---------------

    @Test
    @DisplayName("setAlarm with a non-numeric time fails with the invalid-time message")
    void setAlarm_nonNumericTime_failsWithInvalidTimeMessage() {
        when(context.getString(R.string.action_alarm_invalid_time)).thenReturn("bad time");

        ActionOutcome outcome = executor.execute(action(
                ActionType.SET_ALARM, Map.of("time", "noon")));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).isEqualTo("bad time");
    }

    @Test
    @DisplayName("setAlarm with a valid HH:MM time succeeds")
    void setAlarm_validTime_succeeds() {
        ActionOutcome outcome = executor.execute(action(
                ActionType.SET_ALARM, Map.of("time", "07:30")));

        assertThat(outcome.success()).isTrue();
    }

    @Test
    @DisplayName("setTimer with a non-numeric duration fails with the invalid-duration message")
    void setTimer_nonNumericDuration_failsWithInvalidDurationMessage() {
        when(context.getString(R.string.action_timer_invalid_duration)).thenReturn("bad duration");

        ActionOutcome outcome = executor.execute(action(
                ActionType.SET_TIMER, Map.of("duration_seconds", "five")));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.message()).isEqualTo("bad duration");
    }

    @Test
    @DisplayName("setTimer with a valid numeric duration succeeds")
    void setTimer_validDuration_succeeds() {
        ActionOutcome outcome = executor.execute(action(
                ActionType.SET_TIMER, Map.of("duration_seconds", "300")));

        assertThat(outcome.success()).isTrue();
    }

}

package com.atom.app.ui.main;

import android.content.Intent;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.atom.app.R;
import com.atom.app.overlay.FloatingBubbleService;
import com.atom.app.permission.PermissionCoordinator;
import com.atom.app.settings.AtomPreferences;
import com.atom.app.viewmodel.ChatViewModel;
import com.atom.application.port.out.security.SessionListener;
import com.atom.domain.action.ResolvedAction;

/**
 * Wires the ViewModel's LiveData to the Activity's UI via a Host interface.
 *
 * <p>Sensitive/destructive actions are no longer pre-confirmed by a tap dialog: the
 * backend holds them and asks out loud mid-loop (the voice gate). This binder forwards
 * that spoken-confirmation request to the Host, which speaks the question and re-opens
 * the mic ({@link Host#onVoiceConfirmationRequested}).
 *
 * <p>Constructing this object does not start observation — call {@link #bind()} once
 * the Activity's views and ViewModel are ready (i.e. after {@code setContentView}).
 */
public final class MainViewModelBinder {

    /** Callbacks the Activity must implement to render ViewModel state changes. */
    public interface Host {
        /** Render a successful assistant reply (fadeSwap, core RESPONDED, optional TTS). */
        void showResponse(String response);
        /** Render the "thinking" in-progress state (fadeSwap × 2, core THINKING). */
        void showThinking();
        /** Render an error state and schedule auto-recovery. */
        void showError(String error);
        /** Enable or disable the text input bar (used while automation is running). */
        void setInputEnabled(boolean enabled);
        /** Render the automation-operating state (fadeSwap × 2, core OPERATING). */
        void showOperating();
        /** Held/destructive action mid-loop: speak the question and capture the spoken reply. */
        void onVoiceConfirmationRequested(String question);
    }

    private final AppCompatActivity activity;
    private final ChatViewModel viewModel;
    private final Host host;

    public MainViewModelBinder(AppCompatActivity activity,
                               ChatViewModel viewModel,
                               Host host) {
        this.activity = activity;
        this.viewModel = viewModel;
        this.host = host;
    }

    /**
     * Observe all ViewModel LiveData on the activity's lifecycle. Call once from
     * {@code onCreate}, after the ViewModel and views are ready.
     */
    public void bind() {
        // When the back-end responds
        viewModel.getChatResponse().observe(activity, response -> host.showResponse(response));

        // When waiting for the back-end
        viewModel.getIsLoading().observe(activity, isLoading -> {
            if (isLoading) {
                host.showThinking();
            }
        });

        // When something goes wrong
        viewModel.getErrorMessage().observe(activity, error -> host.showError(error));

        // Held/destructive action mid-loop: speak the question and capture the spoken
        // "sí/no" hands-free (the loop thread waits for the transcript). One-shot Event
        // avoids re-asking on recreation.
        viewModel.getVoiceConfirmationRequested().observe(activity, e -> {
            String question = e.getContentIfNotHandled();
            if (question != null) {
                host.onVoiceConfirmationRequested(question);
            }
        });

        // Accessibility-powered actions need the service enabled first.
        viewModel.getAccessibilityRequired().observe(activity,
                e -> promptEnableAccessibility(e.getContentIfNotHandled()));

        // Session expired (a recognize/RPC call returned UNAUTHENTICATED): hand off to
        // the app-wide redirect (AtomApp implements SessionListener) so the user lands
        // on Login with the back stack cleared — the same path the auth layer uses on a
        // rejected refresh. One-shot Event avoids re-firing on recreation.
        viewModel.getSessionExpired().observe(activity, e -> {
            if (Boolean.TRUE.equals(e.getContentIfNotHandled())
                    && activity.getApplication() instanceof SessionListener) {
                ((SessionListener) activity.getApplication()).onSessionExpired();
            }
        });

        // While the loop runs, freeze input and show the operating indicator.
        viewModel.getAutomationActive().observe(activity, active -> {
            boolean operating = Boolean.TRUE.equals(active);
            host.setInputEnabled(!operating);
            if (operating) {
                host.showOperating();
                ensureOperatingOverlay();
            }
        });
    }

    // When an order starts from the app, make sure the overlay service is alive so the
    // cross-app operating cue (pulsing edge handle + live notification) can appear once
    // Atom navigates away. Gated strictly on already-granted overlay permission and the
    // user's bubble preference — no new permissions, and nothing happens if the user
    // never enabled the bubble.
    private void ensureOperatingOverlay() {
        if (!PermissionCoordinator.canDrawOverlays(activity)
                || !new AtomPreferences(activity).isBubbleEnabled()) {
            return;
        }
        try {
            activity.startForegroundService(new Intent(activity, FloatingBubbleService.class)
                    .setAction(FloatingBubbleService.ACTION_PREPARE_OPERATING));
        } catch (Exception ignored) {
            // Background-start restrictions etc.; the cue is best-effort.
        }
    }

    /** Prompts the user to enable Atom's accessibility service, then opens Settings. */
    private void promptEnableAccessibility(ResolvedAction action) {
        if (action == null) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.accessibility_prompt_title)
                .setMessage(R.string.accessibility_prompt_message)
                .setPositiveButton(R.string.accessibility_prompt_open,
                        (d, w) -> activity.startActivity(
                                PermissionCoordinator.accessibilitySettingsIntent()))
                .setNegativeButton(R.string.action_confirm_no, null)
                .show();
    }
}

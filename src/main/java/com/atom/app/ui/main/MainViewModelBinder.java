package com.atom.app.ui.main;

import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.atom.app.R;
import com.atom.app.permission.PermissionCoordinator;
import com.atom.app.viewmodel.ChatViewModel;
import com.atom.domain.action.ResolvedAction;

/**
 * Wires the ViewModel's LiveData to the Activity's UI via a Host interface, and owns
 * the confirmation/permission dialogs that used to live in MainActivity.
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
    }

    private final AppCompatActivity activity;
    private final ChatViewModel viewModel;
    private final ActivityResultLauncher<String> permissionLauncher;
    private final Host host;

    // Action awaiting a permission grant; resumed in onPermissionResult.
    private ResolvedAction awaitingPermission;

    public MainViewModelBinder(AppCompatActivity activity,
                               ChatViewModel viewModel,
                               ActivityResultLauncher<String> permissionLauncher,
                               Host host) {
        this.activity = activity;
        this.viewModel = viewModel;
        this.permissionLauncher = permissionLauncher;
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

        // Sensitive actions require confirmation; one-shot Event avoids re-prompting on recreation.
        viewModel.getPendingConfirmation().observe(activity,
                e -> confirmAction(e.getContentIfNotHandled()));

        // Destructive action mid-loop: prompt before it runs (loop thread waits for the answer).
        viewModel.getDestructiveConfirmation().observe(activity,
                e -> confirmDestructive(e.getContentIfNotHandled()));

        // Accessibility-powered actions need the service enabled first.
        viewModel.getAccessibilityRequired().observe(activity,
                e -> promptEnableAccessibility(e.getContentIfNotHandled()));

        // While the loop runs, freeze input and show the operating indicator.
        viewModel.getAutomationActive().observe(activity, active -> {
            boolean operating = Boolean.TRUE.equals(active);
            host.setInputEnabled(!operating);
            if (operating) {
                host.showOperating();
            }
        });
    }

    /**
     * Called by MainActivity's {@code permissionLauncher} callback with the grant result.
     * Resumes the awaiting action if granted, or shows a denied toast if not.
     */
    public void onPermissionResult(boolean granted) {
        ResolvedAction action = awaitingPermission;
        awaitingPermission = null;
        if (action == null) {
            return;
        }
        if (granted) {
            viewModel.runAction(action);
        } else {
            Toast.makeText(activity,
                    activity.getString(R.string.action_permission_denied),
                    Toast.LENGTH_SHORT).show();
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

    /** Asks the user to confirm a sensitive action before executing it. */
    private void confirmAction(ResolvedAction action) {
        if (action == null) {
            return;
        }
        String prompt = action.outMessage().isEmpty()
                ? activity.getString(R.string.action_confirm_default)
                : action.outMessage();
        new AlertDialog.Builder(activity)
                .setTitle(R.string.action_confirm_title)
                .setMessage(prompt)
                .setPositiveButton(R.string.action_confirm_yes, (d, w) -> executeWithPermission(action))
                .setNegativeButton(R.string.action_confirm_no, null)
                .show();
    }

    /** Confirms a destructive action mid-loop; the paused loop resumes or aborts on the answer. */
    private void confirmDestructive(ResolvedAction action) {
        if (action == null) {
            return;
        }
        String prompt = action.outMessage().isEmpty()
                ? activity.getString(R.string.action_confirm_default)
                : action.outMessage();
        new AlertDialog.Builder(activity)
                .setTitle(R.string.action_confirm_title)
                .setMessage(prompt)
                .setCancelable(false)
                .setPositiveButton(R.string.action_confirm_yes,
                        (d, w) -> viewModel.resolveDestructiveConfirmation(true))
                .setNegativeButton(R.string.action_confirm_no,
                        (d, w) -> viewModel.resolveDestructiveConfirmation(false))
                .show();
    }

    /** Runs the action, first requesting its runtime permission if one is missing. */
    private void executeWithPermission(ResolvedAction action) {
        String permission = PermissionCoordinator.requiredPermission(action);
        if (permission == null || PermissionCoordinator.isGranted(activity, permission)) {
            viewModel.runAction(action);
            return;
        }
        awaitingPermission = action;
        permissionLauncher.launch(permission);
    }
}

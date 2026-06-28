// src/main/java/com/atom/app/PermissionSetupActivity.java
package com.atom.app;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.atom.app.permission.PermissionCoordinator;
import com.atom.app.permission.PermissionGate;
import com.atom.app.permission.oem.OemSettingsIntents;
import com.atom.app.settings.AtomPreferences;
import com.google.android.material.button.MaterialButton;

/**
 * First-run permission gauntlet shown once after onboarding. Lists every permission;
 * core ones (overlay, accessibility, microphone) must be granted before Continue is
 * enabled, OEM extras (notifications, battery, autostart) are skippable. Statuses
 * refresh on resume (the user returns from system screens). Routes on to MainActivity.
 */
public class PermissionSetupActivity extends AppCompatActivity {

    private AtomPreferences preferences;
    private LinearLayout rowsContainer;
    private MaterialButton continueButton;

    // One row's live view refs, rebound on resume.
    private TextView overlayStatus, accessibilityStatus, micStatus, notificationsStatus, batteryStatus, autostartStatus;
    private boolean autostartRowPresent;

    private final ActivityResultLauncher<String> micLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), g -> refresh());
    private final ActivityResultLauncher<String> notificationsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), g -> refresh());
    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> refresh());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_setup);
        preferences = new AtomPreferences(this);
        rowsContainer = findViewById(R.id.permsetup_rows);
        continueButton = findViewById(R.id.permsetup_continue);
        continueButton.setOnClickListener(v -> finishSetup());
        buildRows();
    }

    private void buildRows() {
        rowsContainer.removeAllViews();
        overlayStatus = addRow(R.string.permsetup_overlay_title, R.string.permsetup_overlay_body,
                R.string.permsetup_grant,
                v -> settingsLauncher.launch(PermissionCoordinator.overlaySettingsIntent(this)));
        accessibilityStatus = addRow(R.string.permsetup_accessibility_title, R.string.permsetup_accessibility_body,
                R.string.permsetup_open,
                v -> settingsLauncher.launch(PermissionCoordinator.accessibilitySettingsIntent()));
        micStatus = addRow(R.string.permsetup_mic_title, R.string.permsetup_mic_body,
                R.string.permsetup_grant,
                v -> micLauncher.launch(Manifest.permission.RECORD_AUDIO));
        notificationsStatus = addRow(R.string.permsetup_notifications_title, R.string.permsetup_notifications_body,
                R.string.permsetup_grant, v -> requestNotifications());
        batteryStatus = addRow(R.string.permsetup_battery_title, R.string.permsetup_battery_body,
                R.string.permsetup_grant,
                v -> settingsLauncher.launch(PermissionCoordinator.batteryOptimizationIntent(this)));

        autostartRowPresent = OemSettingsIntents.supportsAutostart(this);
        if (autostartRowPresent) {
            autostartStatus = addRow(R.string.permsetup_autostart_title, R.string.permsetup_autostart_body,
                    R.string.permsetup_done, v -> {
                        // No API to detect autostart; open the vendor screen, then mark confirmed.
                        settingsLauncher.launch(OemSettingsIntents.autostartIntent(this));
                        preferences.setOemAutostartConfirmed(true);
                    });
        }
    }

    /** Inflates one row, wires its action button, and returns its status TextView. */
    private TextView addRow(int titleRes, int bodyRes, int actionRes, View.OnClickListener onAction) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_permission_row, rowsContainer, false);
        ((TextView) row.findViewById(R.id.row_title)).setText(titleRes);
        ((TextView) row.findViewById(R.id.row_body)).setText(bodyRes);
        MaterialButton action = row.findViewById(R.id.row_action);
        action.setText(actionRes);
        action.setOnClickListener(onAction);
        rowsContainer.addView(row);
        return row.findViewById(R.id.row_status);
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            settingsLauncher.launch(PermissionCoordinator.appNotificationSettingsIntent(this));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean overlay = PermissionCoordinator.canDrawOverlays(this);
        boolean accessibility = PermissionCoordinator.isAccessibilityServiceEnabled(this);
        boolean mic = PermissionCoordinator.isGranted(this, Manifest.permission.RECORD_AUDIO);

        applyStatus(overlayStatus, overlay);
        applyStatus(accessibilityStatus, accessibility);
        applyStatus(micStatus, mic);
        applyStatus(notificationsStatus, PermissionCoordinator.notificationsEnabled(this));
        applyStatus(batteryStatus, PermissionCoordinator.isIgnoringBatteryOptimizations(this));
        if (autostartRowPresent) {
            applyAutostartStatus(autostartStatus, preferences.isOemAutostartConfirmed());
        }

        int missing = PermissionGate.missingCoreCount(overlay, accessibility, mic);
        continueButton.setEnabled(missing == 0);
        continueButton.setText(missing == 0
                ? getString(R.string.permsetup_continue)
                : getString(R.string.permsetup_continue_blocked, missing));
    }

    private void applyStatus(TextView chip, boolean granted) {
        if (chip == null) return;
        chip.setText(granted ? R.string.settings_perm_granted : R.string.settings_perm_not_granted);
        chip.setTextColor(getColor(granted ? R.color.accent : R.color.status_error));
    }

    /** Autostart can't be detected; "confirmed" reads as granted, otherwise "Action needed". */
    private void applyAutostartStatus(TextView chip, boolean confirmed) {
        if (chip == null) return;
        chip.setText(confirmed ? R.string.settings_perm_granted : R.string.settings_perm_action_needed);
        chip.setTextColor(getColor(confirmed ? R.color.accent : R.color.status_error));
    }

    private void finishSetup() {
        preferences.setPermissionSetupComplete(true);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}

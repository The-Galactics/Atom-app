package com.atom.app;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.atom.app.di.AppContainer;
import com.atom.app.overlay.FloatingBubbleService;
import com.atom.app.permission.PermissionCoordinator;
import com.atom.app.permission.oem.OemSettingsIntents;
import com.atom.app.settings.AtomPreferences;
import com.atom.infrastructure.adapter.wake.WakeWordService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {

    private MaterialButton btnToggleBubble;
    private TextView tvBubbleStatus;

    // Drives the button label/state; the service itself is the source of truth.
    private boolean bubbleEnabled = false;

    private AtomPreferences preferences;

    private TextToSpeech voicePickerTts;
    private Spinner spinnerVoice;
    // Parallel to the spinner labels; index -> Voice.getName() ("" = automatic).
    private final List<String> voiceNames = new ArrayList<>();
    // Current speech rate (0.5x..1.5x), kept in sync with the speed slider.
    private float selectedRate = 0.9f;

    // Speech-rate slider range.
    private static final float MIN_RATE = 0.5f;
    private static final float MAX_RATE = 1.5f;

    // Permission dashboard chips, refreshed in onResume.
    private TextView chipOverlayStatus, chipAccessibilityStatus, chipMicrophoneStatus, chipNotificationsStatus;
    private TextView chipBatteryStatus, chipAutostartStatus;
    private View rowAutostart;

    // Microphone runtime request from the dashboard's "Fix" button.
    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> refreshPermissionDashboard());

    // Notifications runtime request (API 33+) from the dashboard's "Fix" button.
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> refreshPermissionDashboard());

    // Overlay ("super position") permission result: re-check on return from Settings.
    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (PermissionCoordinator.canDrawOverlays(this)) {
                            toast("Overlay permission granted");
                        } else {
                            toast("Overlay permission denied");
                        }
                        refreshBubbleControl();
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ImageButton btnBack = findViewById(R.id.btn_back);
        MaterialButton btnSave = findViewById(R.id.btn_save);
        btnToggleBubble = findViewById(R.id.btn_toggle_bubble);
        tvBubbleStatus = findViewById(R.id.tv_bubble_status);
        MaterialSwitch switchTts = findViewById(R.id.switch_tts);
        EditText etUserName = findViewById(R.id.et_user_name);
        EditText etAiName = findViewById(R.id.et_ai_name);

        preferences = new AtomPreferences(this);

        // Prefill name fields from storage.
        etUserName.setText(preferences.getUserName());
        etAiName.setText(preferences.getAssistantName());

        spinnerVoice = findViewById(R.id.spinner_voice);
        com.google.android.material.button.MaterialButton btnPreviewVoice =
                findViewById(R.id.btn_preview_voice);
        btnPreviewVoice.setOnClickListener(v -> previewSelectedVoice());
        voicePickerTts = new TextToSpeech(this, this::onVoicePickerInit);

        // Speech speed: slider maps 0..100 to a 0.5x..1.5x rate, persisted live.
        SeekBar seekBarSpeed = findViewById(R.id.seekbar_speed);
        TextView tvSpeedValue = findViewById(R.id.tv_speed_value);
        selectedRate = preferences.getTtsRate();
        seekBarSpeed.setProgress(rateToProgress(selectedRate));
        tvSpeedValue.setText(formatRate(selectedRate));
        seekBarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedRate = progressToRate(progress);
                tvSpeedValue.setText(formatRate(selectedRate));
                preferences.setTtsRate(selectedRate);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Spoken responses toggle: reflect stored value and persist immediately.
        switchTts.setChecked(preferences.isTtsEnabled());
        switchTts.setOnCheckedChangeListener(
                (button, checked) -> preferences.setTtsEnabled(checked));

        btnBack.setOnClickListener(v -> finish());

        btnSave.setOnClickListener(v -> {
            // Persist profile + assistant names. (TTS on/off, voice and speed are
            // already persisted live by their own listeners.)
            preferences.setUserName(etUserName.getText().toString());
            preferences.setAssistantName(etAiName.getText().toString());

            // The assistant name IS the wake word. Reconfigure in place when the
            // listener is already running (no stop->start FGS churn); only do a
            // full start when enabling from a stopped state.
            boolean wasEnabled = preferences.isWakeWordEnabled();
            preferences.setWakeWordName(preferences.getAssistantName());
            preferences.setWakeWordEnabled(true);
            if (wasEnabled) {
                reconfigureWakeService();
            } else {
                startWakeService();
            }

            toast(getString(R.string.settings_saved));
            ensureAssistantPermissions();
        });

        btnToggleBubble.setOnClickListener(v -> toggleFloatingBubble());

        refreshBubbleControl();
        setupPermissionDashboard();
        setupWakeWordSection();
        setupLanguageSection();

        // Logout: clear the session tokens and return to the login screen.
        MaterialButton btnLogout = findViewById(R.id.logoutButton);
        btnLogout.setOnClickListener(v -> {
            AppContainer container = ((AtomApp) getApplication()).getAppContainer();
            container.getAuthUseCase().logout();
            Intent logoutIntent = new Intent(this, LoginActivity.class);
            logoutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(logoutIntent);
            finish();
        });
    }

    // --- Permission dashboard ------------------------------------------------

    /** Wires the four "Fix" buttons; statuses are filled in by {@link #refreshPermissionDashboard}. */
    private void setupPermissionDashboard() {
        chipOverlayStatus = findViewById(R.id.chip_overlay_status);
        chipAccessibilityStatus = findViewById(R.id.chip_accessibility_status);
        chipMicrophoneStatus = findViewById(R.id.chip_microphone_status);
        chipNotificationsStatus = findViewById(R.id.chip_notifications_status);

        MaterialButton btnFixOverlay = findViewById(R.id.btn_fix_overlay);
        MaterialButton btnFixAccessibility = findViewById(R.id.btn_fix_accessibility);
        MaterialButton btnFixMicrophone = findViewById(R.id.btn_fix_microphone);
        MaterialButton btnFixNotifications = findViewById(R.id.btn_fix_notifications);

        chipBatteryStatus = findViewById(R.id.chip_battery_status);
        chipAutostartStatus = findViewById(R.id.chip_autostart_status);
        rowAutostart = findViewById(R.id.row_autostart);

        MaterialButton btnFixBattery = findViewById(R.id.btn_fix_battery);
        MaterialButton btnFixAutostart = findViewById(R.id.btn_fix_autostart);

        // Reuse PermissionCoordinator so the permission constants/intents aren't duplicated.
        btnFixOverlay.setOnClickListener(v ->
                overlayPermissionLauncher.launch(PermissionCoordinator.overlaySettingsIntent(this)));
        btnFixAccessibility.setOnClickListener(v ->
                startActivity(PermissionCoordinator.accessibilitySettingsIntent()));
        btnFixMicrophone.setOnClickListener(v -> fixMicrophonePermission());
        btnFixNotifications.setOnClickListener(v -> fixNotificationsPermission());
        btnFixBattery.setOnClickListener(v ->
                startActivity(PermissionCoordinator.batteryOptimizationIntent(this)));
        btnFixAutostart.setOnClickListener(v -> {
            startActivity(OemSettingsIntents.autostartIntent(this));
            preferences.setOemAutostartConfirmed(true);
        });
        // Hide the autostart row on skins that have no such screen (OneUI/Stock).
        rowAutostart.setVisibility(
                OemSettingsIntents.supportsAutostart(this) ? View.VISIBLE : View.GONE);
    }

    /**
     * Microphone fix: request the runtime permission directly when we can still
     * prompt; otherwise route to the app's details page (the system won't prompt
     * again after a permanent denial).
     */
    private void fixMicrophonePermission() {
        if (PermissionCoordinator.isGranted(this, Manifest.permission.RECORD_AUDIO)) {
            return;
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                || !hasRequestedMicBefore()) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        }
    }

    // Best-effort: rationale==false before the first ask too, so always allow the
    // first in-app prompt. We can't distinguish "never asked" from "permanently
    // denied" via the platform alone, so the launcher's no-op-on-permanent-denial
    // is acceptable here (the chip simply stays "Not granted").
    private boolean hasRequestedMicBefore() {
        return false;
    }

    /**
     * Notifications fix: on API 33+ request the runtime permission directly when we can
     * still prompt; otherwise (or below 33, where notifications are an app-level toggle)
     * route to Atom's system notification settings.
     */
    private void fixNotificationsPermission() {
        if (PermissionCoordinator.notificationsEnabled(this)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            startActivity(PermissionCoordinator.appNotificationSettingsIntent(this));
        }
    }

    /** Refreshes the six status chips to reflect the current grant state. */
    private void refreshPermissionDashboard() {
        applyChip(chipOverlayStatus, PermissionCoordinator.canDrawOverlays(this));
        applyChip(chipAccessibilityStatus,
                PermissionCoordinator.isAccessibilityServiceEnabled(this));
        applyChip(chipMicrophoneStatus,
                PermissionCoordinator.isGranted(this, Manifest.permission.RECORD_AUDIO));
        applyChip(chipNotificationsStatus, PermissionCoordinator.notificationsEnabled(this));
        applyChip(chipBatteryStatus, PermissionCoordinator.isIgnoringBatteryOptimizations(this));
        if (rowAutostart != null && rowAutostart.getVisibility() == View.VISIBLE) {
            boolean confirmed = preferences.isOemAutostartConfirmed();
            chipAutostartStatus.setText(confirmed
                    ? R.string.settings_perm_granted : R.string.settings_perm_action_needed);
            chipAutostartStatus.setTextColor(getColor(confirmed ? R.color.accent : R.color.status_error));
        }
    }

    /** Colors and labels a status chip: accent when granted, label tint when not. */
    private void applyChip(TextView chip, boolean granted) {
        if (chip == null) {
            return;
        }
        chip.setText(granted ? R.string.settings_perm_granted : R.string.settings_perm_not_granted);
        chip.setTextColor(getColor(granted ? R.color.accent : R.color.status_error));
    }

    // --- Wake word section ---------------------------------------------------

    private void setupWakeWordSection() {
        MaterialSwitch switchWakeEnable = findViewById(R.id.switch_wake_enable);
        MaterialSwitch switchWakeScreenOn = findViewById(R.id.switch_wake_screen_on);
        TextView labelScreenOn = findViewById(R.id.label_wake_screen_on);

        switchWakeEnable.setChecked(preferences.isWakeWordEnabled());
        switchWakeScreenOn.setChecked(preferences.isWakeWordScreenOnOnly());
        // The screen-on toggle only matters while the wake word is on.
        applyWakeScreenOnEnabled(switchWakeScreenOn, labelScreenOn,
                preferences.isWakeWordEnabled());

        switchWakeEnable.setOnCheckedChangeListener((button, checked) -> {
            preferences.setWakeWordEnabled(checked);
            applyWakeScreenOnEnabled(switchWakeScreenOn, labelScreenOn, checked);
            // Start/stop the always-on listener to match the toggle, mirroring how
            // the Save flow (re)starts it elsewhere in this Activity.
            if (checked) {
                startWakeService();
            } else {
                stopWakeService();
            }
        });

        switchWakeScreenOn.setOnCheckedChangeListener(
                (button, checked) -> preferences.setWakeWordScreenOnOnly(checked));
    }

    /** Greys the screen-on toggle out when the wake word itself is disabled. */
    private void applyWakeScreenOnEnabled(MaterialSwitch toggle, TextView label, boolean enabled) {
        toggle.setEnabled(enabled);
        label.setEnabled(enabled);
        toggle.setAlpha(enabled ? 1f : 0.5f);
        label.setAlpha(enabled ? 1f : 0.5f);
    }

    // --- Language section ----------------------------------------------------

    private void setupLanguageSection() {
        Spinner spinnerLanguage = findViewById(R.id.spinner_language);

        // Order MUST match languageCodeForPosition / positionForLanguage below.
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{
                getString(R.string.settings_language_system),
                getString(R.string.settings_language_english),
                getString(R.string.settings_language_spanish)});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(adapter);
        spinnerLanguage.setSelection(positionForLanguage(preferences.getLanguage()));

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String code = languageCodeForPosition(position);
                if (code.equals(preferences.getLanguage())) {
                    return; // no change (e.g. initial selection callback)
                }
                preferences.setLanguage(code);
                // Apply immediately; AppCompat recreates the Activity in the new locale.
                AtomApp.applyLocale(code);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private static String languageCodeForPosition(int position) {
        return switch (position) {
            case 1 -> AtomPreferences.LANGUAGE_ENGLISH;
            case 2 -> AtomPreferences.LANGUAGE_SPANISH;
            default -> AtomPreferences.LANGUAGE_SYSTEM;
        };
    }

    private static int positionForLanguage(String code) {
        if (AtomPreferences.LANGUAGE_ENGLISH.equals(code)) {
            return 1;
        }
        if (AtomPreferences.LANGUAGE_SPANISH.equals(code)) {
            return 2;
        }
        return 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Permission grants can change while we're away (the user fixed one in
        // system Settings), so re-read them every time the screen comes forward.
        refreshPermissionDashboard();
    }

    private void ensureAssistantPermissions() {
        // Overlay ("super position"): draw the assistant UI on top of other apps.
        if (!PermissionCoordinator.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(
                    PermissionCoordinator.overlaySettingsIntent(this));
            return;
        }

        // Accessibility service: lets Atom read on-screen content and act across apps.
        if (!PermissionCoordinator.isAccessibilityServiceEnabled(this)) {
            toast("Enable \"Atom\" under Accessibility");
            startActivity(PermissionCoordinator.accessibilitySettingsIntent());
        }
    }

    private void toggleFloatingBubble() {
        if (bubbleEnabled) {
            startService(new Intent(this, FloatingBubbleService.class)
                    .setAction(FloatingBubbleService.ACTION_STOP));
            bubbleEnabled = false;
            new AtomPreferences(this).setBubbleEnabled(false);
            toast(getString(R.string.settings_bubble_stopped));
            refreshBubbleControl();
            return;
        }

        if (!PermissionCoordinator.canDrawOverlays(this)) {
            toast(getString(R.string.settings_bubble_needs_overlay));
            overlayPermissionLauncher.launch(
                    PermissionCoordinator.overlaySettingsIntent(this));
            return;
        }

        Intent start = new Intent(this, FloatingBubbleService.class)
                .setAction(FloatingBubbleService.ACTION_START);
        startForegroundService(start);
        bubbleEnabled = true;
        new AtomPreferences(this).setBubbleEnabled(true);
        toast(getString(R.string.settings_bubble_started));
        refreshBubbleControl();
    }

    /** Syncs the toggle's label and the status line with overlay-permission/run state. */
    private void refreshBubbleControl() {
        boolean canDraw = PermissionCoordinator.canDrawOverlays(this);
        btnToggleBubble.setText(bubbleEnabled
                ? R.string.settings_disable_bubble
                : R.string.settings_enable_bubble);

        if (!canDraw) {
            tvBubbleStatus.setVisibility(TextView.VISIBLE);
            tvBubbleStatus.setText(R.string.settings_bubble_needs_overlay);
        } else if (bubbleEnabled) {
            tvBubbleStatus.setVisibility(TextView.VISIBLE);
            tvBubbleStatus.setText(R.string.settings_bubble_started);
        } else {
            tvBubbleStatus.setVisibility(TextView.GONE);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void onVoicePickerInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            return;
        }
        new Thread(() -> {
            final List<String> labels = new ArrayList<>();
            final List<String> names = new ArrayList<>();
            labels.add(getString(R.string.settings_tts_voice_auto));
            names.add("");

            Set<Voice> voices = safeVoices();
            List<Voice> spanish = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Voice v : voices) {
                if (v == null || v.getLocale() == null
                        || !"es".equalsIgnoreCase(v.getLocale().getLanguage())) {
                    continue;
                }
                if (v.isNetworkConnectionRequired()) {
                    continue; // offline voices only: keep playback instant
                }
                if (v.getFeatures() != null
                        && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
                    continue;
                }
                if (!seen.add(v.getName())) {
                    continue;
                }
                spanish.add(v);
            }
            Collections.sort(spanish, (a, b) -> a.getName().compareTo(b.getName()));
            for (Voice v : spanish) {
                labels.add(voiceLabel(v));
                names.add(v.getName());
            }

            runOnUiThread(() -> {
                voiceNames.clear();
                voiceNames.addAll(names);
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        this, android.R.layout.simple_spinner_item, labels);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerVoice.setAdapter(adapter);
                int idx = voiceNames.indexOf(preferences.getTtsVoice());
                spinnerVoice.setSelection(idx < 0 ? 0 : idx);
                spinnerVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        preferences.setTtsVoice(voiceNames.get(position));
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
            });
        }, "atom-voice-filter").start();
    }

    private String voiceLabel(Voice v) {
        String country = v.getLocale().getCountry();
        String accent = "ES".equalsIgnoreCase(country) ? "España"
                : "US".equalsIgnoreCase(country) ? "Latino" : country;
        String code = v.getName();
        String[] parts = v.getName().split("-");
        if (parts.length >= 4) {
            code = parts[3];
        }
        return "Español " + accent + " · " + code;
    }

    private void previewSelectedVoice() {
        if (voicePickerTts == null) {
            return;
        }
        int pos = spinnerVoice.getSelectedItemPosition();
        String name = (pos >= 0 && pos < voiceNames.size()) ? voiceNames.get(pos) : "";
        voicePickerTts.setLanguage(new Locale("es", "ES"));
        if (name != null && !name.isEmpty()) {
            for (Voice v : safeVoices()) {
                if (name.equalsIgnoreCase(v.getName())) {
                    voicePickerTts.setVoice(v);
                    break;
                }
            }
        }
        voicePickerTts.setPitch(0.95f);
        voicePickerTts.setSpeechRate(selectedRate);
        voicePickerTts.speak("Hola, soy Atom. Así sueno con esta voz.",
                TextToSpeech.QUEUE_FLUSH, null, "atom_voice_preview");
    }

    private void startWakeService() {
        startForegroundService(new Intent(this, WakeWordService.class)
                .setAction(WakeWordService.ACTION_START));
    }

    /** Rebuilds the running listener for a changed name without an FGS stop->start. */
    private void reconfigureWakeService() {
        startForegroundService(new Intent(this, WakeWordService.class)
                .setAction(WakeWordService.ACTION_RECONFIGURE));
    }

    private void stopWakeService() {
        startService(new Intent(this, WakeWordService.class)
                .setAction(WakeWordService.ACTION_STOP));
    }

    private static int rateToProgress(float rate) {
        int p = Math.round((rate - MIN_RATE) / (MAX_RATE - MIN_RATE) * 100f);
        return Math.max(0, Math.min(100, p));
    }

    private static float progressToRate(int progress) {
        return MIN_RATE + (progress / 100f) * (MAX_RATE - MIN_RATE);
    }

    private static String formatRate(float rate) {
        return String.format(Locale.US, "%.1fx", rate);
    }

    private Set<Voice> safeVoices() {
        try {
            Set<Voice> v = voicePickerTts.getVoices();
            return v != null ? v : Collections.emptySet();
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    @Override
    public void finish() {
        super.finish();
        com.atom.app.ui.NavTransitions.apply(this);
    }

    @Override
    protected void onDestroy() {
        if (voicePickerTts != null) {
            voicePickerTts.stop();
            voicePickerTts.shutdown();
            voicePickerTts = null;
        }
        super.onDestroy();
    }
}

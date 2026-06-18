package com.atom.app;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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

import com.atom.app.overlay.FloatingBubbleService;
import com.atom.app.permission.PermissionCoordinator;
import com.atom.app.settings.AtomPreferences;
import com.atom.infrastructure.adapter.screen.ScreenCaptureService;
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

    // FUTURE WORK: consent result just starts the scaffolding service to test the round-trip.
    private final ActivityResultLauncher<Intent> screenCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
                            serviceIntent.putExtra(
                                    ScreenCaptureService.EXTRA_RESULT_CODE, result.getResultCode());
                            serviceIntent.putExtra(
                                    ScreenCaptureService.EXTRA_RESULT_DATA, result.getData());
                            startForegroundService(serviceIntent);
                            toast("Screen capture consent granted (scaffolding)");
                        } else {
                            toast("Screen capture consent denied");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ImageButton btnBack = findViewById(R.id.btn_back);
        SeekBar seekBarVolume = findViewById(R.id.seekbar_volume);
        TextView tvVolumeValue = findViewById(R.id.tv_volume_value);
        MaterialButton btnSave = findViewById(R.id.btn_save);
        btnToggleBubble = findViewById(R.id.btn_toggle_bubble);
        tvBubbleStatus = findViewById(R.id.tv_bubble_status);
        MaterialSwitch switchTts = findViewById(R.id.switch_tts);

        preferences = new AtomPreferences(this);

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

        // Handle Volume Changes
        seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvVolumeValue.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSave.setOnClickListener(v -> {
            // FUTURE WORK: persist profile name / assistant name / volume to storage.
            toast(getString(R.string.settings_saved));
            ensureAssistantPermissions();
        });

        btnToggleBubble.setOnClickListener(v -> toggleFloatingBubble());

        refreshBubbleControl();
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
            return;
        }

        // Screen capture (MediaProjection). FUTURE WORK: capture itself is not implemented.
        screenCaptureLauncher.launch(
                PermissionCoordinator.screenCaptureIntent(this));
    }

    private void toggleFloatingBubble() {
        if (bubbleEnabled) {
            startService(new Intent(this, FloatingBubbleService.class)
                    .setAction(FloatingBubbleService.ACTION_STOP));
            bubbleEnabled = false;
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
        final List<String> labels = new ArrayList<>();
        voiceNames.clear();
        labels.add(getString(R.string.settings_tts_voice_auto));
        voiceNames.add("");

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
            voiceNames.add(v.getName());
        }

        runOnUiThread(() -> {
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
    protected void onDestroy() {
        if (voicePickerTts != null) {
            voicePickerTts.stop();
            voicePickerTts.shutdown();
            voicePickerTts = null;
        }
        super.onDestroy();
    }
}

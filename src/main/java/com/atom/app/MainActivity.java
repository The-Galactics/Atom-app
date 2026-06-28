package com.atom.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.atom.app.di.AppContainer;
import com.atom.app.settings.AtomPreferences;
import com.atom.app.ui.AtomCoreView;
import com.atom.app.ui.MicAnimations;
import com.atom.app.ui.main.MainInputBarComponent;
import com.atom.app.ui.main.MainViewModelBinder;
import com.atom.app.ui.main.SpeechRecognitionCoordinator;
import com.atom.app.ui.motion.CoreState;
import com.atom.app.ui.motion.CoreStatePresenter;
import com.atom.app.ui.motion.CoreStyle;
import com.atom.app.ui.motion.MotionPreferences;
import com.atom.app.ui.motion.StatusCrossfader;
import com.atom.app.viewmodel.ChatViewModel;
import com.atom.app.viewmodel.ChatViewModelFactory;
import com.atom.infrastructure.adapter.voice.AndroidTextToSpeech;
import com.atom.infrastructure.adapter.wake.WakeWordService;

public class MainActivity extends AppCompatActivity {

    // Delay before an error message fades back to the idle resting state.
    private static final long ERROR_AUTO_RECOVER_MS = 4000;

    // Saved-instance keys for surviving configuration changes (e.g. rotation).
    private static final String KEY_STATUS = "status_text";
    private static final String KEY_SUB_STATUS = "sub_status_text";
    private static final String KEY_ENERGY = "core_energy";

    private AtomCoreView atomCore;
    private ImageButton btnMic, btnSettings, btnHistory, btnKeyboard, btnVolume;
    private TextView statusText, subStatusText, wordmark;
    private ChatViewModel viewModel;

    // Shared mic feedback (press-settle + breathing pulse) reused from the overlay.
    private final MicAnimations micAnimations = new MicAnimations();

    private MainInputBarComponent inputBar;
    private MainViewModelBinder binder;

    // True while capturing a spoken confirmation reply, so the transcript is fed to
    // the loop (submitSpokenConfirmation) instead of being dispatched as a new order.
    private boolean confirmationCapturing;

    private AndroidTextToSpeech tts;
    private AtomPreferences preferences;
    private SpeechRecognitionCoordinator recognition;

    // Last applied core energy, kept so it can be restored across configuration changes.
    private float currentEnergy = CoreStatePresenter.energyFor(CoreState.IDLE);

    // Posted after an error to ease the status line back to idle.
    private final Runnable errorRecoverRunnable = this::recoverFromError;

    // Wake word fired while the app is open: capture with the in-app mic.
    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!recognition.isListening()
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED) {
                recognition.start();
            } else {
                // Can't capture now (already listening / no permission): release the
                // wake engine immediately instead of letting it wait for the fallback.
                notifyWakeDone();
            }
        }
    };

    // Keeps the mute icon/core in sync when the flag is toggled from the overlay.
    private final SharedPreferences.OnSharedPreferenceChangeListener muteListener =
            (sp, key) -> {
                if (AtomPreferences.KEY_MIC_MUTED.equals(key)) {
                    applyMicMutedState(preferences.isMicMuted());
                }
            };

    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    recognition.start();
                } else {
                    onMicPermissionDenied();
                }
            });

    // Requested once on startup so calls can place directly and resolve names from
    // contacts; the per-action permission gate still enforces before each call.
    private final ActivityResultLauncher<String[]> startupPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(), results -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Auth gate: redirect to LoginActivity if the user is not authenticated.
        // This runs before any UI inflation so an unauthenticated user never sees
        // a flash of the home screen.
        AppContainer authContainer = ((AtomApp) getApplication()).getAppContainer();
        if (!authContainer.getAuthUseCase().isAuthenticated()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // First-run routing: if onboarding hasn't been completed, hand off to it
        // before inflating the main UI so the user never sees a flash of the home
        // screen. We finish() immediately so back from onboarding leaves the app.
        preferences = new AtomPreferences(this);
        if (!preferences.isOnboardingComplete()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // Initialize ViewModel via the composition root (AppContainer).
        AppContainer appContainer = ((AtomApp) getApplication()).getAppContainer();
        viewModel = new ViewModelProvider(this, new ChatViewModelFactory(appContainer))
                .get(ChatViewModel.class);

        tts = new AndroidTextToSpeech(this, preferences.getTtsVoice(), preferences.getTtsRate());

        // Initialize UI Components
        atomCore = findViewById(R.id.atom_core_animation);
        btnMic = findViewById(R.id.btn_mic);
        btnSettings = findViewById(R.id.btn_settings);
        btnHistory = findViewById(R.id.btn_history);
        btnKeyboard = findViewById(R.id.btn_keyboard);
        btnVolume = findViewById(R.id.btn_volume);
        statusText = findViewById(R.id.status_text);
        subStatusText = findViewById(R.id.sub_status_text);
        wordmark = findViewById(R.id.wordmark);

        // Start the atom core in its calm idle state (low energy); AtomCoreView owns the glow.
        if (atomCore != null) {
            atomCore.setEnergy(CoreStatePresenter.energyFor(CoreState.IDLE));
        }

        binder = new MainViewModelBinder(this, viewModel,
                new MainViewModelBinder.Host() {
                    @Override
                    public void showResponse(String response) {
                        fadeSwap(statusText, response);
                        fadeSwap(subStatusText, getString(R.string.sub_status_responded));
                        applyCoreState(CoreState.RESPONDED);
                        if (preferences.isTtsEnabled()) {
                            tts.speak(response);
                        }
                    }

                    @Override
                    public void showThinking() {
                        fadeSwap(statusText, getString(R.string.status_thinking));
                        fadeSwap(subStatusText, getString(R.string.sub_status_thinking));
                        applyCoreState(CoreState.THINKING);
                    }

                    @Override
                    public void showError(String error) {
                        fadeSwap(statusText, getString(R.string.status_error));
                        fadeSwap(subStatusText,
                                error != null
                                        ? error.toUpperCase(java.util.Locale.getDefault())
                                        : getString(R.string.status_error));
                        applyCoreState(CoreState.ERROR);
                        statusText.removeCallbacks(errorRecoverRunnable);
                        statusText.postDelayed(errorRecoverRunnable, ERROR_AUTO_RECOVER_MS);
                    }

                    @Override
                    public void setInputEnabled(boolean enabled) {
                        inputBar.setInputEnabled(enabled);
                    }

                    @Override
                    public void showOperating() {
                        fadeSwap(statusText, getString(R.string.automation_operating));
                        fadeSwap(subStatusText, getString(R.string.automation_operating));
                        applyCoreState(CoreState.OPERATING);
                    }

                    @Override
                    public void onVoiceConfirmationRequested(String question) {
                        askConfirmationByVoice(question);
                    }
                });
        binder.bind();

        inputBar = new MainInputBarComponent(this, new MainInputBarComponent.Host() {
            @Override
            public void onSubmitText(String t) {
                if (tts != null) tts.stop();
                dispatchOrder(t);
            }
            @Override
            public void toast(String m) {
                MainActivity.this.toast(m);
            }
        });

        requestCallPermissionsIfNeeded();

        recognition = new SpeechRecognitionCoordinator(this, preferences,
                new SpeechRecognitionCoordinator.Host() {
                    @Override
                    public void onListeningStarted() {
                        if (tts != null) tts.stop();
                        statusText.removeCallbacks(errorRecoverRunnable);
                        clearRetryAffordance();
                        fadeSwap(statusText, getString(R.string.status_listening));
                        fadeSwap(subStatusText, getString(R.string.sub_status_listening));
                        applyCoreState(CoreState.LISTENING);
                        micAnimations.startMicPulse(btnMic);
                    }

                    @Override
                    public void onListeningReady() {
                        fadeSwap(statusText, getString(R.string.status_listening));
                        fadeSwap(subStatusText, getString(R.string.sub_status_listening));
                        applyCoreState(CoreState.LISTENING);
                    }

                    @Override
                    public void onThinking() {
                        fadeSwap(statusText, getString(R.string.status_thinking));
                        fadeSwap(subStatusText, getString(R.string.sub_status_thinking));
                        micAnimations.stopMicPulse(btnMic);
                        applyCoreState(CoreState.THINKING);
                    }

                    @Override
                    public void onPartialTranscript(String text) {
                        if (subStatusText != null && text != null && !text.trim().isEmpty()) {
                            subStatusText.animate().cancel();
                            subStatusText.setAlpha(1f);
                            subStatusText.setText(text);
                        }
                    }

                    @Override
                    public void onFinalTranscript(String text) {
                        // A pending confirmation captures the reply for the loop, not a new order.
                        if (confirmationCapturing) {
                            confirmationCapturing = false;
                            viewModel.submitSpokenConfirmation(text == null ? "" : text);
                            return;
                        }
                        dispatchOrder(text);
                    }

                    @Override
                    public void onRecognitionError(String message) {
                        // A failed confirmation capture submits "no answer" so the loop resumes/aborts.
                        if (confirmationCapturing) {
                            confirmationCapturing = false;
                            viewModel.submitSpokenConfirmation("");
                            return;
                        }
                        int msg = "unavailable".equals(message)
                                ? R.string.stt_unavailable
                                : R.string.stt_error;
                        fadeSwap(statusText, getString(R.string.status_idle));
                        micAnimations.stopMicPulse(btnMic);
                        applyCoreState(CoreState.IDLE);
                        toast(getString(msg));
                        showRetryAffordance();
                    }

                    @Override
                    public void onListeningCancelled() {
                        // Cancel during a pending confirmation: unblock the waiting loop now
                        // and abort the held action (never run a destructive action without an
                        // explicit spoken answer). Without this, the loop would block until the
                        // timeout and a following mic tap would be misread as the sí/no reply.
                        if (confirmationCapturing) {
                            confirmationCapturing = false;
                            viewModel.submitSpokenConfirmation(ChatViewModel.CANT_ASK);
                        }
                        micAnimations.stopMicPulse(btnMic);
                        applyCoreState(CoreState.IDLE);
                        fadeSwap(statusText, getString(R.string.status_idle));
                    }
                });

        btnMic.setOnClickListener(v -> onMicTapped());

        // Long-press the mic FAB to mute/unmute voice input. A regular tap still starts
        // listening; muting gates that so a muted mic can't be triggered accidentally.
        btnMic.setOnLongClickListener(v -> {
            toggleMicMuted();
            return true;
        });

        // Restore the persisted mute state so the icon matches reality after a cold
        // start, process death, or configuration change (the pref outlives the Activity).
        applyMicMutedState(preferences.isMicMuted());

        // Bring back the status line and core state after a configuration change.
        restoreUiState(savedInstanceState);

        // Track mute changes made elsewhere (e.g. the floating bubble).
        preferences.registerChangeListener(muteListener);

        btnSettings.setOnClickListener(v ->
                com.atom.app.ui.NavTransitions.start(this, SettingsActivity.class));

        btnHistory.setOnClickListener(v ->
                com.atom.app.ui.NavTransitions.start(this, HistoryActivity.class));

        btnKeyboard.setOnClickListener(v -> inputBar.toggle());

        btnVolume.setOnClickListener(v -> showVolumeSlider());

        setupQuickActions();
    }

    /**
     * Wires the quick-action chips. Ambiguous actions (timer/call/message need a
     * value the user must supply) pre-fill the input bar with a starter phrase so
     * the user can complete and confirm. Clearly unambiguous toggles (Wi-Fi,
     * flashlight) are dispatched straight away as orders.
     */
    private void setupQuickActions() {
        findViewById(R.id.chip_timer).setOnClickListener(v ->
                inputBar.prefill(getString(R.string.chip_phrase_timer)));
        findViewById(R.id.chip_call).setOnClickListener(v ->
                inputBar.prefill(getString(R.string.chip_phrase_call)));
        findViewById(R.id.chip_message).setOnClickListener(v ->
                inputBar.prefill(getString(R.string.chip_phrase_message)));
        findViewById(R.id.chip_wifi).setOnClickListener(v ->
                dispatchOrder(getString(R.string.chip_phrase_wifi)));
        findViewById(R.id.chip_flashlight).setOnClickListener(v ->
                dispatchOrder(getString(R.string.chip_phrase_flashlight)));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (inputBar != null) inputBar.dismissIfTouchOutside(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Keep the visible status line and core state through a configuration change.
        outState.putString(KEY_STATUS, statusText.getText().toString());
        outState.putString(KEY_SUB_STATUS, subStatusText.getText().toString());
        outState.putFloat(KEY_ENERGY, currentEnergy);
    }

    /** Restores the status line and core state saved before a configuration change. */
    private void restoreUiState(Bundle state) {
        if (state == null) {
            return;
        }
        statusText.setText(state.getString(KEY_STATUS, getString(R.string.status_idle)));
        subStatusText.setText(state.getString(KEY_SUB_STATUS, getString(R.string.sub_status_tap_mic)));
        applyCoreState(state.getFloat(KEY_ENERGY, CoreStatePresenter.energyFor(CoreState.IDLE)));
    }

    /** Shared dispatch point for keyboard, chip, and voice order paths. */
    private void dispatchOrder(String text) {
        viewModel.sendOrder(text);
    }

    private void showVolumeSlider() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_SAME,
                    AudioManager.FLAG_SHOW_UI);
        }
    }

    /** Handles a mic tap: cancel if listening, hint if muted, otherwise start listening. */
    private void onMicTapped() {
        btnMic.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        MicAnimations.playPressSettle(btnMic);
        // Muted mic can't listen: hint and bail.
        if (preferences.isMicMuted()) {
            toast(getString(R.string.mic_muted_hint));
            return;
        }
        // Tapping mid-listen cancels the in-flight recognition.
        if (recognition.isListening()) {
            recognition.cancel();
            fadeSwap(subStatusText, getString(R.string.sub_status_tap_mic));
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            recognition.start();
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    /**
     * Mic permission was denied: explain why if we can still ask, otherwise route to
     * app settings (the system won't prompt again after a permanent "Don't allow").
     */
    private void onMicPermissionDenied() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            toast(getString(R.string.mic_permission_rationale));
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.mic_permission_title)
                    .setMessage(R.string.mic_permission_settings)
                    .setPositiveButton(R.string.mic_permission_open_settings,
                            (d, w) -> openAppSettings())
                    .setNegativeButton(R.string.action_confirm_no, null)
                    .show();
        }
    }

    /** Opens this app's system settings page so the user can grant the microphone. */
    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    /** Flips the persisted mute flag, refreshes the FAB, and stops capture if muting mid-listen. */
    private void toggleMicMuted() {
        boolean muted = !preferences.isMicMuted();
        preferences.setMicMuted(muted);
        applyMicMutedState(muted);
        btnMic.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        MicAnimations.playPressSettle(btnMic);
        if (muted && recognition.isListening()) {
            recognition.cancel();
        }
        fadeSwap(subStatusText, getString(
                muted ? R.string.sub_status_muted : R.string.sub_status_tap_mic));
    }

    /** Swaps the mic FAB icon and content description to match the persisted mute flag. */
    private void applyMicMutedState(boolean muted) {
        if (btnMic == null) {
            return;
        }
        btnMic.setImageResource(muted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
        btnMic.setContentDescription(getString(muted ? R.string.cd_mic_muted : R.string.cd_mic));
        // Desaturate/dim the core so a muted mic doesn't look like plain idle.
        if (atomCore != null) {
            atomCore.setMuted(muted);
        }
    }

    /** Turns the sub-status into a tappable retry hint after a failed recognition. */
    private void showRetryAffordance() {
        if (subStatusText == null) {
            return;
        }
        fadeSwap(subStatusText, getString(R.string.sub_status_retry));
        subStatusText.setOnClickListener(v -> {
            clearRetryAffordance();
            onMicTapped();
        });
    }

    /** Removes the retry tap handler so the sub-status goes back to plain text. */
    private void clearRetryAffordance() {
        if (subStatusText != null) {
            subStatusText.setOnClickListener(null);
            subStatusText.setClickable(false);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        // Show the chosen assistant name as the wordmark; fall back to "ATOM".
        if (wordmark != null) {
            String name = preferences.getAssistantName();
            wordmark.setText(name == null || name.trim().isEmpty()
                    ? getString(R.string.wordmark) : name);
        }
        // Listen for the wake word so it drives the in-app mic while we're visible.
        IntentFilter filter = new IntentFilter(WakeWordService.ACTION_WAKE_IN_APP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
                registerReceiver(wakeReceiver, filter);
        }
        // We can voice + capture a confirmation reply while visible.
        if (viewModel != null) {
            viewModel.setConfirmationUiReady(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // No longer able to voice/capture a confirmation; unblock any waiting loop.
        if (viewModel != null) {
            viewModel.setConfirmationUiReady(false);
        }
        confirmationCapturing = false;
        try {
            unregisterReceiver(wakeReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /** Tells the wake-word service the mic is free so it can resume listening. */
    private void notifyWakeDone() {
        sendBroadcast(new Intent(WakeWordService.ACTION_LISTEN_DONE).setPackage(getPackageName()));
    }

    @Override
    protected void onDestroy() {
        // First-run routing finish()es this Activity from onCreate (before setContentView
        // and findViewById) to hand off to onboarding. onDestroy still runs in that case,
        // so bail out before touching views/collaborators that were never initialized —
        // statusText is null until the full setup below the early return has executed.
        if (statusText == null) {
            super.onDestroy();
            return;
        }
        // Drop any pending error recovery so it can't fire after teardown.
        statusText.removeCallbacks(errorRecoverRunnable);
        preferences.unregisterChangeListener(muteListener);
        // Stop any running mic pulse so its animator doesn't outlive the view.
        micAnimations.stopMicPulse(btnMic);
        if (tts != null) {
            tts.shutdown();
        }
        if (recognition != null) {
            recognition.destroy();
        }
        super.onDestroy();
    }

    /** Eases the status line back to its resting state after an error, unless we're listening. */
    private void recoverFromError() {
        if (recognition != null && recognition.isListening()) {
            return;
        }
        fadeSwap(statusText, getString(R.string.status_idle));
        fadeSwap(subStatusText, getString(preferences.isMicMuted()
                ? R.string.sub_status_muted : R.string.sub_status_tap_mic));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Speaks a confirmation question out loud, then re-opens the mic to capture the
     * user's spoken reply and hands it to the loop via {@link ChatViewModel#submitSpokenConfirmation}.
     * Hands-free: no tap dialog. Signals "couldn't ask" when the mic isn't available.
     */
    private void askConfirmationByVoice(String question) {
        fadeSwap(statusText, question);
        if (preferences.isMicMuted()
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
            // Cannot capture by voice -> signal "couldn't ask" so the loop aborts.
            viewModel.submitSpokenConfirmation(ChatViewModel.CANT_ASK);
            return;
        }
        confirmationCapturing = true;
        // Speak the question (local TTS done-callback) THEN open the mic so Atom
        // doesn't hear its own voice; fall back to a short delay when TTS is off.
        Runnable openMic = () -> { if (confirmationCapturing) recognition.start(); };
        if (tts != null && preferences.isTtsEnabled()) {
            tts.speak(question, openMic);
        } else {
            statusText.postDelayed(openMic, 300L);
        }
    }

    /** Crossfades a status TextView to new text via fade-out, swap, fade-in. */
    private void fadeSwap(TextView view, CharSequence text) {
        StatusCrossfader.swap(view, text);
    }

    /** Drives the core to a semantic UI state via the shared CoreStatePresenter. */
    private void applyCoreState(CoreState state) {
        CoreStyle style = CoreStatePresenter.styleFor(state);
        style = CoreStatePresenter.resolveForReducedMotion(style, isReducedMotion());
        currentEnergy = style.energy;
        if (atomCore != null) {
            atomCore.setStyle(style);
        }
    }

    /** True when the user disabled system animations (transition scale 0). */
    private boolean isReducedMotion() {
        float scale = Settings.Global.getFloat(getContentResolver(),
                Settings.Global.TRANSITION_ANIMATION_SCALE, 1f);
        return MotionPreferences.isReducedMotion(scale);
    }

    /** Low-level energy apply; also used by the saved-state restore path. AtomCoreView's
     *  own breathing glow brightens with energy, so there is no separate glow View to drive. */
    private void applyCoreState(float energy) {
        currentEnergy = energy;
        if (atomCore != null) {
            atomCore.setEnergy(energy);
        }
    }

    /**
     * Requests CALL_PHONE and READ_CONTACTS together on startup so a "call Mom"
     * order can place the call and resolve the name without a mid-action prompt.
     */
    private void requestCallPermissionsIfNeeded() {
        String[] callPerms = {
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS
        };
        boolean needsAny = false;
        for (String p : callPerms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                needsAny = true;
                break;
            }
        }
        if (needsAny) {
            startupPermissionLauncher.launch(callPerms);
        }
    }

}

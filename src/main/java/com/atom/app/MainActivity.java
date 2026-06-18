package com.atom.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.atom.app.di.AppContainer;
import com.atom.app.permission.PermissionCoordinator;
import com.atom.app.settings.AtomPreferences;
import com.atom.app.ui.AtomCoreView;
import com.atom.app.ui.MicAnimations;
import com.atom.domain.action.ResolvedAction;
import com.atom.app.viewmodel.ChatViewModel;
import com.atom.app.viewmodel.ChatViewModelFactory;
import com.atom.infrastructure.adapter.voice.AndroidSpeechRecognizer;
import com.atom.infrastructure.adapter.voice.AndroidTextToSpeech;

public class MainActivity extends AppCompatActivity {

    // Atom core energy (0 = calm idle, 1 = fully engaged) and glow strength per app state.
    private static final float CORE_ENERGY_IDLE = 0.0f;       // calm ambient motion
    private static final float CORE_ENERGY_THINKING = 0.6f;   // working on the request
    private static final float CORE_ENERGY_LISTENING = 1.0f;  // actively capturing speech
    private static final float CORE_GLOW_IDLE = 0.35f;       // dim resting glow
    private static final float CORE_GLOW_ACTIVE = 0.7f;      // brighter while engaged
    private static final long CORE_GLOW_ANIM_MS = 280;       // glow alpha crossfade

    // Status text crossfade timing.
    private static final long TEXT_FADE_OUT_MS = 120;
    private static final long TEXT_FADE_IN_MS = 160;

    // Input bar slide-in/out timing and the fallback travel before first layout.
    private static final long INPUT_BAR_ANIM_MS = 200;
    private static final float INPUT_BAR_FALLBACK_SLIDE_DP = 64f;

    // Send disc opacity while disabled (no text to send).
    private static final float SEND_DISABLED_ALPHA = 0.4f;

    // Delay before an error message fades back to the idle resting state.
    private static final long ERROR_AUTO_RECOVER_MS = 4000;

    // Saved-instance keys for surviving configuration changes (e.g. rotation).
    private static final String KEY_STATUS = "status_text";
    private static final String KEY_SUB_STATUS = "sub_status_text";
    private static final String KEY_ENERGY = "core_energy";
    private static final String KEY_GLOW = "core_glow";

    private AtomCoreView atomCore;
    private View coreGlow;
    private ImageButton btnMic, btnSettings, btnHistory, btnKeyboard, btnVolume;
    private TextView statusText, subStatusText;
    private ChatViewModel viewModel;

    // Shared mic feedback (press-settle + breathing pulse) reused from the overlay.
    private final MicAnimations micAnimations = new MicAnimations();

    private LinearLayout inputBarRoot;
    private EditText inputEditText;
    private ImageButton inputSend;

    // Action awaiting a permission grant; resumed in permissionLauncher's callback.
    private ResolvedAction awaitingPermission;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                ResolvedAction action = awaitingPermission;
                awaitingPermission = null;
                if (action == null) {
                    return;
                }
                if (granted) {
                    viewModel.runAction(action);
                } else {
                    toast(getString(R.string.action_permission_denied));
                }
            });

    private AndroidTextToSpeech tts;
    private AtomPreferences preferences;
    private AndroidSpeechRecognizer speechRecognizer;

    // True while a recognition is in flight; lets a tap cancel it and gates error recovery.
    private boolean isListening;

    // Last applied core state, kept so it can be restored across configuration changes.
    private float currentEnergy = CORE_ENERGY_IDLE;
    private float currentGlow = CORE_GLOW_IDLE;

    // Posted after an error to ease the status line back to idle.
    private final Runnable errorRecoverRunnable = this::recoverFromError;

    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startListening();
                } else {
                    onMicPermissionDenied();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize ViewModel via the composition root (AppContainer).
        AppContainer appContainer = ((AtomApp) getApplication()).getAppContainer();
        viewModel = new ViewModelProvider(this, new ChatViewModelFactory(appContainer))
                .get(ChatViewModel.class);

        preferences = new AtomPreferences(this);
        tts = new AndroidTextToSpeech(this, preferences.getTtsVoice(), preferences.getTtsRate());

        // Initialize UI Components
        atomCore = findViewById(R.id.atom_core_animation);
        coreGlow = findViewById(R.id.core_glow);
        btnMic = findViewById(R.id.btn_mic);
        btnSettings = findViewById(R.id.btn_settings);
        btnHistory = findViewById(R.id.btn_history);
        btnKeyboard = findViewById(R.id.btn_keyboard);
        btnVolume = findViewById(R.id.btn_volume);
        statusText = findViewById(R.id.status_text);
        subStatusText = findViewById(R.id.sub_status_text);

        inputBarRoot = findViewById(R.id.input_bar_root);
        inputEditText = findViewById(R.id.input_edit_text);
        inputSend = findViewById(R.id.input_send);

        // Start the atom core in its calm idle state (dim glow, low energy).
        if (coreGlow != null) {
            coreGlow.setAlpha(CORE_GLOW_IDLE);
        }
        if (atomCore != null) {
            atomCore.setEnergy(CORE_ENERGY_IDLE);
        }

        setupObservers();
        setupInputBar();

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

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        // FUTURE WORK: no conversation store or history screen yet; this is a placeholder.
        btnHistory.setOnClickListener(v ->
                toast("History is not available yet"));

        btnKeyboard.setOnClickListener(v -> toggleInputBar());

        btnVolume.setOnClickListener(v -> showVolumeSlider());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Keep the visible status line and core state through a configuration change.
        outState.putString(KEY_STATUS, statusText.getText().toString());
        outState.putString(KEY_SUB_STATUS, subStatusText.getText().toString());
        outState.putFloat(KEY_ENERGY, currentEnergy);
        outState.putFloat(KEY_GLOW, currentGlow);
    }

    /** Restores the status line and core state saved before a configuration change. */
    private void restoreUiState(Bundle state) {
        if (state == null) {
            return;
        }
        statusText.setText(state.getString(KEY_STATUS, getString(R.string.status_idle)));
        subStatusText.setText(state.getString(KEY_SUB_STATUS, getString(R.string.sub_status_tap_mic)));
        applyCoreState(state.getFloat(KEY_ENERGY, CORE_ENERGY_IDLE),
                state.getFloat(KEY_GLOW, CORE_GLOW_IDLE));
    }

    private void setupInputBar() {
        // Accent focus border: activate the pill background's focused state.
        inputEditText.setOnFocusChangeListener((v, hasFocus) ->
                inputBarRoot.setActivated(hasFocus));

        // Send via the trailing accent disc.
        inputSend.setOnClickListener(v -> sendFromInputBar());

        // Keep send disabled/dimmed until there's non-whitespace text.
        setSendEnabled(false);
        inputEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                setSendEnabled(s.toString().trim().length() > 0);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // IME "Send" action mirrors the send button.
        inputEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendFromInputBar();
                return true;
            }
            return false;
        });
    }

    /**
     * Dismisses the input bar when the user taps anywhere outside of it. We check on the
     * initial ACTION_DOWN: if the bar is visible and the touch lands outside its on-screen
     * bounds, hide it (which clears focus + hides the keyboard + collapses the bar). We then
     * still pass the event to super so the tap that fell outside (mic, keyboard, volume, etc.)
     * is delivered normally rather than being swallowed.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN
                && inputBarRoot != null
                && inputBarRoot.getVisibility() == View.VISIBLE) {
            Rect bounds = new Rect();
            inputBarRoot.getGlobalVisibleRect(bounds);
            if (!bounds.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                hideInputBar();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /** Shows or hides the input bar, managing focus and the soft keyboard. */
    private void toggleInputBar() {
        if (inputBarRoot.getVisibility() == View.VISIBLE) {
            hideInputBar();
        } else {
            showInputBar();
        }
    }

    private void showInputBar() {
        // Slide up + fade in instead of popping into place.
        inputBarRoot.setVisibility(View.VISIBLE);
        inputBarRoot.setAlpha(0f);
        inputBarRoot.setTranslationY(inputBarSlideDistance());
        inputBarRoot.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(INPUT_BAR_ANIM_MS)
                .start();
        inputEditText.requestFocus();
        // Post the IME show to the next frame so the adjustResize layout pass (which lifts the
        // bar above the keyboard) settles independently of the slide-in, avoiding a first-open
        // double-move where the resting position shifts mid-animation.
        inputEditText.post(() -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(inputEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void hideInputBar() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(inputEditText.getWindowToken(), 0);
        }
        inputEditText.clearFocus();
        // Slide down + fade out, then actually collapse the view and reset it so the
        // next show() starts from a clean resting transform.
        inputBarRoot.animate()
                .translationY(inputBarSlideDistance())
                .alpha(0f)
                .setDuration(INPUT_BAR_ANIM_MS)
                .withEndAction(() -> {
                    inputBarRoot.setVisibility(View.GONE);
                    inputBarRoot.setTranslationY(0f);
                    inputBarRoot.setAlpha(1f);
                })
                .start();
    }

    /** Vertical travel for the input-bar slide; its measured height, or a fallback. */
    private float inputBarSlideDistance() {
        int height = inputBarRoot.getHeight();
        if (height > 0) {
            return height;
        }
        // First show happens before the bar has ever been laid out (height 0).
        return INPUT_BAR_FALLBACK_SLIDE_DP * getResources().getDisplayMetrics().density;
    }

    /** Validates and dispatches the typed message, then collapses the bar. */
    private void sendFromInputBar() {
        String text = inputEditText.getText().toString().trim();
        if (text.isEmpty()) {
            toast(getString(R.string.input_empty));
            return;
        }
        inputSend.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        // Typed input is treated as an ORDER: the backend decides whether it is
        // an executable action or a plain conversational reply.
        viewModel.sendOrder(text);
        inputEditText.setText("");
        hideInputBar();
    }

    /** Enables or dims the send disc based on whether there's text to send. */
    private void setSendEnabled(boolean enabled) {
        inputSend.setEnabled(enabled);
        inputSend.setAlpha(enabled ? 1f : SEND_DISABLED_ALPHA);
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
        if (isListening) {
            tearDownRecognizer();
            fadeSwap(subStatusText, getString(R.string.sub_status_tap_mic));
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startListening();
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

    /** Stops any active recognition and eases the core back to idle. */
    private void tearDownRecognizer() {
        if (speechRecognizer != null) {
            // AndroidSpeechRecognizer has no stopListening(); destroy() is its stop path.
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        isListening = false;
        micAnimations.stopMicPulse(btnMic);
        applyCoreState(CORE_ENERGY_IDLE, CORE_GLOW_IDLE);
        fadeSwap(statusText, getString(R.string.status_idle));
    }

    /** Flips the persisted mute flag, refreshes the FAB, and stops capture if muting mid-listen. */
    private void toggleMicMuted() {
        boolean muted = !preferences.isMicMuted();
        preferences.setMicMuted(muted);
        applyMicMutedState(muted);
        btnMic.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        MicAnimations.playPressSettle(btnMic);
        if (muted && isListening) {
            tearDownRecognizer();
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

    private void startListening() {
        if (speechRecognizer == null) {
            speechRecognizer = new AndroidSpeechRecognizer(this, new SttListener());
        }
        isListening = true;
        // Drop any pending error recovery now that we're active again.
        statusText.removeCallbacks(errorRecoverRunnable);
        fadeSwap(statusText, getString(R.string.status_listening));
        fadeSwap(subStatusText, getString(R.string.sub_status_listening));
        // Drive the atom core brighter/faster and start the listening mic pulse.
        applyCoreState(CORE_ENERGY_LISTENING, CORE_GLOW_ACTIVE);
        micAnimations.startMicPulse(btnMic);
        speechRecognizer.startListening();
    }

    /** Routes recognizer callbacks to UI state and dispatches the transcript as an order. */
    private final class SttListener implements AndroidSpeechRecognizer.Listener {
        @Override
        public void onReadyForSpeech() {
            fadeSwap(statusText, getString(R.string.status_listening));
            fadeSwap(subStatusText, getString(R.string.sub_status_listening));
            applyCoreState(CORE_ENERGY_LISTENING, CORE_GLOW_ACTIVE);
        }

        @Override
        public void onEndOfSpeech() {
            isListening = false;
            fadeSwap(statusText, getString(R.string.status_thinking));
            fadeSwap(subStatusText, getString(R.string.sub_status_thinking));
            // Speech captured: settle the mic pulse and ease the core to thinking.
            micAnimations.stopMicPulse(btnMic);
            applyCoreState(CORE_ENERGY_THINKING, CORE_GLOW_ACTIVE);
        }

        @Override
        public void onResult(String text) {
            // Speech is treated as an ORDER, same as typed input.
            viewModel.sendOrder(text);
        }

        @Override
        public void onError(String message) {
            isListening = false;
            int msg = "unavailable".equals(message)
                    ? R.string.stt_unavailable
                    : R.string.stt_error;
            fadeSwap(statusText, getString(R.string.status_idle));
            fadeSwap(subStatusText, getString(R.string.sub_status_tap_mic));
            // Recognition failed: stop the pulse and return the core to its calm idle.
            micAnimations.stopMicPulse(btnMic);
            applyCoreState(CORE_ENERGY_IDLE, CORE_GLOW_IDLE);
            toast(getString(msg));
        }
    }

    @Override
    protected void onDestroy() {
        // Drop any pending error recovery so it can't fire after teardown.
        statusText.removeCallbacks(errorRecoverRunnable);
        // Stop any running mic pulse so its animator doesn't outlive the view.
        micAnimations.stopMicPulse(btnMic);
        if (tts != null) {
            tts.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }

    /** Eases the status line back to its resting state after an error, unless we're listening. */
    private void recoverFromError() {
        if (isListening) {
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
     * Crossfades a status TextView to new text: fade out, swap the text, fade back in.
     * Replaces the previous abrupt {@code setText} swaps so state changes read smoothly.
     */
    private void fadeSwap(TextView view, CharSequence text) {
        if (view == null) {
            return;
        }
        view.animate()
                .alpha(0f)
                .setDuration(TEXT_FADE_OUT_MS)
                .withEndAction(() -> {
                    view.setText(text);
                    view.animate().alpha(1f).setDuration(TEXT_FADE_IN_MS).start();
                })
                .start();
    }

    /**
     * Drives the atom core's energy level and the surrounding glow alpha to reflect
     * the current app state (idle / listening / thinking). The core eases its own
     * energy internally and the glow alpha is animated, so transitions between states
     * feel continuous rather than stepped.
     */
    private void applyCoreState(float energy, float glowAlpha) {
        currentEnergy = energy;
        currentGlow = glowAlpha;
        if (atomCore != null) {
            atomCore.setEnergy(energy);
        }
        if (coreGlow != null) {
            coreGlow.animate().alpha(glowAlpha).setDuration(CORE_GLOW_ANIM_MS).start();
        }
    }

    private void setupObservers() {
        // When the back-end responds
        viewModel.getChatResponse().observe(this, response -> {
            fadeSwap(statusText, response);
            fadeSwap(subStatusText, getString(R.string.sub_status_responded));
            // Reply landed: ease the core back to its calm idle with a settle pulse.
            applyCoreState(CORE_ENERGY_IDLE, CORE_GLOW_IDLE);
            // Speak the assistant reply aloud when enabled in Settings.
            if (preferences.isTtsEnabled()) {
                tts.speak(response);
            }
        });

        // When waiting for the back-end
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading) {
                fadeSwap(statusText, getString(R.string.status_thinking));
                fadeSwap(subStatusText, getString(R.string.sub_status_thinking));
                applyCoreState(CORE_ENERGY_THINKING, CORE_GLOW_ACTIVE);
            }
        });

        // When something goes wrong
        viewModel.getErrorMessage().observe(this, error -> {
            fadeSwap(statusText, getString(R.string.status_error));
            fadeSwap(subStatusText,
                    error != null ? error.toUpperCase() : getString(R.string.status_error));
            applyCoreState(CORE_ENERGY_IDLE, CORE_GLOW_IDLE);
            // Don't leave the error on screen: ease back to idle after a short delay.
            statusText.removeCallbacks(errorRecoverRunnable);
            statusText.postDelayed(errorRecoverRunnable, ERROR_AUTO_RECOVER_MS);
        });

        // Sensitive actions (call, message) require explicit confirmation.
        viewModel.getPendingConfirmation().observe(this, this::confirmAction);
    }

    /** Asks the user to confirm a sensitive action before executing it. */
    private void confirmAction(ResolvedAction action) {
        if (action == null) {
            return;
        }
        String prompt = action.outMessage().isEmpty()
                ? getString(R.string.action_confirm_default)
                : action.outMessage();
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_confirm_title)
                .setMessage(prompt)
                .setPositiveButton(R.string.action_confirm_yes, (d, w) -> executeWithPermission(action))
                .setNegativeButton(R.string.action_confirm_no, null)
                .show();
    }

    /** Run the action, first requesting its runtime permission if one is missing. */
    private void executeWithPermission(ResolvedAction action) {
        String permission = PermissionCoordinator.requiredPermission(action);
        if (permission == null || PermissionCoordinator.isGranted(this, permission)) {
            viewModel.runAction(action);
            return;
        }
        awaitingPermission = action;
        permissionLauncher.launch(permission);
    }
}

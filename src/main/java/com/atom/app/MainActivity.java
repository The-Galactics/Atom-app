package com.atom.app;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.airbnb.lottie.LottieAnimationView;
import com.atom.app.di.AppContainer;
import com.atom.app.viewmodel.ChatViewModel;
import com.atom.app.viewmodel.ChatViewModelFactory;

public class MainActivity extends AppCompatActivity {

    private LottieAnimationView atomCore;
    private ImageButton btnMic, btnSettings, btnHistory, btnKeyboard, btnVolume;
    private TextView statusText, subStatusText;
    private ChatViewModel viewModel;

    private LinearLayout inputBarRoot;
    private EditText inputEditText;
    private ImageButton inputSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize ViewModel via the composition root (AppContainer).
        AppContainer appContainer = ((AtomApp) getApplication()).getAppContainer();
        viewModel = new ViewModelProvider(this, new ChatViewModelFactory(appContainer))
                .get(ChatViewModel.class);

        // Initialize UI Components
        atomCore = findViewById(R.id.atom_core_animation);
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

        setupObservers();
        setupInputBar();

        // FUTURE WORK: capture real microphone audio; until then this sends a fixed prompt.
        btnMic.setOnClickListener(v ->
                viewModel.sendMessage("Hello Atom, can you help me?"));

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        // FUTURE WORK: no conversation store or history screen yet; this is a placeholder.
        btnHistory.setOnClickListener(v ->
                toast("History is not available yet"));

        btnKeyboard.setOnClickListener(v -> toggleInputBar());

        btnVolume.setOnClickListener(v -> showVolumeSlider());
    }

    private void setupInputBar() {
        // Accent focus border: activate the pill background's focused state.
        inputEditText.setOnFocusChangeListener((v, hasFocus) ->
                inputBarRoot.setActivated(hasFocus));

        // Send via the trailing accent disc.
        inputSend.setOnClickListener(v -> sendFromInputBar());

        // IME "Send" action mirrors the send button.
        inputEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendFromInputBar();
                return true;
            }
            return false;
        });
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
        inputBarRoot.setVisibility(View.VISIBLE);
        inputEditText.requestFocus();
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(inputEditText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideInputBar() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(inputEditText.getWindowToken(), 0);
        }
        inputEditText.clearFocus();
        inputBarRoot.setVisibility(View.GONE);
    }

    /** Validates and dispatches the typed message, then collapses the bar. */
    private void sendFromInputBar() {
        String text = inputEditText.getText().toString().trim();
        if (text.isEmpty()) {
            toast(getString(R.string.input_empty));
            return;
        }
        viewModel.sendMessage(text);
        inputEditText.setText("");
        hideInputBar();
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void setupObservers() {
        // When the back-end responds
        viewModel.getChatResponse().observe(this, response -> {
            statusText.setText(response);
            subStatusText.setText(R.string.sub_status_responded);
        });

        // When waiting for the back-end
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading) {
                statusText.setText(R.string.status_thinking);
                subStatusText.setText(R.string.sub_status_thinking);
            }
        });

        // When something goes wrong
        viewModel.getErrorMessage().observe(this, error -> {
            statusText.setText(R.string.status_error);
            subStatusText.setText(error != null ? error.toUpperCase() : getString(R.string.status_error));
        });
    }
}

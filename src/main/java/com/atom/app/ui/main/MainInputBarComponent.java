package com.atom.app.ui.main;

import android.content.Context;
import android.graphics.Rect;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.atom.app.R;
import com.atom.app.ui.InputBarUtils;

public final class MainInputBarComponent {

    public interface Host {
        /** Dispatch a submitted message (host stops TTS + sends the order). */
        void onSubmitText(String text);
        /** Show a short toast. */
        void toast(String message);
    }

    // Input bar slide-in/out timing and the fallback travel before first layout.
    private static final long INPUT_BAR_ANIM_MS = 200;
    private static final float INPUT_BAR_FALLBACK_SLIDE_DP = 64f;

    private final AppCompatActivity activity;
    private final Host host;

    private final LinearLayout inputBarRoot;
    private final EditText inputEditText;
    private final ImageButton inputSend;

    // Reusable Rect for touch bounds checking in dismissIfTouchOutside, avoiding per-touch allocations.
    private final Rect touchBounds = new Rect();

    // Back press collapses the input bar instead of leaving the screen; only enabled while it's open.
    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            hide();
        }
    };

    public MainInputBarComponent(AppCompatActivity activity, Host host) {
        this.activity = activity;
        this.host = host;

        inputBarRoot = activity.findViewById(R.id.input_bar_root);
        inputEditText = activity.findViewById(R.id.input_edit_text);
        inputSend = activity.findViewById(R.id.input_send);

        setupInputBar();

        activity.getOnBackPressedDispatcher().addCallback(activity, backCallback);
    }

    private void setupInputBar() {
        // Accent focus border: activate the pill background's focused state.
        inputEditText.setOnFocusChangeListener((v, hasFocus) ->
                inputBarRoot.setActivated(hasFocus));

        // Send via the trailing accent disc.
        inputSend.setOnClickListener(v -> sendFromInputBar());

        // Keep send disabled/dimmed until there's non-whitespace text.
        InputBarUtils.setSendEnabled(inputSend, false);
        inputEditText.addTextChangedListener(InputBarUtils.enableSendOnText(inputSend));

        // IME "Send" action mirrors the send button.
        inputEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendFromInputBar();
                return true;
            }
            return false;
        });
    }

    public boolean isVisible() {
        return inputBarRoot.getVisibility() == View.VISIBLE;
    }

    public void toggle() {
        if (inputBarRoot.getVisibility() == View.VISIBLE) {
            hide();
        } else {
            show();
        }
    }

    public void show() {
        // Slide up + fade in instead of popping into place.
        inputBarRoot.setVisibility(View.VISIBLE);
        inputBarRoot.setAlpha(0f);
        inputBarRoot.setTranslationY(inputBarSlideDistance());
        inputBarRoot.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(INPUT_BAR_ANIM_MS)
                .start();
        backCallback.setEnabled(true);
        inputEditText.requestFocus();
        // Post the IME show to the next frame so the adjustResize layout pass (which lifts the
        // bar above the keyboard) settles independently of the slide-in, avoiding a first-open
        // double-move where the resting position shifts mid-animation.
        inputEditText.post(() -> {
            InputMethodManager imm =
                    (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(inputEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    public void hide() {
        InputMethodManager imm =
                (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(inputEditText.getWindowToken(), 0);
        }
        inputEditText.clearFocus();
        backCallback.setEnabled(false);
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
        return INPUT_BAR_FALLBACK_SLIDE_DP * activity.getResources().getDisplayMetrics().density;
    }

    /** Validates and dispatches the typed message, then collapses the bar. */
    private void sendFromInputBar() {
        String text = inputEditText.getText().toString().trim();
        if (text.isEmpty()) {
            host.toast(activity.getString(R.string.input_empty));
            return;
        }
        inputSend.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        host.onSubmitText(text);
        inputEditText.setText("");
        hide();
    }

    /** Opens the input bar pre-filled with a starter phrase, caret at the end. */
    public void prefill(String starter) {
        show();
        inputEditText.setText(starter);
        inputEditText.setSelection(inputEditText.getText().length());
    }

    public void setInputEnabled(boolean enabled) {
        inputEditText.setEnabled(enabled);
    }

    /**
     * On ACTION_DOWN outside the visible bar, hide it. Call from dispatchTouchEvent.
     */
    public void dismissIfTouchOutside(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN
                && inputBarRoot != null
                && inputBarRoot.getVisibility() == View.VISIBLE) {
            inputBarRoot.getGlobalVisibleRect(touchBounds);
            if (!touchBounds.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                hide();
            }
        }
    }
}

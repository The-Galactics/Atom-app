package com.atom.app.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ImageButton;

/** Shared input-bar send-disc helpers used by the main screen and overlay panel. */
public final class InputBarUtils {

    private static final float SEND_DISABLED_ALPHA = 0.4f;

    private InputBarUtils() { }

    /** Enables or dims a send disc based on whether there is text to send. */
    public static void setSendEnabled(ImageButton send, boolean enabled) {
        send.setEnabled(enabled);
        send.setAlpha(enabled ? 1f : SEND_DISABLED_ALPHA);
    }

    /** A TextWatcher that toggles the send disc whenever the field has non-blank text. */
    public static TextWatcher enableSendOnText(ImageButton send) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                setSendEnabled(send, s.toString().trim().length() > 0);
            }
            @Override public void afterTextChanged(Editable s) { }
        };
    }
}

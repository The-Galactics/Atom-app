package com.atom.app.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.atom.app.AtomApp;
import com.atom.app.R;
import com.atom.app.model.ResponseModel;
import com.atom.app.repository.ChatRepository;
import com.atom.infrastructure.adapter.voice.AndroidSpeechRecognizer;

public class FloatingBubbleService extends Service implements AtomApp.ForegroundListener {

    public static final String ACTION_START = "com.atom.app.overlay.START";
    public static final String ACTION_STOP = "com.atom.app.overlay.STOP";
    public static final String ACTION_SHOW = "com.atom.app.overlay.SHOW";

    private static final int NOTIFICATION_ID = 0xA70;
    private static final String CHANNEL_ID = "atom_floating_assistant";

    // Motion tuning for the bubble + mic. Kept in code (behaviour, not layout).
    private static final float MIC_PULSE_SCALE = 1.18f;     // peak of the breathing pulse
    private static final long MIC_PULSE_DURATION_MS = 620;  // one half-cycle (grows, then reverses)
    private static final long MIC_PRESS_DURATION_MS = 90;   // tap settle dip
    private static final long SNAP_DURATION_MS = 220;       // edge snap glide
    private static final float PUSH_OFF_FRACTION = 0.4f;    // drag this far past an edge to hide
    private static final float HANDLE_IDLE_ALPHA = 0.5f;    // dimmed handle when untouched

    private WindowManager windowManager;
    private LayoutInflater inflater;

    private View bubbleView;          // collapsed disc
    private View panelView;           // expanded input panel
    private WindowManager.LayoutParams bubbleParams;

    private ChatRepository chatRepository;
    private AndroidSpeechRecognizer speechRecognizer;
    private int touchSlop;

    private ObjectAnimator micPulse;  // infinite "listening" pulse on the panel mic
    private ValueAnimator bubbleSettle; // edge snap glide

    private View handleView;          // edge tab shown while the bubble is hidden
    private WindowManager.LayoutParams handleParams;
    private ValueAnimator handleSettle; // handle edge snap / fade-to-idle glide
    private boolean collapsedToHandle; // bubble is tucked away to the edge handle
    private boolean lastBubbleOnLeft;  // which edge the bubble last rested on
    private int lastBubbleY = -1;      // last bubble Y, reused to place the handle

    private AtomApp app;
    private boolean overlayEnabled; // set between START and STOP

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        inflater = LayoutInflater.from(this);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        app = (AtomApp) getApplication();
        chatRepository = new ChatRepository(
                app.getAppContainer().getExternalMessageUseCase(),
                app.getAppContainer().getSessionUserId(),
                app.getAppContainer().getSessionChatId());
        commandRepository = new CommandRepository(
                app.getAppContainer().getExternalCommandUseCase(),
                app.getAppContainer().getActionExecutor());
        app.setForegroundListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) {
            stopOverlay();
            return START_NOT_STICKY;
        }
        overlayEnabled = true;
        if (ACTION_SHOW.equals(action)) {
            // Brought back from the notification after a hide.
            startForegroundWithNotification();
            restoreBubble();
            return START_STICKY;
        }
        startForegroundWithNotification();
        // Avoid covering our own UI: show only if the app is already backgrounded.
        if (!app.isAppInForeground()) {
            showBubble();
        }
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Rotation / screen-size change: re-dock whatever is showing to the new bounds
        // so it can never end up stranded off the visible area.
        repositionForBounds();
    }

    // Re-pins the bubble or handle to its edge and clamps it within the current screen.
    private void repositionForBounds() {
        int[] screen = getScreenSize();
        if (bubbleView != null && bubbleParams != null) {
            int bubbleWidth = bubbleSpan(bubbleView.getWidth());
            int edgeMargin = getResources().getDimensionPixelSize(R.dimen.bubble_edge_margin);
            bubbleParams.x = lastBubbleOnLeft
                    ? edgeMargin : screen[0] - bubbleWidth - edgeMargin;
            bubbleParams.y = Math.max(0, Math.min(bubbleParams.y, screen[1] - bubbleWidth));
            lastBubbleY = bubbleParams.y;
            windowManager.updateViewLayout(bubbleView, bubbleParams);
        } else if (handleView != null && handleParams != null) {
            int handleWidth = handleSpan(handleView.getWidth());
            int handleHeight = getResources().getDimensionPixelSize(R.dimen.handle_height);
            handleParams.x = lastBubbleOnLeft ? 0 : screen[0] - handleWidth;
            handleParams.y = Math.max(0, Math.min(handleParams.y, screen[1] - handleHeight));
            lastBubbleY = handleParams.y;
            windowManager.updateViewLayout(handleView, handleParams);
        }
    }

    @Override
    public void onAppForeground() {
        hideOverlayViews();
    }

    @Override
    public void onAppBackground() {
        if (!overlayEnabled
                || bubbleView != null || panelView != null || handleView != null) {
            return;
        }
        // Come back in whichever state the user left us: tucked to the handle, or open.
        if (collapsedToHandle) {
            showHandle();
        } else {
            showBubble();
        }
    }

    private void hideOverlayViews() {
        cancelAnimations();
        removeView(panelView);
        panelView = null;
        removeView(bubbleView);
        bubbleView = null;
        removeView(handleView);
        handleView = null;
    }

    private void startForegroundWithNotification() {
        ensureNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification(false));
    }

    private void ensureNotificationChannel() {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null
                && nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.overlay_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }
    }

    // The ongoing notification doubles as the way back from a hide: tapping it
    // (or the "Show" action) re-displays the bubble; "Turn off" stops the overlay.
    private Notification buildNotification(boolean hidden) {
        int flag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0;
        Intent showIntent = new Intent(this, FloatingBubbleService.class).setAction(ACTION_SHOW);
        PendingIntent showPending = PendingIntent.getService(this, 1, showIntent, flag);
        Intent stopIntent = new Intent(this, FloatingBubbleService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent, flag);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.overlay_notification_title))
                .setContentText(getString(hidden
                        ? R.string.overlay_notification_text_hidden
                        : R.string.overlay_notification_text))
                .setSmallIcon(R.drawable.ic_mic)
                .setOngoing(true)
                .setContentIntent(showPending);
        if (hidden) {
            builder.addAction(R.drawable.ic_atom_glyph,
                    getString(R.string.overlay_action_show), showPending);
        }
        builder.addAction(R.drawable.ic_close,
                getString(R.string.overlay_action_stop), stopPending);
        return builder.build();
    }

    private void updateNotification(boolean hidden) {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(hidden));
        }
    }

    // Tucks the overlay away to a small arrow tab pinned to the screen edge, so it
    // stops covering other apps without disappearing. Tapping the tab brings it back.
    private void hideToHandle() {
        int[] screen = getScreenSize();
        if (bubbleView != null) {
            int bubbleWidth = bubbleSpan(bubbleView.getWidth());
            lastBubbleOnLeft = (bubbleParams.x + bubbleWidth / 2) < screen[0] / 2;
            lastBubbleY = bubbleParams.y;
        }
        cancelAnimations();
        removeView(panelView);
        panelView = null;
        removeView(bubbleView);
        bubbleView = null;
        collapsedToHandle = true;
        showHandle();
        updateNotification(true);
    }

    // The edge handle: a thin glowing line, dimmed until touched, docked to the edge.
    // The touchable window is the wider transparent grab zone, so dock against its width.
    private void showHandle() {
        if (handleView != null) {
            return;
        }
        int[] screen = getScreenSize();
        int handleWidth = getResources().getDimensionPixelSize(R.dimen.handle_touch_width);
        int handleHeight = getResources().getDimensionPixelSize(R.dimen.handle_height);

        handleView = inflater.inflate(R.layout.view_overlay_handle, null);
        applyHandleSide(lastBubbleOnLeft);
        handleView.setOnTouchListener(new HandleTouchListener());
        // Dimmed while untouched, like a quiet edge marker; full opacity on touch.
        handleView.setAlpha(HANDLE_IDLE_ALPHA);

        handleParams = baseLayoutParams();
        handleParams.gravity = Gravity.TOP | Gravity.START;
        handleParams.x = lastBubbleOnLeft ? 0 : screen[0] - handleWidth;
        int y = lastBubbleY >= 0 ? lastBubbleY : 240;
        handleParams.y = Math.max(0, Math.min(y, screen[1] - handleHeight));
        windowManager.addView(handleView, handleParams);
    }

    // Pins the visible line to the docked border inside the transparent grab zone, so
    // the line always hugs the screen edge whether docked left or right.
    private void applyHandleSide(boolean onLeft) {
        if (handleView == null) {
            return;
        }
        View bar = handleView.findViewById(R.id.handle_bar);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
        lp.gravity = (onLeft ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL;
        bar.setLayoutParams(lp);
    }

    // The handle drags around the screen like the bubble; a tap (no drag) restores it.
    private final class HandleTouchListener implements View.OnTouchListener {
        private int initialX, initialY;
        private float touchX, touchY;
        private boolean dragging;

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    cancelHandleSettle();
                    handleView.setAlpha(1f);
                    initialX = handleParams.x;
                    initialY = handleParams.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - touchX);
                    int dy = (int) (event.getRawY() - touchY);
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        dragging = true;
                    }
                    handleParams.x = initialX + dx;
                    handleParams.y = initialY + dy;
                    windowManager.updateViewLayout(handleView, handleParams);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        settleHandleToEdge();
                    } else {
                        v.performClick();
                        restoreBubble();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    // Gesture stolen (e.g. system edge-swipe). Don't leave the grip lit
                    // and half-dragged: settle if we were moving, else fade back to idle.
                    if (dragging) {
                        settleHandleToEdge();
                    } else {
                        animateHandleTo(handleParams.x, handleParams.y, HANDLE_IDLE_ALPHA);
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    // Glide the handle flush to the nearest edge, then fade it back to its idle dim.
    private void settleHandleToEdge() {
        if (handleView == null) {
            return;
        }
        int[] screen = getScreenSize();
        // Use the live measured width, not the dimen: the right-edge target must be
        // computed against what is actually on screen, or the grip lands short of the
        // border instead of flush against it.
        int handleWidth = handleSpan(handleView.getWidth());
        int handleHeight = getResources().getDimensionPixelSize(R.dimen.handle_height);

        boolean toLeft = (handleParams.x + handleWidth / 2) < screen[0] / 2;
        lastBubbleOnLeft = toLeft;
        applyHandleSide(toLeft);

        int targetX = toLeft ? 0 : screen[0] - handleWidth;
        int targetY = Math.max(0, Math.min(handleParams.y, screen[1] - handleHeight));
        lastBubbleY = targetY;
        animateHandleTo(targetX, targetY, HANDLE_IDLE_ALPHA);
    }

    private void animateHandleTo(int toX, int toY, float toAlpha) {
        if (handleView == null) {
            return;
        }
        cancelHandleSettle();
        final int fromX = handleParams.x;
        final int fromY = handleParams.y;
        final float fromAlpha = handleView.getAlpha();
        handleSettle = ValueAnimator.ofFloat(0f, 1f);
        handleSettle.setDuration(SNAP_DURATION_MS);
        handleSettle.setInterpolator(new DecelerateInterpolator());
        handleSettle.addUpdateListener(animation -> {
            if (handleView == null || !handleView.isAttachedToWindow()) {
                return;
            }
            float fraction = (float) animation.getAnimatedValue();
            handleParams.x = Math.round(fromX + (toX - fromX) * fraction);
            handleParams.y = Math.round(fromY + (toY - fromY) * fraction);
            handleView.setAlpha(fromAlpha + (toAlpha - fromAlpha) * fraction);
            windowManager.updateViewLayout(handleView, handleParams);
        });
        handleSettle.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Guarantee the final docked spot even if the system skipped frames
                // (animator duration scale 0 / battery saver) — otherwise it floats.
                if (handleView != null && handleView.isAttachedToWindow()) {
                    handleParams.x = toX;
                    handleParams.y = toY;
                    handleView.setAlpha(toAlpha);
                    windowManager.updateViewLayout(handleView, handleParams);
                }
            }
        });
        handleSettle.start();
    }

    private void cancelHandleSettle() {
        if (handleSettle != null) {
            handleSettle.cancel();
            handleSettle = null;
        }
    }

    // Removes the edge tab and re-displays the bubble where it was tucked away.
    private void restoreBubble() {
        removeView(handleView);
        handleView = null;
        handleParams = null;
        collapsedToHandle = false;
        showBubbleAt(lastBubbleOnLeft, lastBubbleY >= 0 ? lastBubbleY : 240);
        updateNotification(false);
    }

    private void showBubble() {
        // Restore the last resting spot, or the default left-edge anchor on first show.
        boolean onLeft = lastBubbleY < 0 || lastBubbleOnLeft;
        int y = lastBubbleY >= 0 ? lastBubbleY : 240;
        showBubbleAt(onLeft, y);
    }

    private void showBubbleAt(boolean onLeft, int y) {
        if (bubbleView != null) {
            return;
        }
        int[] screen = getScreenSize();
        int bubbleWidth = getResources().getDimensionPixelSize(R.dimen.bubble_size);
        int edgeMargin = getResources().getDimensionPixelSize(R.dimen.bubble_edge_margin);

        bubbleView = inflater.inflate(R.layout.view_overlay_bubble, null);
        bubbleParams = baseLayoutParams();
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = onLeft ? edgeMargin : screen[0] - bubbleWidth - edgeMargin;
        bubbleParams.y = Math.max(0, Math.min(y, screen[1] - bubbleWidth));
        lastBubbleOnLeft = onLeft;
        lastBubbleY = bubbleParams.y;

        bubbleView.setOnTouchListener(new BubbleTouchListener());
        windowManager.addView(bubbleView, bubbleParams);
    }

    private final class BubbleTouchListener implements View.OnTouchListener {
        private int initialX, initialY;
        private float touchX, touchY;
        private boolean dragging;

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // A fresh touch wins over any running snap.
                    cancelBubbleSettle();
                    initialX = bubbleParams.x;
                    initialY = bubbleParams.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - touchX);
                    int dy = (int) (event.getRawY() - touchY);
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        dragging = true;
                    }
                    bubbleParams.x = initialX + dx;
                    bubbleParams.y = initialY + dy;
                    windowManager.updateViewLayout(bubbleView, bubbleParams);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) {
                        settleToEdge();
                    } else {
                        v.performClick();
                        expandPanel();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    // Gesture stolen (common with edge gesture-nav on tall phones):
                    // settle to the edge instead of leaving the bubble stranded.
                    if (dragging) {
                        settleToEdge();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private void expandPanel() {
        if (panelView != null) {
            return;
        }
        removeView(bubbleView);
        bubbleView = null;

        panelView = inflater.inflate(R.layout.view_overlay_panel, null);
        WindowManager.LayoutParams params = baseLayoutParams();
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.BOTTOM;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        final EditText editText = panelView.findViewById(R.id.overlay_edit_text);
        final TextView status = panelView.findViewById(R.id.overlay_status);
        final View inputRoot = panelView.findViewById(R.id.overlay_input_root);
        ImageButton send = panelView.findViewById(R.id.overlay_send);
        ImageButton mic = panelView.findViewById(R.id.overlay_mic);
        ImageButton close = panelView.findViewById(R.id.overlay_close);
        ImageButton hide = panelView.findViewById(R.id.overlay_hide);

        editText.setOnFocusChangeListener((view, hasFocus) -> inputRoot.setActivated(hasFocus));

        send.setOnClickListener(v -> dispatchPrompt(editText, status));
        editText.setOnEditorActionListener((view, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                dispatchPrompt(editText, status);
                return true;
            }
            return false;
        });

        // Tap the mic to dictate: capture speech on-device, then send the transcript.
        mic.setOnClickListener(v -> startVoiceCapture(status));

        close.setOnClickListener(v -> collapseToBubble());
        // Tuck the overlay away to the edge handle while using other apps.
        hide.setOnClickListener(v -> hideToHandle());

        windowManager.addView(panelView, params);
        editText.requestFocus();
    }

    private void dispatchPrompt(EditText editText, TextView status) {
        String text = editText.getText().toString().trim();
        if (text.isEmpty()) {
            status.setText(R.string.input_empty);
            return;
        }
        status.setText(R.string.overlay_sending);
        editText.setText("");
        askAtom(text, status, null);
    }

    private void askAtom(String prompt, TextView status, View mic) {
        chatRepository.askAtom(prompt, new ChatRepository.ChatCallback() {
            @Override
            public void onSuccess(ResponseModel response) {
                stopMicPulse(mic);
                if (status.isAttachedToWindow()) {
                    status.setText(response.getResponseText());
                }
            }

            @Override
            public void onError(String error) {
                stopMicPulse(mic);
                if (status.isAttachedToWindow()) {
                    status.setText(error);
                }
            }
        });
    }

    private void collapseToBubble() {
        stopMicPulse(null);
        removeView(panelView);
        panelView = null;
        showBubble();
    }

    // --- Mic animation (Phase 2) -------------------------------------------

    // A quick scale dip-and-recover that acknowledges the tap.
    private void playPressSettle(View mic) {
        if (mic == null) {
            return;
        }
        mic.animate()
                .scaleX(0.86f).scaleY(0.86f)
                .setDuration(MIC_PRESS_DURATION_MS)
                .withEndAction(() -> mic.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(MIC_PRESS_DURATION_MS + 30)
                        .start())
                .start();
    }

    // Infinite "breathing" pulse (scale + alpha) marking the listening state.
    private void startMicPulse(View mic) {
        if (mic == null) {
            return;
        }
        stopMicPulse(mic);
        PropertyValuesHolder scaleX =
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, MIC_PULSE_SCALE);
        PropertyValuesHolder scaleY =
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, MIC_PULSE_SCALE);
        PropertyValuesHolder alpha =
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.55f);
        micPulse = ObjectAnimator.ofPropertyValuesHolder(mic, scaleX, scaleY, alpha);
        micPulse.setDuration(MIC_PULSE_DURATION_MS);
        micPulse.setRepeatCount(ValueAnimator.INFINITE);
        micPulse.setRepeatMode(ValueAnimator.REVERSE);
        micPulse.setInterpolator(new AccelerateDecelerateInterpolator());
        micPulse.start();
    }

    // Stops the pulse and restores the mic to its resting state. The view may
    // already be detached (panel torn down mid-request); resetting it is safe.
    private void stopMicPulse(View mic) {
        if (micPulse != null) {
            micPulse.cancel();
            micPulse = null;
        }
        if (mic != null) {
            mic.setScaleX(1f);
            mic.setScaleY(1f);
            mic.setAlpha(1f);
        }
    }

    // --- Edge snap & force-to-hide (Phase 3) -------------------------------

    // Called after a drag: glide flush to the nearest edge, or hide the overlay
    // entirely when the bubble was forced well past that edge.
    private void settleToEdge() {
        if (bubbleView == null) {
            return;
        }
        int[] screen = getScreenSize();
        int screenWidth = screen[0];
        int screenHeight = screen[1];
        int bubbleWidth = bubbleSpan(bubbleView.getWidth());
        int bubbleHeight = bubbleSpan(bubbleView.getHeight());
        int edgeMargin = getResources().getDimensionPixelSize(R.dimen.bubble_edge_margin);

        boolean toLeft = (bubbleParams.x + bubbleWidth / 2) < screenWidth / 2;
        int pushThreshold = Math.round(bubbleWidth * PUSH_OFF_FRACTION);
        boolean forcedOff = toLeft
                ? bubbleParams.x < -pushThreshold
                : bubbleParams.x > screenWidth - bubbleWidth + pushThreshold;
        if (forcedOff) {
            // Shoved hard into the border: tuck it to the edge handle.
            hideToHandle();
            return;
        }

        int targetX = toLeft ? edgeMargin : screenWidth - bubbleWidth - edgeMargin;
        int targetY = Math.max(0, Math.min(bubbleParams.y, screenHeight - bubbleHeight));
        lastBubbleOnLeft = toLeft;
        lastBubbleY = targetY;
        animateBubbleTo(targetX, targetY);
    }

    // Interpolates the bubble window position via the WindowManager.
    private void animateBubbleTo(int toX, int toY) {
        if (bubbleView == null) {
            return;
        }
        cancelBubbleSettle();
        final int fromX = bubbleParams.x;
        final int fromY = bubbleParams.y;
        bubbleSettle = ValueAnimator.ofFloat(0f, 1f);
        bubbleSettle.setDuration(SNAP_DURATION_MS);
        bubbleSettle.setInterpolator(new DecelerateInterpolator());
        bubbleSettle.addUpdateListener(animation -> {
            if (bubbleView == null || !bubbleView.isAttachedToWindow()) {
                return;
            }
            float fraction = (float) animation.getAnimatedValue();
            bubbleParams.x = Math.round(fromX + (toX - fromX) * fraction);
            bubbleParams.y = Math.round(fromY + (toY - fromY) * fraction);
            windowManager.updateViewLayout(bubbleView, bubbleParams);
        });
        bubbleSettle.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Guarantee the final docked spot even if the system skipped frames
                // (animator duration scale 0 / battery saver) — otherwise it floats.
                if (bubbleView != null && bubbleView.isAttachedToWindow()) {
                    bubbleParams.x = toX;
                    bubbleParams.y = toY;
                    windowManager.updateViewLayout(bubbleView, bubbleParams);
                }
            }
        });
        bubbleSettle.start();
    }

    // Bubble dimension fallback for the rare frame where the view is unmeasured.
    private int bubbleSpan(int measured) {
        return measured > 0 ? measured : getResources().getDimensionPixelSize(R.dimen.bubble_size);
    }

    // Handle window-width fallback for the rare frame where the view is unmeasured.
    // This is the transparent grab-zone width (the window span), not the visible line.
    private int handleSpan(int measured) {
        return measured > 0
                ? measured
                : getResources().getDimensionPixelSize(R.dimen.handle_touch_width);
    }

    @SuppressWarnings("deprecation")
    private int[] getScreenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();
            return new int[]{bounds.width(), bounds.height()};
        }
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        return new int[]{dm.widthPixels, dm.heightPixels};
    }

    private void cancelBubbleSettle() {
        if (bubbleSettle != null) {
            bubbleSettle.cancel();
            bubbleSettle = null;
        }
    }

    private void cancelAnimations() {
        cancelBubbleSettle();
        cancelHandleSettle();
        stopMicPulse(null);
    }

    private WindowManager.LayoutParams baseLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
    }

    private void removeView(View view) {
        if (view != null && view.isAttachedToWindow()) {
            windowManager.removeView(view);
        }
    }

    private void stopOverlay() {
        overlayEnabled = false;
        collapsedToHandle = false;
        cancelAnimations();
        removeView(bubbleView);
        removeView(panelView);
        removeView(handleView);
        bubbleView = null;
        panelView = null;
        handleView = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (app != null) {
            app.clearForegroundListener(this);
        }
        cancelAnimations();
        removeView(bubbleView);
        removeView(panelView);
        removeView(handleView);
        bubbleView = null;
        panelView = null;
        handleView = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

package com.atom.app.overlay;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.atom.app.AtomApp;
import com.atom.app.R;
import com.atom.app.model.ResponseModel;
import com.atom.app.repository.ChatRepository;

public class FloatingBubbleService extends Service implements AtomApp.ForegroundListener {

    public static final String ACTION_START = "com.atom.app.overlay.START";
    public static final String ACTION_STOP = "com.atom.app.overlay.STOP";

    private static final int NOTIFICATION_ID = 0xA70;
    private static final String CHANNEL_ID = "atom_floating_assistant";

    private WindowManager windowManager;
    private LayoutInflater inflater;

    private View bubbleView;          // collapsed disc
    private View panelView;           // expanded input panel
    private WindowManager.LayoutParams bubbleParams;

    private ChatRepository chatRepository;
    private int touchSlop;

    private AtomApp app;
    private boolean overlayEnabled; // set between START and STOP

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        inflater = LayoutInflater.from(this);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        app = (AtomApp) getApplication();
        chatRepository = new ChatRepository(app.getAppContainer().getExternalMessageUseCase());
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
        startForegroundWithNotification();
        // Avoid covering our own UI: show only if the app is already backgrounded.
        if (!app.isAppInForeground()) {
            showBubble();
        }
        return START_STICKY;
    }

    @Override
    public void onAppForeground() {
        hideOverlayViews();
    }

    @Override
    public void onAppBackground() {
        if (overlayEnabled && bubbleView == null && panelView == null) {
            showBubble();
        }
    }

    private void hideOverlayViews() {
        removeView(panelView);
        panelView = null;
        removeView(bubbleView);
        bubbleView = null;
    }

    private void startForegroundWithNotification() {
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

        Intent stopIntent = new Intent(this, FloatingBubbleService.class).setAction(ACTION_STOP);
        int flag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent, flag);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.overlay_notification_title))
                .setContentText(getString(R.string.overlay_notification_text))
                .setSmallIcon(R.drawable.ic_mic)
                .setOngoing(true)
                .setContentIntent(stopPending)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void showBubble() {
        if (bubbleView != null) {
            return;
        }
        bubbleView = inflater.inflate(R.layout.view_overlay_bubble, null);
        bubbleParams = baseLayoutParams();
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 24;
        bubbleParams.y = 240;

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
                    if (!dragging) {
                        v.performClick();
                        expandPanel();
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

        editText.setOnFocusChangeListener((view, hasFocus) -> inputRoot.setActivated(hasFocus));

        send.setOnClickListener(v -> dispatchPrompt(editText, status));
        editText.setOnEditorActionListener((view, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                dispatchPrompt(editText, status);
                return true;
            }
            return false;
        });

        // FUTURE WORK: real voice capture; for now the mic fires a smoke-test prompt.
        mic.setOnClickListener(v -> {
            status.setText(R.string.overlay_sending);
            askAtom("Hello Atom, can you help me?", status);
        });

        close.setOnClickListener(v -> collapseToBubble());

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
        askAtom(text, status);
    }

    private void askAtom(String prompt, TextView status) {
        chatRepository.askAtom(prompt, new ChatRepository.ChatCallback() {
            @Override
            public void onSuccess(ResponseModel response) {
                if (status.isAttachedToWindow()) {
                    status.setText(response.getResponseText());
                }
            }

            @Override
            public void onError(String error) {
                if (status.isAttachedToWindow()) {
                    status.setText(error);
                }
            }
        });
    }

    private void collapseToBubble() {
        removeView(panelView);
        panelView = null;
        showBubble();
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
        removeView(bubbleView);
        removeView(panelView);
        bubbleView = null;
        panelView = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (app != null) {
            app.clearForegroundListener(this);
        }
        removeView(bubbleView);
        removeView(panelView);
        bubbleView = null;
        panelView = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

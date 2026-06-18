package com.atom.infrastructure.adapter.action;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.util.Log;

import com.atom.app.R;
import com.atom.application.port.out.ActionExecutorPortOut;
import com.atom.domain.action.ActionOutcome;
import com.atom.domain.action.ResolvedAction;

import java.util.List;
import java.util.Locale;

/**
 * Android adapter for {@link ActionExecutorPortOut}: turns a {@link ResolvedAction}
 * into a concrete device action via one of two strategies per action:
 * plain Intents (OPEN_APP, MAKE_CALL, SET_ALARM, SET_TIMER, SMS) are fire-and-forget;
 * TOGGLE_SETTING uses system services (flashlight via {@link CameraManager}) or opens
 * the Settings panel, since apps cannot silently toggle radios on API 29+ and
 * do-not-disturb needs {@code ACCESS_NOTIFICATION_POLICY}.
 *
 * <p>Holds the application context, so Activity-bound launches add
 * {@link Intent#FLAG_ACTIVITY_NEW_TASK}.
 */
public class AndroidActionExecutor implements ActionExecutorPortOut {

    private static final String TAG = "AtomActionExec";

    private final Context appContext;

    public AndroidActionExecutor(Context context) {
        // Always hold the application context to avoid leaking an Activity.
        this.appContext = context.getApplicationContext();
    }

    @Override
    public ActionOutcome execute(ResolvedAction action) {
        if (action == null || !action.isExecutable()) {
            return ActionOutcome.ok("");
        }
        try {
            return switch (action.type()) {
                case OPEN_APP -> openApp(action.param("app_name"));
                case MAKE_CALL -> makeCall(action.param("target"));
                case SEND_MESSAGE -> sendMessage(action.param("recipient"), action.param("body"));
                case SET_ALARM -> setAlarm(action.param("time"), action.param("label"));
                case SET_TIMER -> setTimer(action.param("duration_seconds"), action.param("label"));
                case TOGGLE_SETTING -> toggleSetting(action.param("setting"), action.param("state"));
                default -> ActionOutcome.ok("");
            };
        } catch (Exception e) {
            Log.e(TAG, "Action execution failed: " + action.type(), e);
            return ActionOutcome.failed(appContext.getString(R.string.action_failed, e.getMessage()));
        }
    }

    // --- handlers -----------------------------------------------------------

    private ActionOutcome openApp(String appName) {
        if (isBlank(appName)) {
            return ActionOutcome.failed(appContext.getString(R.string.action_open_app_unknown));
        }
        String packageName = resolvePackage(appName);
        if (packageName == null) {
            return ActionOutcome.failed(
                    appContext.getString(R.string.action_open_app_not_found, appName));
        }
        Intent launch = appContext.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            return ActionOutcome.failed(
                    appContext.getString(R.string.action_open_app_failed, appName));
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(launch);
        return ActionOutcome.ok(appContext.getString(R.string.action_opening_app, appName));
    }

    /** Best-effort label/package fuzzy match against launchable apps. */
    private String resolvePackage(String appName) {
        String needle = appName.toLowerCase(Locale.ROOT).trim();
        PackageManager pm = appContext.getPackageManager();

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcherIntent, 0);

        String contains = null;
        for (ResolveInfo info : apps) {
            String label = String.valueOf(info.loadLabel(pm)).toLowerCase(Locale.ROOT);
            String pkg = info.activityInfo.packageName.toLowerCase(Locale.ROOT);
            if (label.equals(needle)) {
                return info.activityInfo.packageName; // exact label wins
            }
            if (contains == null && (label.contains(needle) || pkg.contains(needle))) {
                contains = info.activityInfo.packageName;
            }
        }
        return contains;
    }

    private ActionOutcome makeCall(String target) {
        if (isBlank(target)) {
            return ActionOutcome.failed(appContext.getString(R.string.action_call_unknown));
        }
        // Requires the CALL_PHONE runtime permission. Without it, fall back to
        // the dialer (ACTION_DIAL needs no permission) so the order still helps.
        boolean canCall = appContext.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
        String action = canCall ? Intent.ACTION_CALL : Intent.ACTION_DIAL;
        Intent intent = new Intent(action, Uri.parse("tel:" + Uri.encode(target)));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
        return ActionOutcome.ok(appContext.getString(R.string.action_calling, target));
    }

    private ActionOutcome sendMessage(String recipient, String body) {
        if (isBlank(recipient)) {
            return ActionOutcome.failed(appContext.getString(R.string.action_message_unknown));
        }
        // Open the SMS composer pre-filled (ACTION_SENDTO needs no permission;
        // the user taps send). SmsManager could send silently but needs SEND_SMS.
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(recipient)));
        if (!isBlank(body)) {
            intent.putExtra("sms_body", body);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
        return ActionOutcome.ok(appContext.getString(R.string.action_message_preparing, recipient));
    }

    private ActionOutcome setAlarm(String time, String label) {
        if (isBlank(time) || !time.contains(":")) {
            return ActionOutcome.failed(appContext.getString(R.string.action_alarm_unknown));
        }
        String[] parts = time.trim().split(":");
        int hour = Integer.parseInt(parts[0].trim());
        int minute = Integer.parseInt(parts[1].trim());

        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        if (!isBlank(label)) {
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
        return ActionOutcome.ok(appContext.getString(R.string.action_alarm_set, time));
    }

    private ActionOutcome setTimer(String durationSeconds, String label) {
        if (isBlank(durationSeconds)) {
            return ActionOutcome.failed(appContext.getString(R.string.action_timer_unknown));
        }
        int seconds = (int) Math.round(Double.parseDouble(durationSeconds.trim()));
        if (seconds <= 0) {
            return ActionOutcome.failed(appContext.getString(R.string.action_timer_invalid));
        }
        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        if (!isBlank(label)) {
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
        return ActionOutcome.ok(appContext.getString(R.string.action_timer_started));
    }

    private ActionOutcome toggleSetting(String setting, String state) {
        if (isBlank(setting)) {
            return ActionOutcome.failed(appContext.getString(R.string.action_setting_unknown));
        }
        boolean enable = !"off".equalsIgnoreCase(state); // "on"/"toggle"/null -> enable
        return switch (setting.toLowerCase(Locale.ROOT)) {
            case "flashlight" -> toggleFlashlight(enable);
            case "wifi" -> openSettingsPanel(panelOrFallback(Settings.Panel.ACTION_WIFI),
                    appContext.getString(R.string.setting_label_wifi));
            case "bluetooth" -> openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS,
                    appContext.getString(R.string.setting_label_bluetooth));
            case "do_not_disturb" -> openSettingsPanel(
                    Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS,
                    appContext.getString(R.string.setting_label_dnd));
            default -> ActionOutcome.failed(
                    appContext.getString(R.string.action_setting_unsupported, setting));
        };
    }

    private ActionOutcome toggleFlashlight(boolean enable) {
        CameraManager cameraManager = (CameraManager) appContext.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            return ActionOutcome.failed(appContext.getString(R.string.action_torch_unavailable));
        }
        try {
            for (String id : cameraManager.getCameraIdList()) {
                Boolean hasFlash = cameraManager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(hasFlash)) {
                    cameraManager.setTorchMode(id, enable);
                    return ActionOutcome.ok(appContext.getString(
                            enable ? R.string.action_torch_on : R.string.action_torch_off));
                }
            }
            return ActionOutcome.failed(appContext.getString(R.string.action_torch_none));
        } catch (Exception e) {
            return ActionOutcome.failed(
                    appContext.getString(R.string.action_torch_failed, e.getMessage()));
        }
    }

    /**
     * Apps can't silently flip radios on API 29+, so we surface the relevant
     * Settings panel/screen for a one-tap confirmation by the user.
     */
    private ActionOutcome openSettingsPanel(String action, String label) {
        Intent intent = new Intent(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
        return ActionOutcome.ok(appContext.getString(R.string.action_opening_settings, label));
    }

    private static String panelOrFallback(String panelAction) {
        // Settings.Panel.* exists from API 29; minSdk is 26, so guard it.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? panelAction
                : Settings.ACTION_WIFI_SETTINGS;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

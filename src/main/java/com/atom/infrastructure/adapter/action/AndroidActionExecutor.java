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
import com.atom.application.port.out.ContactResolverPortOut;
import com.atom.application.port.out.PhoneNumberNormalizerPortOut;
import com.atom.domain.contact.ContactResolution;
import com.atom.domain.action.ActionOutcome;
import com.atom.domain.action.ResolvedAction;
import com.atom.infrastructure.adapter.accessibility.AtomAccessibilityService;

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
    private final ContactResolverPortOut contactResolver;
    private final PhoneNumberNormalizerPortOut normalizer;

    public AndroidActionExecutor(Context context,
                                 ContactResolverPortOut contactResolver,
                                 PhoneNumberNormalizerPortOut normalizer) {
        // Always hold the application context to avoid leaking an Activity.
        this.appContext = context.getApplicationContext();
        this.contactResolver = contactResolver;
        this.normalizer = normalizer;
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
                case SEND_MESSAGE -> sendMessage(action.param("recipient"), action.param("body"), action.param("app"));
                case SET_ALARM -> setAlarm(action.param("time"), action.param("label"));
                case SET_TIMER -> setTimer(action.param("duration_seconds"), action.param("label"));
                case TOGGLE_SETTING -> toggleSetting(action.param("setting"), action.param("state"));
                case NAVIGATE -> navigate(action.param("direction"));
                case SCROLL -> scroll(action.param("direction"));
                case READ_SCREEN -> readScreen();
                case TAP_ELEMENT -> tapElement(action.param("text"));
                case TYPE_TEXT -> typeText(action.param("text"), action.param("submit"));
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

        String number = target;
        // A fuzzy contact match opens the dialer (pre-filled) so the user confirms
        // the resolved number instead of placing a direct call (2B.5).
        boolean forceDial = false;
        // A name (not a dialable string) is resolved against the device address
        // book first; numbers and dial codes pass straight through.
        if (!isDialable(target)) {
            boolean canReadContacts = appContext.checkSelfPermission(
                    android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
            if (!canReadContacts) {
                return ActionOutcome.failed(appContext.getString(R.string.action_permission_denied));
            }
            ContactResolution resolution = contactResolver.resolveContact(target);
            if (resolution.number().isEmpty()) {
                // NONE or AMBIGUOUS: never guess — ask the user to be more specific.
                return ActionOutcome.failed(appContext.getString(R.string.contact_not_found));
            }
            number = resolution.number().get();
            // Only an exact unique match may be dialed directly.
            forceDial = !resolution.isExact();
        }

        // ACTION_CALL needs CALL_PHONE; without it — or for a fuzzy match — fall back
        // to the dialer (ACTION_DIAL needs no permission) so the user confirms first.
        boolean canCall = appContext.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
        String action = chooseCallAction(canCall, !forceDial);
        Intent intent = new Intent(action, Uri.parse("tel:" + Uri.encode(number)));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
        return ActionOutcome.ok(appContext.getString(R.string.action_calling, number));
    }

    /**
     * Picks the call intent action: a direct {@link Intent#ACTION_CALL} only when the
     * app may call AND the contact match is exact; otherwise {@link Intent#ACTION_DIAL}
     * so the user confirms the number in the dialer (no permission needed) (2B.5).
     * Pure (returns the inlined String constants) so it is unit-testable.
     */
    static String chooseCallAction(boolean canCall, boolean exactMatch) {
        return (canCall && exactMatch) ? Intent.ACTION_CALL : Intent.ACTION_DIAL;
    }

    private static boolean isDialable(String target) {
        return target != null && target.matches("[+0-9 \\-()]+");
    }

    /**
     * Routes a message to the requested instant-messaging app. The {@code app}
     * param ({@code whatsapp}/{@code telegram}/{@code sms}, case-insensitive,
     * may be null/blank) decides the target so a single SEND_MESSAGE reaches the
     * right composer directly, instead of opening SMS and then the IM app.
     */
    private ActionOutcome sendMessage(String recipient, String body, String app) {
        if (isBlank(recipient)) {
            return ActionOutcome.failed(appContext.getString(R.string.action_message_unknown));
        }
        String normalized = app == null ? "" : app.toLowerCase(Locale.ROOT).trim();
        if (normalized.contains("whatsapp")) {
            return sendWhatsApp(recipient, body);
        }
        if (normalized.contains("telegram")) {
            return sendTelegram(recipient, body);
        }
        return sendSms(recipient, body);
    }

    /**
     * Opens a wa.me deep link for the recipient (number normalized to E.164 — required
     * by wa.me). When no normalizable number exists, opens WhatsApp's home screen so
     * the backend ReAct loop can search the recipient by name on the next turn.
     */
    private ActionOutcome sendWhatsApp(String recipient, String body) {
        String raw = isDialable(recipient)
                ? recipient
                : contactResolver.resolveNumber(recipient).orElse(null);
        String e164 = (raw == null) ? null : normalizer.toE164(raw).orElse(null);

        if (e164 != null) {
            Uri uri = Uri.parse("https://wa.me/" + digitsOnly(e164)
                    + "?text=" + Uri.encode(body == null ? "" : body));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(intent);
            return ActionOutcome.ok(
                    appContext.getString(R.string.action_message_preparing, recipient));
        }

        // No usable number: hand off to the search loop by opening WhatsApp's home
        // screen. Never a targetless wa.me link, never a hard failure (unless
        // WhatsApp isn't installed at all).
        Intent launch = appContext.getPackageManager().getLaunchIntentForPackage("com.whatsapp");
        if (launch == null) {
            return ActionOutcome.failed(
                    appContext.getString(R.string.action_open_app_not_found, "WhatsApp"));
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(launch);
        return ActionOutcome.ok(
                appContext.getString(R.string.action_whatsapp_search_fallback, recipient));
    }

    /**
     * Opens Telegram for the message. Telegram has no reliable public deep link to
     * target a chat by phone number, so we don't pretend to: a @username opens that
     * user directly via {@code tg://resolve} (Telegram can't attach text there), and
     * anything else opens Telegram's share-to-chat picker with the body prefilled so
     * the user picks the chat. The outcome message stays honest about this.
     */
    private ActionOutcome sendTelegram(String recipient, String body) {
        Uri uri;
        String handle = telegramHandle(recipient);
        if (handle != null) {
            uri = Uri.parse("tg://resolve?domain=" + handle);
        } else {
            uri = Uri.parse("https://t.me/share/url?url=&text="
                    + Uri.encode(body == null ? "" : body));
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
        return ActionOutcome.ok(appContext.getString(R.string.action_telegram_opening));
    }

    /**
     * Returns a bare Telegram username (no leading {@code @}) when {@code recipient}
     * looks like a handle and is not a dialable number, else null.
     */
    private static String telegramHandle(String recipient) {
        if (isBlank(recipient) || isDialable(recipient)) {
            return null;
        }
        String trimmed = recipient.trim();
        if (trimmed.matches("@?[A-Za-z0-9_]{5,}")) {
            return trimmed.startsWith("@") ? trimmed.substring(1) : trimmed;
        }
        return null;
    }

    private ActionOutcome sendSms(String recipient, String body) {
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

    private static String digitsOnly(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    private ActionOutcome setAlarm(String time, String label) {
        if (isBlank(time)) {
            return ActionOutcome.failed(appContext.getString(R.string.action_alarm_unknown));
        }
        // Validate explicitly so non-numeric LLM input ("noon") returns a clear,
        // specific failure instead of an Integer.parseInt NumberFormatException
        // surfaced as the generic action_failed. The regex also enforces the
        // colon, so a missing-colon string is rejected here too.
        String trimmedTime = time.trim();
        if (!trimmedTime.matches("\\d{1,2}:\\d{2}")) {
            return ActionOutcome.failed(appContext.getString(R.string.action_alarm_invalid_time));
        }
        String[] parts = trimmedTime.split(":");
        int hour = Integer.parseInt(parts[0].trim());
        int minute = Integer.parseInt(parts[1].trim());

        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
        intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
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
        // Validate explicitly so non-numeric LLM input ("five") returns a clear,
        // specific failure instead of a Double.parseDouble NumberFormatException
        // surfaced as the generic action_failed.
        if (!isPositiveNumber(durationSeconds.trim())) {
            return ActionOutcome.failed(
                    appContext.getString(R.string.action_timer_invalid_duration));
        }
        int seconds = (int) Math.round(Double.parseDouble(durationSeconds.trim()));
        if (seconds <= 0) {
            return ActionOutcome.failed(appContext.getString(R.string.action_timer_invalid));
        }
        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
        intent.putExtra(AlarmClock.EXTRA_LENGTH, seconds);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
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

    // --- accessibility-powered handlers -------------------------------------

    private ActionOutcome navigate(String direction) {
        if (isBlank(direction)) {
            return ActionOutcome.failed(appContext.getString(R.string.action_navigate_unknown));
        }
        AtomAccessibilityService service = AtomAccessibilityService.getInstance();
        if (service == null) {
            return ActionOutcome.failed(appContext.getString(R.string.action_accessibility_disabled));
        }
        return service.navigate(direction)
                ? ActionOutcome.ok(appContext.getString(R.string.action_navigated, direction))
                : ActionOutcome.failed(appContext.getString(R.string.action_navigate_failed));
    }

    private ActionOutcome scroll(String direction) {
        if (isBlank(direction)) {
            return ActionOutcome.failed(appContext.getString(R.string.action_scroll_unknown));
        }
        AtomAccessibilityService service = AtomAccessibilityService.getInstance();
        if (service == null) {
            return ActionOutcome.failed(appContext.getString(R.string.action_accessibility_disabled));
        }
        return service.scroll(direction)
                ? ActionOutcome.ok(appContext.getString(R.string.action_scrolled, direction))
                : ActionOutcome.failed(appContext.getString(R.string.action_scroll_failed));
    }

    private ActionOutcome readScreen() {
        AtomAccessibilityService service = AtomAccessibilityService.getInstance();
        if (service == null) {
            return ActionOutcome.failed(appContext.getString(R.string.action_accessibility_disabled));
        }
        String text = service.readScreen();
        // The screen text is carried in the outcome message so the existing
        // render path speaks/shows it to the user.
        return isBlank(text)
                ? ActionOutcome.ok(appContext.getString(R.string.action_read_screen_empty))
                : ActionOutcome.ok(text);
    }

    private ActionOutcome tapElement(String text) {
        if (isBlank(text)) {
            return ActionOutcome.failed(appContext.getString(R.string.action_tap_unknown));
        }
        AtomAccessibilityService service = AtomAccessibilityService.getInstance();
        if (service == null) {
            return ActionOutcome.failed(appContext.getString(R.string.action_accessibility_disabled));
        }
        return service.tapByText(text)
                ? ActionOutcome.ok(appContext.getString(R.string.action_tapped, text))
                : ActionOutcome.failed(appContext.getString(R.string.action_tap_not_found, text));
    }

    private ActionOutcome typeText(String text, String submitParam) {
        if (isBlank(text)) {
            return ActionOutcome.failed(appContext.getString(R.string.action_type_unknown));
        }
        AtomAccessibilityService service = AtomAccessibilityService.getInstance();
        if (service == null) {
            return ActionOutcome.failed(appContext.getString(R.string.action_accessibility_disabled));
        }
        return service.typeText(text, parseSubmit(submitParam))
                ? ActionOutcome.ok(appContext.getString(R.string.action_typed, text))
                : ActionOutcome.failed(appContext.getString(R.string.action_type_failed));
    }

    /**
     * Resolves the {@code submit} param into a boolean: submit defaults to true
     * when the param is absent/blank (the common "type and search" intent), and
     * any non-boolean string parses to false via {@link Boolean#parseBoolean}.
     * Package-private and static so it is unit-testable without the Android
     * accessibility-service singleton (which short-circuits the JVM path).
     */
    static boolean parseSubmit(String submitParam) {
        return isBlank(submitParam) || Boolean.parseBoolean(submitParam.trim());
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** True when {@code s} parses as a strictly positive decimal number. */
    private static boolean isPositiveNumber(String s) {
        try {
            return Double.parseDouble(s) > 0d;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

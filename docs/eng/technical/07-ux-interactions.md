# Main-Screen UX Interactions [Atom Mobile]

This document describes the quality-of-life interactions on the main screen (`MainActivity`) and the control row (`activity_main.xml`). All state changes route through the existing `fadeSwap`/`applyCoreState` helpers so the atom core, status text, and sub-status stay in sync.

## Microphone mute

- **Gesture:** long-press the central mic FAB (`btn_mic`).
- **Behavior:** toggles the persisted mute flag. The icon swaps `ic_mic` ↔ `ic_mic_off`, the content description and sub-status update, and a `LONG_PRESS` haptic confirms the toggle.
- **Tap while muted:** does not listen; shows the `mic_muted_hint` toast.
- **Muting mid-listen:** tears down the active recognizer and settles the core back to idle.
- **Persistence:** stored in `AtomPreferences` (`mic_muted`), so the icon is correct after rotation, returning from Settings, or a cold start. Restored once in `onCreate` via `applyMicMutedState`.
- **Core identity:** `applyMicMutedState` also calls `AtomCoreView.setMuted`, which applies a near-grayscale (`MUTED_SATURATION`) and dimmed (`MUTED_ALPHA`) color filter to the core's software layer, so a muted mic reads differently from the vivid lavender idle.

## Tap-to-cancel while listening

- **Gesture:** tap the mic FAB while a recognition is in flight.
- **Behavior:** cancels the recognition through the shared `tearDownRecognizer()` (which calls `destroy()`, since `AndroidSpeechRecognizer` has no `stopListening()`), stops the mic pulse, and returns the core to idle.
- **State:** tracked by the `isListening` flag, set in `startListening()` and cleared on end-of-speech, error, or cancel.

## Send-button gating

- A `TextWatcher` on the input field enables the send disc only when there is non-whitespace text.
- While disabled it is dimmed to `SEND_DISABLED_ALPHA` (0.4). This replaces the previous "type a message first" toast as the primary affordance (the toast remains as a safety guard).

## Haptic feedback

- Mic tap: `VIRTUAL_KEY`.
- Mute long-press: `LONG_PRESS`.
- Send: `VIRTUAL_KEY` (only on a successful send).

## Error auto-recovery

- After an error is shown, a delayed `errorRecoverRunnable` eases the status line back to idle after `ERROR_AUTO_RECOVER_MS` (4 s).
- Recovery is skipped if the user has started listening again, is cancelled when a new recognition starts, and is removed in `onDestroy` so it cannot fire after teardown. The resting sub-status respects the current mute state.

## State persistence across configuration changes

- `onSaveInstanceState` stores the visible status text, sub-status text, and the last core energy/glow (tracked in `currentEnergy` / `currentGlow`, updated by `applyCoreState`).
- `restoreUiState` reapplies them in `onCreate`, so a rotation mid-"Thinking" (or any state) no longer snaps back to "Ready".

## App-wide mute (overlay)

- The floating bubble (`FloatingBubbleService`) reads the same persisted `mic_muted` flag.
- On panel build, `applyOverlayMicMuted` sets the `overlay_mic` icon/label to match. A long-press toggles the shared flag (parity with the main screen), and `startVoiceCapture` refuses to listen while muted, showing `mic_muted_hint`.
- Because the flag lives in `AtomPreferences`, muting in either surface mutes both.

## Microphone-permission rationale

- On denial, `onMicPermissionDenied` checks `shouldShowRequestPermissionRationale`:
  - re-askable → shows `mic_permission_rationale` (retry on the next tap);
  - permanently denied → an `AlertDialog` routes to the app's system settings via `openAppSettings` (`ACTION_APPLICATION_DETAILS_SETTINGS`), so the mic isn't a dead end.

## Stopping speech on a new turn

- `AndroidTextToSpeech.stop()` silences in-progress/queued speech without tearing down the engine.
- Called when a new turn starts — `startListening` and `sendFromInputBar` on the main screen, and `startVoiceCapture`/`dispatchPrompt` in the overlay — so a long reply doesn't talk over the next request.

## Back collapses the input bar

- `MainActivity` registers an `OnBackPressedCallback` enabled only while the input bar is open (`showInputBar` enables it, `hideInputBar` disables it). Pressing Back then collapses the bar instead of leaving the screen, matching the tap-outside dismissal.

## Overlay panel parity

- The floating panel mirrors the main-screen polish: `overlay_send` is gated/dimmed (`SEND_DISABLED_ALPHA`) until there's text, send/mic/mute fire haptics, and transient error/cancel statuses auto-recover to `overlay_panel_hint` after `STATUS_RESET_MS` via `scheduleStatusReset` (cancelled when a new turn starts).

## Live cross-surface mute sync

- `AtomPreferences` exposes `registerChangeListener`/`unregisterChangeListener` over its `SharedPreferences`.
- Both `MainActivity` and `FloatingBubbleService` listen for `KEY_MIC_MUTED`; because they share one process and one prefs file, toggling mute on either surface instantly updates the other's mic icon (the overlay re-reads `overlay_mic` from the live `panelView`).

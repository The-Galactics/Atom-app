# ATOM — Overlay/Accessibility/Screen-View Permissions, First-Run Testing, and gRPC Contract Update

## Metadata

| Field   | Value                                                                 |
|---------|-----------------------------------------------------------------------|
| Type    | Feature + Scaffolding + Contract sync                                  |
| Sprint  | 1                                                                     |
| Date    | 2026-06-14                                                            |
| Related | ATOM-30 (gRPC), ADR-001 (Android hexagonal migration)                |
| Branch  | `feature/migration`                                                  |
| Status  | 🟡 Implemented (build-unverified — no Android SDK in environment)    |

---

## Context

The Atom assistant needs to operate *on top of* the phone (an on-screen "super position" overlay) and to observe/act across other apps. That requires OS-level capabilities the app did not previously declare: an **overlay** permission, an **Accessibility Service**, and — as forward-looking work — **screen view** via MediaProjection. In parallel, the Python backend (`Atom-agent`) published a unified gRPC contract that extends the one the app shipped, so the Android proto had to be re-synced. Finally, the app had never been run end to end, so the existing buttons needed real interaction handlers to make a first manual smoke test possible.

This document covers four changes delivered together:

1. Overlay ("super position") + Accessibility permissions.
2. Screen-view (MediaProjection) scaffolding — **future work**.
3. First-run button interactions for manual testing.
4. gRPC contract update to match the backend (source of truth).

---

## 1. Overlay ("super position") and Accessibility

### Permissions and manifest

- `SYSTEM_ALERT_WINDOW` — lets the assistant draw over other apps (the "super position" overlay).
- An `AccessibilityService` declared with the `BIND_ACCESSIBILITY_SERVICE` permission and the `android.accessibilityservice.AccessibilityService` intent filter, plus a `<meta-data>` pointing at its capability descriptor.

### Files

- `src/main/AndroidManifest.xml` — permission + `<service>` declarations.
- `src/main/java/com/atom/infrastructure/adapter/accessibility/AtomAccessibilityService.java` — the service. Currently scaffolding: it logs connection and events so it can be verified as live; no node inspection or gesture dispatch yet (**future work**).
- `src/main/res/xml/accessibility_service_config.xml` — declares listened events, `canRetrieveWindowContent`, `canPerformGestures`, and flags.
- `src/main/res/values/strings.xml` — `accessibility_service_description`.

### Runtime flow

Centralized in `com.atom.app.permission.PermissionCoordinator`:

- **Overlay:** `canDrawOverlays(context)` checks the grant; `overlaySettingsIntent(context)` builds the `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` intent (`package:` URI). `MainActivity` launches it via an `ActivityResultLauncher` and re-checks on return.
- **Accessibility:** cannot be enabled programmatically. `isAccessibilityServiceEnabled(context)` reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`; `accessibilitySettingsIntent()` opens `Settings.ACTION_ACCESSIBILITY_SETTINGS` for the user to toggle Atom on.

---

## 2. Screen view (MediaProjection) — FUTURE WORK

Scaffolding only. Screen capture/encoding/streaming is **not** implemented.

### Permissions and manifest

- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PROJECTION`.
- A `<service>` with `android:foregroundServiceType="mediaProjection"`.

### Files

- `src/main/java/com/atom/infrastructure/adapter/screen/ScreenCaptureService.java` — foreground service stub. Starts itself in the foreground with a notification and accepts the consent result code/data via `EXTRA_RESULT_CODE` / `EXTRA_RESULT_DATA`, but performs no capture.
- `PermissionCoordinator.screenCaptureIntent(context)` — builds the consent intent from `MediaProjectionManager`.

### Flow

`MainActivity` launches the consent intent for result; on `RESULT_OK` it starts `ScreenCaptureService` with the result extras. The remaining work (obtaining the `MediaProjection`, `VirtualDisplay`, encoder/stream) is marked `FUTURE WORK` in code.

---

## 3. First-run button interactions

`MainActivity` now wires every button on `activity_main.xml` to a real flow so the app can be smoke-tested manually for the first time:

| Button         | Action                                                              |
|----------------|--------------------------------------------------------------------|
| `btn_mic`      | Sends a real message over gRPC `StreamChat` to the Python backend  |
| `btn_settings` | Opens `SettingsActivity`                                            |
| `btn_history`  | Requests the overlay ("super position") permission                 |
| `btn_keyboard` | Sends the user to enable the Accessibility service                 |
| `btn_volume`   | Requests screen-capture (MediaProjection) consent — scaffolding    |

End-to-end testability: with `Atom-agent` running and `GRPC_HOST`/`GRPC_PORT` pointed at it (default `10.0.2.2:50051` for the emulator), tapping the mic exercises the full path UI → ViewModel → `ChatRepository` → `StreamChatPortIn` → gRPC adapter → backend and renders the response. Results of permission flows are surfaced via toasts.

---

## 4. gRPC contract update

The backend contract at `Atom-agent/proto/atom_agent.proto` is the source of truth. The app's `src/main/proto/ai.proto` was a strict subset; it was re-synced.

### What changed

- Same `package com.atom.proto`, same `java_package`/`java_outer_classname`, same `AtomAgentService` name.
- **Unchanged RPCs:** `ExecuteCommand` (unary) and `StreamChat` (server-streaming) — message shapes identical, so `InteractionGrpcAdapter`'s existing methods stay valid.
- **Added RPCs (voice):**
  - `Transcribe(TranscribeRequest) returns (TranscribeResponse)` — Speech-to-Text, unary.
  - `Synthesize(SynthesizeRequest) returns (stream SynthesizeResponse)` — Text-to-Speech, server-streaming.
- New messages: `TranscribeRequest/Response`, `SynthesizeRequest/Response` (copied field-for-field, including tag numbers, from the backend).

### Hexagonal wiring

To keep boundaries intact, the new capabilities are exposed through the existing out-port rather than leaking generated stubs:

- `ExternalInteractionPortOut` — added `transcribeAudio(...)` and `synthesizeSpeech(...)`.
- `InteractionGrpcAdapter` — implements them against the generated `transcribe`/`synthesize` stubs (uses `ByteString` for `bytes` fields). Marked `FUTURE WORK`: no UI/use-case drives them yet.

### Codegen

No `build.gradle.kts` change needed — same proto file path and the same protobuf/grpc-java lite plugin config compile the new messages and RPCs.

---

## Verification status

- Proto syntax validated with `grpc_tools.protoc` (from the backend venv): **OK**.
- Java signatures reviewed against generated-name conventions (`transcribe`, `synthesize`, `setAudioBytes`, etc.) and existing precedent.
- Manifest reviewed for validity (service declarations, permissions, foreground service type).
- **Not** built with the Android toolchain — no Android SDK in this environment. A `./gradlew assembleDebug` is required to fully confirm.

## Direct access

- [Proto contract](../../../../../src/main/proto/ai.proto)
- [gRPC adapter](../../../../../src/main/java/com/atom/infrastructure/adapter/grpc/InteractionGrpcAdapter.java)
- [Accessibility service](../../../../../src/main/java/com/atom/infrastructure/adapter/accessibility/AtomAccessibilityService.java)
- [Screen capture service (scaffolding)](../../../../../src/main/java/com/atom/infrastructure/adapter/screen/ScreenCaptureService.java)
- [Permission coordinator](../../../../../src/main/java/com/atom/app/permission/PermissionCoordinator.java)

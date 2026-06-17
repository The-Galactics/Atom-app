# ADR-002 — Order → Action Execution (Function Calling + Accessibility)

- **Status:** Accepted
- **Date:** 2026-06-16
- **Deciders:** Architecture (Principal Architect), Mobile team (The-Galactics)
- **Context source:** Adding device actions on top of the hexagonal core established in [ADR-001](./ADR-001-android-hexagonal-migration.md).

---

## 1. Context

Until now the app could only **converse**: text in → AI text out (gRPC `StreamChat`).
The product goal is for the user to give a natural-language **order** ("open
WhatsApp", "call mum", "set an alarm at 7:30") and have Atom **perform it** on
the device.

The Python AI agent (`Atom-agent`) was extended to interpret an order with **LLM
function calling** (Google Gemini): it classifies the utterance into a structured
action and returns it over the existing gRPC `ExecuteCommand` RPC. The contract
is documented in `Atom-agent/INTENT_ACTIONS_CONTRACT.md` (the single source of
truth for the action catalog).

On the Android side we must: receive that structured action, decide whether it
needs user confirmation, and **execute** it — some actions are plain `Intent`s,
others need a system service or the **AccessibilityService**.

---

## 2. Decision

Split the feature along the same dependency rule as ADR-001: **recognition is
remote, execution is local**, and both sit behind ports so the domain stays pure.

### 2.1 The contract (`ai.proto`)

`CommandResponse` was extended (backward-compatible field numbers) to carry the
resolved action:

```proto
message CommandResponse {
  bool   success = 1;
  string out_message = 2;            // reply to speak/show
  string action_type = 3;           // "OPEN_APP", "MAKE_CALL", … or "NONE"
  string parameters_json = 4;       // JSON slots, e.g. {"app_name":"whatsapp"}
  float  confidence = 5;
  bool   requires_confirmation = 6; // sensitive actions confirm first
}
```

The proto is shared with the backend; the protobuf-gradle plugin regenerates the
Java stubs on build. **`ai.proto` must stay byte-for-byte compatible with
`Atom-agent/proto/atom_agent.proto`.**

### 2.2 Layer map (new pieces)

```
domain.action            ActionType (enum, mirrors the backend catalog)
                         ResolvedAction (immutable projection of CommandResponse)
                         ActionOutcome (result of executing an action)
application.port.in      ExecuteCommandPortIn  -> now returns ResolvedAction
application.port.out     ExternalInteractionPortOut.commandResponse -> ResolvedAction
                         ActionExecutorPortOut (NEW: execute a ResolvedAction)
application.usecase      ExternalCommandUseCase (delegates recognition to the backend)
infrastructure.adapter.grpc           InteractionGrpcAdapter (parses parameters_json here)
infrastructure.adapter.action         AndroidActionExecutor (the on-device dispatcher)
infrastructure.adapter.accessibility  AtomAccessibilityService (global-action executor)
app.repository           CommandRepository (threads recognition + execution)
app.viewmodel            ChatViewModel.sendOrder() / runAction() / pendingConfirmation
app                      MainActivity (typed input = order; confirm dialog)
```

**The dependency rule holds:** JSON and protobuf live only in the gRPC adapter;
Android APIs live only in `infrastructure.adapter.action` / `.accessibility`. The
`domain` and `application` layers never import either.

### 2.3 Execution strategy (per action)

| `action_type` | Slots | Confirm | Android mechanism |
|---|---|:--:|---|
| `OPEN_APP` | `app_name` | no | `PackageManager.getLaunchIntentForPackage` (fuzzy label/package match) |
| `MAKE_CALL` | `target` | **yes** | `ACTION_CALL` (perm `CALL_PHONE`); falls back to `ACTION_DIAL` if not granted |
| `SEND_MESSAGE` | `recipient`, `body`, `app?` | **yes** | `ACTION_SENDTO` (`smsto:`) pre-filled composer (no perm) |
| `SET_ALARM` | `time`, `label?` | no | `AlarmClock.ACTION_SET_ALARM` |
| `SET_TIMER` | `duration_seconds`, `label?` | no | `AlarmClock.ACTION_SET_TIMER` |
| `TOGGLE_SETTING` | `setting`, `state` | no | flashlight: `CameraManager.setTorchMode`; wifi/bt/DnD: Settings panel/screen |
| `NONE` | — | no | no action; present `out_message` |

**Why an AccessibilityService.** Intents cover most actions reliably. But apps
**cannot silently toggle radios** (Wi-Fi/Bluetooth) on Android 10+ (API 29+), and
some flows need system navigation. `AtomAccessibilityService` is the fallback
executor: it exposes `back()/home()/recents()/quickSettings()` via
`performGlobalAction`, reachable through `AtomAccessibilityService.getInstance()`.
It is enabled by the user from system Settings (checked by `PermissionCoordinator`).
For now `TOGGLE_SETTING` opens the relevant Settings panel rather than driving the
toggle through accessibility — safer and predictable; the service is wired and
ready for gesture-driven flows next.

### 2.4 Confirmation UX

`requires_confirmation` (true for `MAKE_CALL`, `SEND_MESSAGE`) makes the ViewModel
emit `pendingConfirmation`; `MainActivity` shows an `AlertDialog` using
`out_message` and only calls `runAction` on **Yes**. Non-sensitive actions run
immediately; `NONE` shows `out_message` as a conversational reply.

### 2.5 Flow

```
User types/says an order
  → ChatViewModel.sendOrder(text)
  → CommandRepository.recognize()         [background thread]
  → ExecuteCommandPortIn → gRPC ExecuteCommand → ResolvedAction
  → if NONE: show out_message
    else if requires_confirmation: AlertDialog → (Yes) →
    CommandRepository.run() → ActionExecutorPortOut → AndroidActionExecutor
  → ActionOutcome (success/message) → status text
```

---

## 3. Consequences

**Positive**
- Recognition and execution are independently testable; `ActionExecutorPortOut`
  and `ExecuteCommandPortIn` are mockable without Android or a live backend.
- Adding an action is a localized change (see §4).
- Forward-compatible: an unknown `action_type` from a newer backend degrades to
  `NONE` (`ActionType.fromWire`), so an older app never crashes.

**Negative / risks**
- `OPEN_APP` resolution is heuristic (label/package contains); ambiguous names
  may mis-resolve. Mitigation: exact-label match wins; otherwise first contains.
- Radio toggles are not silent (platform restriction) — the user finishes the
  toggle in the Settings panel.
- New runtime permissions (`CALL_PHONE`, `ACCESS_NOTIFICATION_POLICY`) must be
  requested at runtime via `PermissionCoordinator` before the relevant actions.
- Package visibility: a `<queries>` block is required (Android 11+) for
  `OPEN_APP` to see launchable apps.

---

## 4. How to add a new action (keep both sides in sync)

1. **Backend** — add one `ActionSpec` to `Atom-agent/domain/intent/catalog.py`
   (tool name, slots, `requires_confirmation`). It auto-binds as a callable tool.
2. **Android** — add the enum constant to `ActionType`, a `case` in
   `AndroidActionExecutor.execute`, and any new permission in the manifest.
3. Update the catalog table here and in `INTENT_ACTIONS_CONTRACT.md`.
4. No proto change is needed unless you add new **response fields**.

---

## 5. Testing notes

- `InteractionGrpcAdapterTest` covers the wire → `ResolvedAction` mapping
  (conversational `NONE` and an executable action) over an in-process gRPC server.
- Parameter parsing uses `org.json`, which is only a stub in JVM unit tests;
  tests therefore exercise the empty-parameters path. Slot parsing is covered by
  instrumented tests / manual verification on device.
- **Known pre-existing gap:** the test source set references JUnit Jupiter,
  Mockito, AssertJ and grpc-testing, which are **not declared** in
  `build.gradle.kts` (`testImplementation` only has JUnit 4). The unit-test source
  set does not compile until those dependencies are added. Production code
  (`compileDebugJavaWithJavac`) builds cleanly.

# Project Atom: UI Redesign — Design System, Screens & Floating Overlay

> Companion to [`Atom-View.md`](./Atom-View.md). That document captured the original
> "Aether" vision; this one documents the **refined** design system and the
> redesigned screens, the in-app input bar, and the floating system overlay that
> realize it. Inspiration was taken from the **Gemini mobile assistant**, while
> Atom's identity (deep-onyx canvas, lavender core, serif voice) is preserved.

The colors and typography below are **rendered using those very colors and
fonts** (inline HTML swatches and serif/sans specimens) so this page is itself a
living spec.

---

## 1. Design System (single source of truth)

The tokens live in `src/main/res/values/`:
`colors.xml`, `dimens.xml`, `styles.xml`, plus reusable drawables under
`src/main/res/drawable/`. Everything else in the app consumes them.

### 1.1 Color palette

#### Brand base (identity — unchanged)

<table>
  <tr>
    <td style="background:#0A0A0C;width:120px;height:56px;border:1px solid #333"></td>
    <td><code>deep_onyx</code> / <code>surface_base</code><br/><b>#0A0A0C</b> — screen canvas</td>
  </tr>
  <tr>
    <td style="background:#BF94FF;width:120px;height:56px"></td>
    <td><code>purple_lavender</code> / <code>accent</code><br/><b>#BF94FF</b> — core, mic FAB, focus, highlights</td>
  </tr>
  <tr>
    <td style="background:#121214;width:120px;height:56px;border:1px solid #333"></td>
    <td><code>surface_dark</code> / <code>surface_1</code><br/><b>#121214</b> — cards & input wells</td>
  </tr>
</table>

#### Surface elevation ramp (new)

<table>
  <tr><td style="background:#0A0A0C;width:120px;height:40px;border:1px solid #333"></td><td><code>surface_base</code> <b>#0A0A0C</b></td></tr>
  <tr><td style="background:#121214;width:120px;height:40px;border:1px solid #333"></td><td><code>surface_1</code> <b>#121214</b> — cards / input wells</td></tr>
  <tr><td style="background:#191920;width:120px;height:40px;border:1px solid #333"></td><td><code>surface_2</code> <b>#191920</b> — raised cards, overlay panel</td></tr>
  <tr><td style="background:#22222B;width:120px;height:40px;border:1px solid #333"></td><td><code>surface_3</code> <b>#22222B</b> — pressed / focused</td></tr>
</table>

#### Accent ramp & gradient pair (new)

<table>
  <tr><td style="background:#D6B8FF;width:120px;height:40px"></td><td><code>accent_bright</code> <b>#D6B8FF</b> — gradient highlight</td></tr>
  <tr><td style="background:#BF94FF;width:120px;height:40px"></td><td><code>accent</code> <b>#BF94FF</b></td></tr>
  <tr><td style="background:#8C5CF0;width:120px;height:40px"></td><td><code>accent_deep</code> <b>#8C5CF0</b> — gradient deep end</td></tr>
</table>

The mic FAB, the bubble and the core glow all use the
`accent_bright → accent → accent_deep` gradient so the lavender reads as a single
luminous material.

#### On-surface text tints & status

<table>
  <tr><td style="background:#0A0A0C;color:#FFFFFF;width:200px;height:34px;padding:0 8px">on_surface_high — 100%</td><td><b>#FFFFFF</b> primary text</td></tr>
  <tr><td style="background:#0A0A0C;color:#B3B3B3;width:200px;height:34px;padding:0 8px">on_surface_medium — 70%</td><td><b>#B3FFFFFF</b> secondary</td></tr>
  <tr><td style="background:#0A0A0C;color:#808080;width:200px;height:34px;padding:0 8px">on_surface_label — 50%</td><td><b>#80FFFFFF</b> labels</td></tr>
  <tr><td style="background:#0A0A0C;color:#FF8A80;width:200px;height:34px;padding:0 8px">status_error</td><td><b>#FF8A80</b></td></tr>
  <tr><td style="background:#0A0A0C;color:#7EE6A8;width:200px;height:34px;padding:0 8px">status_success</td><td><b>#7EE6A8</b></td></tr>
</table>

### 1.2 Typography (type scale)

Locked identity rule: **serif** for display headings (wordmark, status, section
titles, the AI's "voice"); **system sans** for body, labels, buttons, captions.
No font files are bundled — we refine the platform `serif` and `sans-serif`
families through size, tracking and weight only. Styles live in `styles.xml`.

| Style | Family | Size | Specimen |
|---|---|---|---|
| `TextAppearance.Atom.Wordmark` | serif | 26sp / +0.28 tracking | <span style="font-family:Georgia,serif;font-size:26px;letter-spacing:6px">ATOM</span> |
| `TextAppearance.Atom.Display` | serif | 30sp | <span style="font-family:Georgia,serif;font-size:28px">How can I help?</span> |
| `TextAppearance.Atom.Status` | serif (accent) | 22sp | <span style="font-family:Georgia,serif;font-size:22px;color:#BF94FF">Listening</span> |
| `TextAppearance.Atom.Title` | serif | 20sp | <span style="font-family:Georgia,serif;font-size:20px">Settings</span> |
| `TextAppearance.Atom.Body` | sans | 16sp | <span style="font-family:Helvetica,Arial,sans-serif;font-size:16px">Ask Atom anything in your own words.</span> |
| `TextAppearance.Atom.Label` | sans-medium | 14sp | <span style="font-family:Helvetica,Arial,sans-serif;font-size:14px;font-weight:600">Pro</span> |
| `TextAppearance.Atom.Caption` | sans | 12sp | <span style="font-family:Helvetica,Arial,sans-serif;font-size:12px;color:#808080">Your name</span> |
| `TextAppearance.Atom.Overline` | sans-medium, caps | 11sp / +0.18 | <span style="font-family:Helvetica,Arial,sans-serif;font-size:11px;letter-spacing:2px;color:#808080">AWAITING PROMPT</span> |

### 1.3 Reusable drawables

| Drawable | Purpose |
|---|---|
| `bg_core_glow` | Radial lavender halo rendered behind the Lottie core |
| `bg_mic_fab` | Filled accent gradient disc + ripple — the mic FAB |
| `bg_pill_input` | Rounded pill input background with accent **focus** state |
| `bg_surface_card` / `bg_surface_raised` | Rounded surfaces at elevation 1 / 2 |
| `ripple_borderless` / `ripple_card` | Accent ripples for ghost icons / cards |
| `bg_bubble` | Lavender gradient disc for the floating overlay bubble |
| `bg_send_button` | Small filled accent disc behind the send icon |

---

## 2. Main screen redesign

File: `src/main/res/layout/activity_main.xml` (IDs unchanged; `MainActivity`
wiring intact).

**Gemini inspiration:** generous breathing room, a single luminous hero, a
filled circular primary action flanked by quiet "ghost" controls, and a pill
input that slides up from the bottom.

**Atom identity kept:** the Lottie "atom core" remains the protagonist, now seated
in a soft radial `bg_core_glow` halo; the serif status line ("Listening", responses)
keeps the assistant's contemplative voice; deep-onyx canvas and lavender accent are
untouched.

Concretely:
- Top bar uses ghost icon buttons (`Widget.Atom.IconButton.Ghost`) with the
  refined `ATOM` wordmark centered between History and Settings.
- The core is anchored slightly above center (42% guideline) with the glow
  behind it for depth.
- Status (`status_text`) + sub-status (`sub_status_text`) use the serif `Status`
  and the all-caps `Overline` styles; the existing observers drive them through
  idle / thinking / responded / error states.
- The control row centers a **filled accent mic FAB** (`bg_mic_fab`) between
  ghost keyboard and volume buttons.

---

## 3. In-app input bar

Files: `src/main/res/layout/view_input_bar.xml` (included into the main screen),
wired in `MainActivity`.

Replaces the old plain `AlertDialog` typing flow with a Gemini-style rounded pill:
inline mic, a flexible `EditText` hinted **"Ask Atom…"**, and a filled accent send
disc. Focusing the field activates the pill's accent border (`bg_pill_input`
`state_activated`).

**Trigger decision — toggle, not persistent.** `btnKeyboard` reveals/hides the
bar (and the soft keyboard). Rationale: the main screen is voice-first with the
core as its hero; a permanently docked bar would compete with the mic FAB and
crowd the composition. Toggling keeps `btnKeyboard` a meaningful trigger while
still delivering the polished typing experience on demand.

Sending routes through the **same** `viewModel.sendMessage()` → `ChatRepository`
→ `StreamChatPortIn` gRPC path as the mic — only the input source differs.
Pressing the IME "Send" action mirrors the send button.

---

## 4. Floating system overlay (configure → use)

Files: `FloatingBubbleService.java` (`com.atom.app.overlay`),
`view_overlay_bubble.xml`, `view_overlay_panel.xml`; manifest service entry with
`foregroundServiceType="specialUse"`.

### 4.1 The configure-then-use UX flow

> *"The person first configures and then uses; then when they need the app they
> have the bubble system."*

```
First run (inside the app)
  ┌─────────────────────────────────────────────┐
  │ 1. CONFIGURE                                 │
  │    Settings → SAVE → ensureAssistantPermissions()
  │    grants: overlay, accessibility, screen-capture
  │ 2. ENABLE                                    │
  │    Settings → "Enable floating bubble"       │
  │    (gated on overlay permission)             │
  └─────────────────────────────────────────────┘
                       │
                       ▼
Everyday use (on top of any app)
  ┌─────────────────────────────────────────────┐
  │ Draggable Atom bubble floats over other apps │
  │ tap → compact input panel (text + mic)       │
  │ send → same gRPC / StreamChat path           │
  └─────────────────────────────────────────────┘
```

### 4.2 Architecture

- `FloatingBubbleService` is a **foreground** `Service` (ongoing notification,
  `specialUse` type for Android 14+). It adds a draggable bubble to the
  `WindowManager` using `TYPE_APPLICATION_OVERLAY`.
- Touch handling distinguishes **drag** (movement beyond touch-slop → reposition)
  from **tap** (→ expand). Expansion swaps the bubble for a focusable input panel
  (`view_overlay_panel.xml`) that can receive keyboard input.
- The panel reuses the design system (raised surface, pill input, accent send)
  and dispatches prompts through a `ChatRepository` built from
  `AppContainer.getExternalMessageUseCase()` — **the same gRPC `StreamChat`
  contract** the in-app UI uses, so there is one backend path, not two.
- `SettingsActivity` starts (`ACTION_START`) / stops (`ACTION_STOP`) the service
  and keeps its toggle label in sync with overlay-permission and run state.

---

## 5. Future work

- **Real voice capture.** The mic affordances (main FAB, input-bar mic, overlay
  mic) currently send a fixed smoke-test prompt. Wiring `RECORD_AUDIO` + the
  `transcribeAudio` round-trip (already on `ExternalInteractionPortOut`) is the
  next step; TTS playback via `synthesizeSpeech` follows.
- **State-reactive core.** Drive distinct Lottie speeds/colors for
  idle / listening / thinking from the existing observers.
- **Conversation history** store + screen (the History button is still a
  placeholder).
- **Persistence** of profile name / assistant name / volume from Settings.
- **Bubble session restore** across process death (re-arm from a saved pref).

> Build note: this redesign was authored without the Android SDK available in the
> environment, so it is **build-unverified**. Every referenced resource ID and
> symbol was cross-checked by hand against the sources.

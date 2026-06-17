# Project Atom: Floating Overlay Motion — Bubble Identity, Mic Animation & Edge Hide

> Companion to [`Atom-Redesign.md`](./Atom-Redesign.md) §4 (the floating system
> overlay) and [`Atom-View.md`](./Atom-View.md) (the original "Aether" vision).
> That section established the draggable bubble and the expand-to-panel flow; this
> document covers the **motion and identity** pass layered on top of it: the bubble
> now wears the Atom mark, the microphone breathes while it listens, and the bubble
> can tuck itself into a screen border.

All three features touch a single screen surface — the collapsed bubble and its
input panel — and are implemented without new dependencies (no Lottie, no Gradle
changes), using only `android.animation` property animators and a vector drawable.

---

## 1. Bubble identity — the Atom mark

The collapsed bubble previously displayed `ic_mic` on the lavender gradient disc,
which read as "a microphone" rather than "Atom". It now displays the brand mark so
the floating presence matches the app launcher.

| Element | Before | After |
|---|---|---|
| Bubble glyph | `ic_mic` | `ic_atom_glyph` |
| Tint | `on_accent` | `white` (glyph reads bright on the lavender disc) |
| Disc background | `bg_bubble` | `bg_bubble` (unchanged) |

### 1.1 The shared mark

`ic_atom_glyph.xml` reuses the **exact path** from `ic_launcher_foreground.xml`, so
the bubble and the launcher are the same artwork. The launcher path is authored for
the adaptive-icon safe zone (a `108x108` viewport with the mark parked in the inner
region); reusing it verbatim would leave the bubble glyph small and off-center.

The glyph is therefore re-projected into a tight `48x48` box via a `<group>`
transform, and the fill is set to **white** so the call site drives the color
through `app:tint`:

```
viewport 48x48, fill #FFFFFF (tint-driven)
group: scaleX/Y 0.55, translateX -37.82, translateY -5.66
  -> path content centers on (24, 24)
  -> spans x ~7..41, y ~2..46  (centered, no clipping, even margin)
```

> Design rule kept: brand artwork lives in **one** path. If the logo changes, update
> `ic_launcher_foreground` and copy the path into `ic_atom_glyph` — the transform
> and the tint stay as they are.

---

## 2. Microphone animation

The panel microphone (`R.id.overlay_mic` in `view_overlay_panel.xml`) gains two
distinct motions, both driven from `FloatingBubbleService`. They reuse the "breathing
core" language from the main screen so the overlay feels like the same organism.

### 2.1 States

```
RESTING        scale 1.0, alpha 1.0            idle, awaiting a tap
  │  tap
  ▼
PRESS SETTLE   scale 1.0 -> 0.86 -> 1.0        ~90 ms dip-and-recover (tactile ack)
  │
  ▼
LISTENING      scale 1.0 <-> 1.18              infinite breathing pulse +
               alpha 1.0 <-> 0.55             alpha fade, while a request is in flight
  │  response (success or error)
  ▼
RESTING        scale 1.0, alpha 1.0            pulse cancelled, mic reset
```

### 2.2 Implementation

- **Press settle** uses a `ViewPropertyAnimator` (`view.animate()`): a brief scale
  dip to `0.86` and back, acknowledging the tap before the request leaves.
- **Listening pulse** is an `ObjectAnimator` over `SCALE_X`, `SCALE_Y` and `ALPHA`
  (`PropertyValuesHolder`), `REPEAT_MODE = REVERSE`, `REPEAT_COUNT = INFINITE`, with
  an `AccelerateDecelerateInterpolator` for an organic ease. One half-cycle is
  `620 ms`.
- The pulse **starts** when the mic is tapped (`startMicPulse`) and **stops** in both
  the `onSuccess` and `onError` callbacks of `askAtom` (`stopMicPulse`), which also
  resets scale and alpha to rest. The mic view is threaded into `askAtom` so the
  callback can reach it.
- The view may detach mid-request (panel collapsed, app foregrounded). `stopMicPulse`
  tolerates a `null` / detached view, mirroring the existing
  `status.isAttachedToWindow()` guards. Teardown paths (`collapseToBubble`,
  `hideOverlayViews`, `stopOverlay`, `onDestroy`) cancel the animator via
  `cancelAnimations`.

> Until real speech capture lands, the mic still fires the fixed smoke-test prompt
> (see [`Atom-Redesign.md`](./Atom-Redesign.md) §5). The pulse is the visual stand-in
> for the "listening" state and will map directly onto the live STT round-trip when
> `RECORD_AUDIO` + `transcribeAudio` are wired in.

---

## 3. Edge snap, force-to-hide & the edge handle

The bubble can be snapped to a screen edge, **forced into a border to hide it**, or
tucked away on demand from the panel. Hiding never makes it vanish: it collapses to a
small **arrow tab pinned to the edge**, so it stops covering other apps while staying
one tap away.

### 3.1 Behavior

```
DRAG ───────────────────────────────────────────────────────────────►
  release near an edge          release shoved PAST the edge
        │                                  │
        ▼                                  ▼
  SNAP (resting)                     HIDE -> edge handle
  glides flush to the edge,          bubble collapses to a small
  fully visible, 8dp margin          chevron tab on that edge
                                            │  tap the arrow
                                            ▼
                                     bubble returns at the same spot

PANEL hide button (chevron) ───────────────► same HIDE path
```

- On drag release, the bubble **snaps to the nearest horizontal edge** (left/right
  decided by its center vs. the screen midline), gliding flush with an 8dp margin.
- If the drag shoves the bubble more than `40%` of its width past that edge, it
  **collapses to the edge handle**: a thin glowing white line (matching the Atom logo)
  in the Game Turbo / Edge-panel style. The visible line is `handle_width` 11dp ×
  `handle_bar_height` 67dp with a soft white halo, pinned flush to the border; the
  touchable window is a wider
  transparent `handle_touch_width` (36dp) grab zone around it so the restore tap lands
  reliably. The bubble and panel come down, but the service keeps running.
- The expanded panel carries a **hide** button (a chevron, next to Close) that
  triggers the identical collapse — so the show/hide gesture is one tap away in both
  directions.
- **The handle is draggable, just like the bubble** — move it anywhere; on release it
  glides flush to the nearest edge. It sits **dimmed** (`HANDLE_IDLE_ALPHA` 0.5) while
  untouched and lifts to full opacity the moment you touch it, so it stays unobtrusive.
- **Tapping the handle restores the bubble** at the edge and height it was last left.
  The ongoing notification remains a secondary way back (tap or **Show**), and
  **Turn off** stops the overlay entirely. The hidden/open state survives the app
  coming to the foreground and going back: `onAppBackground` re-shows whichever state
  the user left.

### 3.2 Implementation

- The snap is animated by a `ValueAnimator` (0 → 1) that interpolates `bubbleParams.x`
  / `bubbleParams.y`, pushing each frame through `windowManager.updateViewLayout`.
  Duration `220 ms`, `DecelerateInterpolator`.
- Hiding (`hideToHandle`) records the resting edge/Y, removes the bubble/panel, sets
  `collapsedToHandle`, and adds the handle window (`view_overlay_handle.xml`). The line
  (`handle_bar`) is pinned to the docked border via `applyHandleSide` (its gravity flips
  start/end), while the transparent grab zone is what the window docks against.
  `restoreBubble` removes the handle and calls `showBubbleAt(onLeft, y)`.
- **Tap-to-restore, not edge-swipe**, deliberately: an edge swipe would collide with the
  Android 10+ Back gesture, so the line restores on tap and repositions on drag.
- The handle has its own `HandleTouchListener` (drag / tap), mirroring the bubble's:
  touch lifts it to full opacity, drag moves the window, and release runs
  `settleHandleToEdge` — a `ValueAnimator` that snaps to the nearest edge (using the
  grip's **measured** width so the right-edge target lands flush) while fading back to
  `HANDLE_IDLE_ALPHA`. A tap with no drag calls `restoreBubble`; `ACTION_CANCEL`
  (gesture stolen by the system) settles or fades back instead of leaving it stuck lit.
- `onStartCommand` handles `ACTION_SHOW` (notification) by routing to `restoreBubble`;
  `buildNotification(boolean hidden)` maps the content intent and a `Show` action to
  `ACTION_SHOW`, and a `Turn off` action to `ACTION_STOP` (distinct request codes).
- Vertical position is clamped to the screen so neither bubble nor handle can strand
  off the top or bottom. Screen bounds come from `WindowManager.getCurrentWindowMetrics`
  on API 30+, with a `getDefaultDisplay().getMetrics(...)` fallback below it.
- **Device adaptiveness / "never stranded":** the bubble's `BubbleTouchListener` handles
  `ACTION_CANCEL` (a drag stolen by edge gesture-nav) by settling to the edge; both snap
  animators apply their final docked position in `onAnimationEnd`, so the element still
  reaches the edge when the system skips animation frames (animator duration scale 0 /
  battery saver); and `onConfigurationChanged` re-docks and re-clamps on rotation or any
  screen-size change. Together these stop the overlay from being left floating mid-screen.
- A measurement fallback (`bubbleSpan`) uses `R.dimen.bubble_size` for the rare frame
  where the view has not been laid out yet.
- The running snap animator is cancelled on a new touch and on every teardown path,
  alongside the mic pulse.

### 3.3 Tokens & size

| Token | Value | Purpose |
|---|---|---|
| `bubble_size` | `44dp` | compact, low-intrusion disc (was 60dp) |
| `bubble_edge_margin` | `8dp` | resting gap from the screen edge |
| `handle_width` | `11dp` | width of the **visible** edge line |
| `handle_bar_height` | `67dp` | length of the visible edge line |
| `handle_touch_width` | `36dp` | transparent grab zone around the line so the tap lands |
| `handle_height` | `96dp` | height of the grab zone |

Motion constants (durations, pulse scale, push-off fraction) live as
`private static final` fields in `FloatingBubbleService` — they are behavior, not
layout, and are documented inline.

---

## 4. Touched files

| File | Change |
|---|---|
| `src/main/res/drawable/ic_atom_glyph.xml` | Brand mark re-centered for the bubble |
| `src/main/res/drawable/ic_chevron.xml` | Chevron for the panel hide button |
| `src/main/res/drawable/bg_edge_handle.xml` | Glowing white edge-line background (halo + core) |
| `src/main/res/layout/view_overlay_bubble.xml` | Bubble shows `ic_atom_glyph` |
| `src/main/res/layout/view_overlay_panel.xml` | Chevron hide button in the header |
| `src/main/res/layout/view_overlay_handle.xml` | Thin glowing edge line + transparent grab zone shown while hidden |
| `src/main/res/values/dimens.xml` | Smaller `bubble_size`; `bubble_edge_margin`; `handle_width`/`handle_height` |
| `src/main/res/values/strings.xml` | Hide / notification action + content-description strings |
| `src/main/java/com/atom/app/overlay/FloatingBubbleService.java` | Mic pulse + press settle; edge snap; force-to-hide to the edge handle; restore; notification show/stop; animator cleanup |

> Build note: unlike the original redesign, this pass was authored **with** the
> Android toolchain available and verified via `./gradlew compileDebugJavaWithJavac`.
> The deprecation note from the pre-API-30 `getDefaultDisplay()` fallback is expected
> and intentional.

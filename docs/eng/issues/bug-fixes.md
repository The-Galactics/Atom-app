# Bug Fix Log — Atom App (Android / Java)
**Version:** 1.0.0
**Project:** Atom — AI Assistant for Mobile Devices
**Component:** `Atom-app` (Android · Java · gRPC · Accessibility Service · EncryptedTokenStore)
**Architecture:** Hexagonal (Domain – Application – Infrastructure)

---

> **Purpose:** This document retroactively records the bugs detected during development (QA phase) and the fixes applied to the mobile app. Each entry documents symptom, root cause, solution, reproduction and verification.
>
> **ID convention:**
> - Fix: `FIX-APP-[NNN]`
> - Reference to the User Story (US) and the Git commit that applied the change.
>
> **Severity levels:** `Critical` · `High` · `Medium` · `Low`

---

## Index

| ID | Title | Severity | US | Commit |
|----|-------|----------|----|--------|
| FIX-APP-001 | Token refresh on the hot path of every authenticated RPC | High | US-E3 | `9198a36` |
| FIX-APP-002 | Expired token sent before the autonomous command loop | High | US-E3 | `cf74bea` |
| FIX-APP-003 | Expired token sent when sending a chat message | High | US-E3 | `a882c30` |
| FIX-APP-004 | Test regression from gRPC deadlines (`withDeadlineAfter`) | Low | US-E2 | `018a412` |
| FIX-APP-005 | The server-verified `userId` was lost | Medium | US-D1 | `943eb5d` |
| FIX-APP-006 | Command and chat used different sessions | Medium | US-D2 | `73da360` |
| FIX-APP-007 | Blocking RPCs without deadline (hangs/ANR) | High | US-E2 | `40c3c55` |
| FIX-APP-008 | Startup crash-loop on invalidated keystore | Critical | US-E1 | `6ff15aa` |
| FIX-APP-009 | Accessibility automation inaccuracies | High | — | `1d1426e` |
| FIX-APP-010 | Opens the profile photo instead of the chat (WhatsApp) | High | — | `92b9a79` |
| FIX-APP-011 | WhatsApp deep link fails without E.164 format | High | — | `fd4fb2e` |
| FIX-APP-012 | Speech recognition in the wrong language | Medium | — | `3830103` |
| FIX-APP-013 | Wrong validation message on empty login | Low | — | `f55169e` |

---

> **US-E3 epic (FIX-APP-001 → 002 → 003):** these three fixes share a single design decision rolled out in stages. `FIX-APP-001` moves token refresh off the hot path (cache-only interceptor), which intentionally opens a functional gap that `FIX-APP-002` (commands) and `FIX-APP-003` (chat) close on each authenticated surface. **Any new authenticated surface must remember to call `refreshIfNeeded()` before the flow, or it will reintroduce the bug.**

---

## FIX-APP-001 — Token refresh on the hot path of every authenticated RPC

- **Commit:** `9198a36` ("fix(auth): move token refresh off the call path") · **US:** US-E3
- **Severity:** High
- **Affected modules:** `app/di/AppContainer.java`, `application/usecase/security/AuthUseCase.java`, test `security/TokenRefreshOffPathTest.java`

### Bug description
The gRPC interceptor read the token via `AuthUseCase::getValidAccessToken`, a `synchronized` method that triggers a blocking token refresh (RPC). Any protected call could block waiting on a slow network, under a lock that serializes the remaining calls (ANR risk / cascading UI latency).

### Root cause
The interceptor's supplier (`tokenSupplierHolder`) pointed at the refresh-aware, blocking logic: the refresh happened **inside** the call's hot path.

### Applied solution
- Added `getCachedAccessToken()`: reads `tokenStore` without issuing an RPC (may return an expired token).
- Renamed/isolated the blocking logic as `refreshIfNeeded()`, to be invoked **off** the hot path.
- The interceptor holder now points at `getCachedAccessToken`, so the interceptor never blocks.

### Steps to reproduce (original bug)
On a slow or unstable network, fire an authenticated RPC right when the token is about to expire → the calling thread blocks while it refreshes.

### Verification / test steps
Test `cachedAccessToken_neverCallsGateway_evenWhenExpired`: returns the cached token (even when "expired") without touching the gateway. Confirm the interceptor issues no refresh RPC.

### Impact / affected areas
Every authenticated RPC path. UI responsiveness under degraded networks.

### Notes (regressions / dependencies)
Intentionally introduces a functional gap: if nobody calls `refreshIfNeeded()` before the flow, a cached/expired token is sent. This is exactly what `FIX-APP-002` and `FIX-APP-003` close.

---

## FIX-APP-002 — Expired token sent before the autonomous command loop

- **Commit:** `cf74bea` ("fix(auth): refresh-ahead before the authenticated command loop") · **US:** US-E3 (follow-up)
- **Severity:** High
- **Affected modules:** `app/repository/CommandRepository.java`, `app/di/AppContainer.java`, `app/overlay/FloatingBubbleService.java`, `app/viewmodel/ChatViewModelFactory.java`, `application/port/in/security/AuthPortIn.java`, `AuthUseCase.java`, test `CommandRepositoryAutonomousTest`

### Bug description
After moving the interceptor to *cache-only* (`FIX-APP-001`), running an autonomous command right after token expiry sent a stale token to the backend → the loop's first protected RPC failed on authentication.

### Root cause
`CommandRepository` had no reference to `AuthPortIn` and did not prime the token before the command loop.

### Applied solution
- `AuthPortIn` is injected into all `CommandRepository` constructors.
- At the start of `executeAutonomous`, on the background executor, `authUseCase.refreshIfNeeded()` is called (null-checked) before the first RPC. A refresh failure falls through the shared catch as a normal abort.
- `refreshIfNeeded()` is exposed on the `AuthPortIn` interface.

### Steps to reproduce (original bug)
Wait for the token to expire and run an autonomous voice command → without the fix, the first execution fails on an expired token.

### Verification / test steps
Test `refreshesTokenBeforeFirstCommandRpc` with `InOrder`: `refreshIfNeeded()` is invoked before `useCase.execute(...)`.

### Impact / affected areas
Autonomous command loop (ReAct). Continuity of the authenticated session.

### Notes (regressions / dependencies)
Changes `CommandRepository` constructor signatures (updated in this commit). The null-check suggests some path may pass a null `authUseCase`. Depends on `FIX-APP-001`.

---

## FIX-APP-003 — Expired token sent when sending a chat message

- **Commit:** `a882c30` ("fix(auth): refresh-ahead before chat send") · **US:** US-E3 (chat follow-up)
- **Severity:** High
- **Affected modules:** `app/repository/ChatRepository.java`, `app/overlay/FloatingBubbleService.java`, `app/viewmodel/ChatViewModelFactory.java`, test `ChatRepositoryRefreshTest.java`

### Bug description
Sending a chat message immediately after token expiry sent a stale cached token → the `messageChat` RPC failed on authentication. Same gap as `FIX-APP-002`, but on the chat surface (which that fix did not cover).

### Root cause
`ChatRepository` did not know about `AuthPortIn` and did not prime the token (an exact mirror of the command problem).

### Applied solution
- `AuthPortIn` is injected into `ChatRepository`.
- In `askAtom`, on the background executor, `refreshIfNeeded()` is called before `streamChatUseCase.messageChat(...)`, replicating the `CommandRepository` pattern.

### Steps to reproduce (original bug)
Expire the token and send a chat message → without the fix, the reply fails on an expired token.

### Verification / test steps
Test `refreshesTokenBeforeChatRpc` with `CountDownLatch` + `InOrder`: `refreshIfNeeded()` before `messageChat(...)`.

### Impact / affected areas
Chat surface. Completes US-E3 coverage together with `FIX-APP-002`.

### Notes (regressions / dependencies)
Changes the `ChatRepository` constructor signature (4 args). Depends on `FIX-APP-001`.

---

## FIX-APP-004 — Test regression from gRPC deadlines (`withDeadlineAfter`)

- **Commit:** `018a412` ("fix(test): stub withDeadlineAfter") · **US:** US-E2 (regression)
- **Severity:** Low (test-only; no production change)
- **Affected modules:** `test/.../grpc/AuthGrpcAdapterTest.java`

### Bug description
After adding gRPC deadlines (`FIX-APP-007`), `AuthGrpcAdapterTest` broke: the adapter now chains `stub.withDeadlineAfter(...)` before each RPC, and the stub mock returned `null` by default on that method → `NullPointerException` when chaining `.login()/.register()` on `null`.

### Root cause
The mock had no stub for `withDeadlineAfter`; Mockito returned `null` instead of the stub itself, breaking the fluent chain.

### Applied solution
A `@BeforeEach` that stubs `when(stub.withDeadlineAfter(anyLong, any(TimeUnit))).thenReturn(stub)`, so the fluent chain resolves to the same mock.

### Steps to reproduce (original bug)
Check out between `FIX-APP-007` (`40c3c55`) and this commit, then run `AuthGrpcAdapterTest` → `NullPointerException`.

### Verification / test steps
The `AuthGrpcAdapterTest` suite passes again.

### Impact / affected areas
Test tree only. No functional impact on the app.

### Notes (regressions / dependencies)
A test regression caused by `FIX-APP-007` (US-E2). Not an application bug.

---

## FIX-APP-005 — The server-verified `userId` was lost

- **Commit:** `943eb5d` ("fix(auth): persist and expose server-verified user id") · **US:** US-D1
- **Severity:** Medium
- **Affected modules:** `domain/security/TokenPair.java`, `infrastructure/adapter/grpc/AuthGrpcAdapter.java`, `application/port/out/security/TokenStore.java`, `infrastructure/adapter/out/security/EncryptedTokenStore.java`, `application/usecase/security/AuthUseCase.java`, test `ServerUserIdTest.java`

### Bug description
The server-verified `userId` (present in `AuthResponse`) was lost: it was neither mapped nor persisted, so the app could not expose it. Without it, command and chat cannot share a backend identity (an enabler for `FIX-APP-006`).

### Root cause
`TokenPair` had no `userId` field; `AuthGrpcAdapter.toPair` did not read `r.getUserId()`; `TokenStore`/`EncryptedTokenStore` had no slot for it.

### Applied solution
- `userId` is added to `TokenPair` (overloaded constructor + `fromExpiresIn` with `userId`, backward-compatible via `null`).
- `AuthGrpcAdapter` propagates it.
- `EncryptedTokenStore` persists `server_user_id` (and clears it in `clear()`).
- `AuthUseCase.persist` stores the id if non-empty and exposes `getServerUserId()`.

### Steps to reproduce (original bug)
Log in successfully and then query the user identity → before the fix there was no way to obtain the server id (always `null`/random).

### Verification / test steps
Test `login_persistsServerUserId_andExposesIt`: verifies `saveUserId` and `getServerUserId`.

### Impact / affected areas
User identity persistence. Prerequisite for the shared session (`FIX-APP-006`).

### Notes (regressions / dependencies)
`clear()` must also wipe the user id (covered). Prerequisite of `FIX-APP-006`.

---

## FIX-APP-006 — Command and chat used different sessions

- **Commit:** `73da360` ("fix(session): share one session id across command & chat") · **US:** US-D2
- **Severity:** Medium
- **Affected modules:** `app/di/AppContainer.java`, `app/repository/CommandRepository.java`, `app/overlay/FloatingBubbleService.java`, `app/viewmodel/ChatViewModelFactory.java`, `AuthPortIn.java`/`AuthUseCase.java`, tests `CommandSessionIdTest.java`, `CommandRepositoryAutonomousTest`

### Bug description
`CommandRepository` generated its own `UUID.randomUUID()` per instance, different from the session id used by chat. As a result, command and chat did not share a session/context on the backend (they appeared as two different users/sessions).

### Root cause
Locally auto-generated session id (`private final UUID sessionUserId = UUID.randomUUID();`) in `CommandRepository`, instead of injecting a shared one.

### Applied solution
- The internal random is removed and `sessionUserId` is **injected** into all constructors.
- `AppContainer.getSessionUserId()` derives a stable UUID from `getServerUserId()` (US-D1) via `UUID.nameUUIDFromBytes(serverId)`, with a fallback to the device's persisted pre-login UUID. Thus command and chat share a single session after authentication.

### Steps to reproduce (original bug)
Log in, send a chat message and then a command → on the backend they appear as two different users/sessions (mismatched ids).

### Verification / test steps
Test `autonomousLoop_usesTheInjectedSharedSessionId`: the `execute` RPC receives the injected id, not a random one.

### Impact / affected areas
Session coherence between command and chat. Shared context on the backend.

### Notes (regressions / dependencies)
Changes multiple `CommandRepository` constructors (visibility widened to `public`; `MainThreadPoster` now `public`). Depends on `FIX-APP-005`. The `nameUUIDFromBytes` derivation changes the effective id between pre-login and post-login (an expected session transition).

---

## FIX-APP-007 — Blocking RPCs without deadline (hangs/ANR)

- **Commit:** `40c3c55` ("fix(grpc): deadlines on all blocking RPCs") · **US:** US-E2
- **Severity:** High
- **Affected modules:** `infrastructure/adapter/grpc/AuthGrpcAdapter.java`, `infrastructure/adapter/grpc/InteractionGrpcAdapter.java`, test `InteractionGrpcAdapterTest`

### Bug description
No blocking RPC (`register`/`login`/`authenticateWithGoogle`/`refreshToken` and `executeCommand`/`transcribe`) had a deadline → against a hung network or an unresponsive server, the call blocked indefinitely (ANR / endless spinner).

### Root cause
The blocking gRPC stubs were invoked without `withDeadlineAfter(...)`.

### Applied solution
Explicit deadlines: 15s on the auth RPCs and 30s (`COMMAND_DEADLINE_SECONDS`/`TRANSCRIBE_DEADLINE_SECONDS`) on interaction, chaining `withDeadlineAfter(...)` before each call.

### Steps to reproduce (original bug)
Simulate a server that accepts the connection but never responds → without a deadline the call never returns.

### Verification / test steps
Test `executeCommand_setsADeadlineOnTheCall`: captures the `Context` `Deadline` in the fake service and requires it to be non-null.

### Impact / affected areas
All blocking RPCs. Robustness under degraded networks.

### Notes (regressions / dependencies)
- Caused the test regression fixed in `FIX-APP-004` (`018a412`).
- Possible: legitimately long commands exceeding 30s are now cancelled (`DEADLINE_EXCEEDED`); that status should be handled in error mapping.

---

## FIX-APP-008 — Startup crash-loop on invalidated keystore

- **Commit:** `6ff15aa` ("fix(security): recover from invalidated keystore") · **US:** US-E1
- **Severity:** Critical
- **Affected modules:** `infrastructure/adapter/out/security/EncryptedTokenStore.java`, test `EncryptedTokenStoreRecoveryTest.java`

### Bug description
If the Keystore master key became invalidated (device credential reset, restore to a new device), `EncryptedSharedPreferences.create` threw and the constructor rethrew `IllegalStateException` → the app crashed at launch, **in a loop**, with no recovery. The app was unusable without reinstalling.

### Root cause
Opening the encrypted prefs without a recovery strategy: a single attempt, and the failure was fatal.

### Applied solution
`openWithRecovery(factory, wipe)`: tries to open (attempt 0); on failure it runs `wipeCorruptStore` (deletes the prefs file and the `_androidx_security_master_key_` master-key entry from the AndroidKeyStore, best-effort) and retries (attempt 1). If the retry also fails, it throws `IllegalStateException`. The user simply logs in again instead of being locked out.

### Steps to reproduce (original bug)
Invalidate the master key (change/remove the screen lock on certain devices, or restore to a new device) and open the app → crash at launch before the fix.

### Verification / test steps
Tests `recoversOnceWhenFirstAttemptFails` (2 attempts + wipe) and `rethrowsWhenRecoveryAlsoFails`.

### Impact / affected areas
App startup and secure token storage. Eliminates the crash-loop.

### Notes (regressions / dependencies)
- The wipe deletes the persisted tokens → the user loses the session and must re-login (accepted/intended behavior).
- The hardcoded alias `_androidx_security_master_key_` depends on the `androidx.security` version; if it changes, deleting the key would fail silently (though deleting the file usually suffices).

---

## FIX-APP-009 — Accessibility automation inaccuracies

- **Commit:** `1d1426e` ("fix(a11y): accent-folding, word-boundary tap ranking, multi-window editable resolution, submit fallback")
- **Severity:** High (composite fix)
- **Affected modules:** `infrastructure/adapter/accessibility/AtomAccessibilityService.java`, test `AtomAccessibilityServiceRankTest.java`

### Bug description
Four automation-precision defects fixed at once:
1. Accent/case-sensitive name matching: it did not find "María" if the needle came without the accent.
2. A short needle like "ana" substring-matched against "susana" → tapped the wrong contact.
3. When typing in search bars (e.g., Google), the active window is the IME popup, not the editable field → it did not resolve the field and did not type.
4. Submitting the search failed in apps with a non-standard IME (e.g., TikTok), where `IME_ENTER` only existed on API 30+.

### Root cause
1. Used `toLowerCase` without normalizing accents.
2. Ranking only exact/prefix/substring, with no word tier or length guard.
3. `resolveEditable` only looked at `getRootInActiveWindow`.
4. `submit` only tried `ACTION_IME_ENTER` and then a localized button.

### Applied solution
1. `TextNormalizer.fold` (lowercase + strip accents) applied to haystack and needle.
2. New `RANK_WORD` tier (whole token) between prefix and substring, and `MIN_SUBSTRING_NEEDLE=4` that blocks substring-matching of short needles.
3. `resolveEditable` now traverses **all** windows (`getWindows()`) via `editableInRoot`.
4. `submit` adds an intermediate `ACTION_CLICK` fallback on the focused field, with per-branch logging. Also better tap-target selection and corrected node handling (`findScrollable` returns `obtain` and recycles correctly, avoiding use-after-recycle/leaks).

### Steps to reproduce (original bug)
1. Ask to open the chat of "Maria" without the accent.
2. Ask for "ana" with a contact "Susana" in the list → opens Susana.
3. Dictate text into the Google search bar → it does not type.
4. Search in TikTok → text is placed but not sent.

### Verification / test steps
Ranking tests (`rankFor`): exact/prefix/word/substring and the short-needle guard ("ana" vs "susana" = no match). Manual verification of multi-window typing and submit-by-click.

### Impact / affected areas
Accessibility service: contact matching, typing and submitting in third-party apps.

### Notes (regressions / dependencies)
- The `submit` `ACTION_CLICK` fallback could trigger unwanted behavior in fields where a click does not equal submit.
- Node recycling handling changed across several paths (regression risk if any path is uncovered).
- `MIN_SUBSTRING_NEEDLE=4` may exclude legitimate 3-letter-needle matches.
- Introduces `TextNormalizer.fold`, on which `FIX-APP-010` depends.

---

## FIX-APP-010 — Opens the profile photo instead of the chat (WhatsApp)

- **Commit:** `92b9a79` ("fix(a11y): skip profile-image avatar nodes in contact DFS")
- **Severity:** High
- **Affected modules:** `infrastructure/adapter/accessibility/AtomAccessibilityService.java`

### Bug description
In WhatsApp, the name-search DFS matched the avatar node (an `ImageView` whose `contentDescription` is "Foto de perfil de María") and tapped it → it opened the profile photo instead of the chat.

### Root cause
The DFS evaluated any node whose text/description matched as a candidate, without distinguishing the avatar (which carries the name in its description) from the chat row.

### Applied solution
`isStructuralImage(node)` (via `isProfileImageDesc` with folded markers: "foto de perfil", "avatar", "profile picture", etc.) excludes the avatar from being chosen as a match, but **still traverses** its children. Not all `ImageView`s are discarded (icon buttons "Settings"/"History" remain tappable via their `contentDescription`).

### Steps to reproduce (original bug)
Ask to open the chat of a WhatsApp contact whose row has an avatar described "Foto de perfil de X" → the photo opens.

### Verification / test steps
No unit test in the commit; manual verification (opens the chat, not the photo) and inspection of `isProfileImageDesc`. Limitation: marker coverage is per-language (es/en); other languages are not covered.

### Impact / affected areas
The "open contact chat" action in WhatsApp via accessibility.

### Notes (regressions / dependencies)
- Hardcoded marker list, dependent on WhatsApp's language/version; if WhatsApp changes the `contentDescription` text, the skip stops applying.
- Builds on `TextNormalizer.fold` introduced in `FIX-APP-009` (dependency).

---

## FIX-APP-011 — WhatsApp deep link fails without E.164 format

- **Commit:** `fd4fb2e` ("fix(action): normalize WhatsApp number to E.164 and fall back to home-screen search")
- **Severity:** High
- **Affected modules:** `infrastructure/adapter/action/AndroidActionExecutor.java`, `app/di/AppContainer.java`, `infrastructure/adapter/out/device/TelephonyE164Normalizer.java` (new), `application/port/out/PhoneNumberNormalizerPortOut.java` (new), `res/values*/strings.xml`

### Bug description
The `wa.me/<number>` deep link was built with the raw local number (`digitsOnly`). `wa.me` **requires E.164 format**; a non-E.164 local number caused WhatsApp to open its home screen (or a targetless `wa.me`) instead of the chat. With no number, it also used a `wa.me/?text=` with no recipient.

### Root cause
Missing E.164 normalization and an inadequate fallback (targetless `wa.me`).

### Applied solution
- New `PhoneNumberNormalizerPortOut` + `TelephonyE164Normalizer` adapter (infers the device region), injected into `AndroidActionExecutor`.
- `sendWhatsApp` normalizes to E.164; if E.164 is available it uses the chat deep link; if no number is normalizable, it **opens WhatsApp's home screen** (the `com.whatsapp` package launch intent) so the backend ReAct loop can search the contact by name on the next turn.
- If WhatsApp is not installed, it fails with a clear message. New strings `action_whatsapp_search_fallback` (es/en).

### Steps to reproduce (original bug)
Ask to send a WhatsApp to a contact whose number is in local format (no country prefix) → it opens WhatsApp's home/compose with no target instead of the chat.

### Verification / test steps
No test in the diff; manual verification with a local number (should open the chat) and with a name-only contact (should open WhatsApp's home with the fallback message). Recommended: a normalizer test.

### Impact / affected areas
The WhatsApp message-sending action.

### Notes (regressions / dependencies)
- Changes the `AndroidActionExecutor` constructor signature (3 args).
- Region inference depends on the device SIM/locale: a local number from another country may be normalized incorrectly.

---

## FIX-APP-012 — Speech recognition in the wrong language

- **Commit:** `3830103` ("fix(voice): recognize speech in the app's chosen language")
- **Severity:** Medium
- **Affected modules:** `infrastructure/adapter/voice/AndroidSpeechRecognizer.java`

### Bug description
The recognizer passed a `Locale` object (`Locale.getDefault()`) to `EXTRA_LANGUAGE`, which **must be a BCP-47 String**. The object was silently ignored → the service fell back to its default (en-US) and mis-transcribed Spanish on phones whose system was set to another language.

### Root cause
Wrong type on the extra (`Locale` instead of a String tag) and, additionally, it followed the device locale instead of the app's language preference.

### Applied solution
`resolveLanguageTag()` reads `AtomPreferences.getLanguage()` (the source of truth): explicit `en`/`es` wins; `system` or `null` → `Locale.getDefault().toLanguageTag()`. It is set as a String on `EXTRA_LANGUAGE` and also on `EXTRA_LANGUAGE_PREFERENCE`.

### Steps to reproduce (original bug)
English phone, app set to Spanish, dictate in Spanish → it transcribes as English (wrong text).

### Verification / test steps
No unit test (depends on the Android service); manual verification: app in Spanish + phone in English transcribes Spanish correctly.

### Impact / affected areas
Speech recognition (STT) for non-English users.

### Notes (regressions / dependencies)
Depends on `AtomPreferences` exposing `getLanguage()`/`LANGUAGE_SYSTEM`. Low regression risk.

---

## FIX-APP-013 — Wrong validation message on empty login

- **Commit:** `f55169e` ("fix(auth): show proper validation message for empty login fields")
- **Severity:** Low
- **Affected modules:** `app/LoginActivity.java`, `res/values/strings.xml`

### Bug description
With empty login fields, the error message showed `auth_email_hint` (the literal "Email") instead of a useful validation message.

### Root cause
Wrong string key in the empty-fields branch of `submit()`.

### Applied solution
The new string `auth_fields_required` ("Completa todos los campos.") is used.

### Steps to reproduce (original bug)
Tap login with an empty email or password → it shows "Email".

### Verification / test steps
Manual: leave a field empty and submit → it shows "Completa todos los campos.".

### Impact / affected areas
Validation messaging on the login screen. A cosmetic/UX defect.

### Notes (regressions / dependencies)
The new string was added to `values/strings.xml`; it should be confirmed it also exists in `values-es` (not present in the diff — a possible localization gap, although the default value is already in Spanish).

---

_Last updated: 2026-06-26._

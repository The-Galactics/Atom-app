---
name: implementation-gaps
description: Security/user features NOT yet implemented in Atom-app as of 2026-06-24
metadata:
  type: project
---

Confirmed gaps (read the actual code before relying on these — they may close).

**User creation flow is incomplete:**
- `UserUseCase` exists but is NOT wired in `AppContainer` (deliberate, [[server-client-security-split]]).
- No `PasswordEncoderPortOut` adapter on Android (no Argon2/BCrypt). The old `docs/.../ATOM-33-password-hashing-resumen.md` describes a Spring/Maven server impl with `java/` paths and `Argon2PasswordEncoderAdapter` that DOES NOT exist in this Android module.
- No `UserPortOut` adapter, no `UserPortIn` implementation class.
- `UserPortIn.createUser(email, name)` signature does NOT take a password; `UserUseCase.registerUser(email,name,rawPassword)` does NOT implement `UserPortIn`. Contract mismatch.

**Runtime security is built but NOT integrated (the core gap):**
- `DeviceSecurityUseCase` and `InputValidationUsecase` are wired in `AppContainer` but NEVER CALLED by any production caller (only tests). Grep for `verifyDeviceIntegrity`/`validateFormFields` returns only tests + the use cases themselves.
- There is NO secure-termination flow: nothing calls `FloatingBubbleService.stopOverlay()` or `AtomAccessibilityService.disableSelf()` on HIGH risk. `DeviceSecurityException` is thrown but never caught/acted upon.
- `shouldBlockOperation` THROWS on HIGH risk (fail-loud) instead of returning a verdict; callers must wrap in try/catch.

**Detection weaknesses (DeviceInspectorAdapter):**
- `detail` field leaks specifics (ROOT_ACCESS etc.) into the exception message — must NOT reach UI/logs.
- Root: no Magisk/SuperSU package list, no build test-keys check, no `id`/mount-rw probe. Single ROOT_BINARIES array + `which su`.
- No fail-closed default: exceptions in probes are swallowed (`catch(Exception ignored)`) -> treated as not-rooted.
- Hardcoded emulator strings inline in adapter (Build.* literals) rather than a config list.

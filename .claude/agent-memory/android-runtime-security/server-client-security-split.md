---
name: server-client-security-split
description: ADR-001 decision — password hashing and user identity are server-side; deferred on the Android client
metadata:
  type: project
---

ADR-001 (`docs/es/tecnico/issues/ADR-001-migracion-hexagonal-android.md`, updated 2026-06-14) decided:
- Password hashing MUST happen on the server, never on the Android client. `Argon2PasswordEncoderAdapter` is excluded from Android.
- `UserUseCase` + `PasswordEncoderPortOut` are intentionally NOT wired in `AppContainer`; user auth/identity is server-side and deferred until an auth strategy is defined.
- The app currently uses a random per-session `userId`/`chatId` (see `AppContainer.loadOrCreateUuid`) to satisfy the chat use-case contract — there is no real user identity yet.
- `DeviceSecurityAspect` + `@SecureOperation` (Spring AOP) are excluded; rule enforcement is reimplemented as explicit guard calls on Android.

**Why:** Spring Security crypto isn't on the Android classpath, and client-side hashing is a security anti-pattern.

**How to apply:** Do NOT recommend building an Argon2/password adapter on Android. When documenting "user creation", note the domain/use-case/ports exist for portability/testing but the live registration path belongs to the backend. The on-device security scope is runtime attestation (root/emulator/proxy) + input validation, NOT credential storage.

**Audit finding (2026-06-24) — the "deferred to backend" work does NOT exist in the backend either.** Verified against code, not docs:
- `Atom-agent` has NO authn/authz/hashing: grep for `authenticate|verify_token|hash_password|verify_password|jwt|login` → no matches. The only `Authorization: Bearer` (`infrastructure/provider_clients.py`) is for OUTBOUND LLM/TTS provider API keys, not for authenticating the Android client.
- gRPC contract (`atom_agent.proto` / `ai.proto`) exposes only `ExecuteCommand`/`StreamChat`/`Transcribe`/`Synthesize`. No Register/Login/Session RPC. `user_id` is an unsigned plain `string` in the request → trivially spoofable; backend trusts it and there is no interceptor (`server.py` only aborts with UNAVAILABLE/INTERNAL, never UNAUTHENTICATED/PERMISSION_DENIED).
- So authn/authz is ABSENT system-wide, not merely deferred. Closing it is mostly `Atom-agent` work (proto RPCs + hashing + per-user authz interceptor + TLS), per ADR-001's split. On the client side the only real bugs are the dead `UserPortIn` contract (`createUser(email,name)` lacks password; `UserUseCase` doesn't implement it) and `UserUseCase` not being wired in `AppContainer`.

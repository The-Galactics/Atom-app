---
name: security-architecture-map
description: Package locations and wiring of security ports/adapters/use cases in Atom-app (Android hexagonal)
metadata:
  type: project
---

Atom-app Android client uses strict hexagonal layering (per [[server-client-security-split]] ADR-001):
`com.atom.domain` (pure POJO/records, no framework) -> `com.atom.application` (ports + use cases + exceptions, no Android) -> `com.atom.infrastructure` (adapters, may use Android APIs) -> `com.atom.app` (presentation + manual DI).

**Why:** Keeps detection logic JVM-unit-testable; adapters isolate `android.os.Build`/`File`/`Runtime`.

**How to apply:** When adding security code, put verdict logic in use cases, OS access in `infrastructure/adapter/out/...`, and wire in `com.atom.app.di.AppContainer`.

Security components (verified 2026-06-24):
- Domain: `domain/model/security/DeviceSecurityStatus.java` (record, holds risk scoring `resolveRisk`: rooted=3, emulator=2, proxy=1; HIGH>=3, MEDIUM>=1), `domain/model/security/ValidationResult.java` (record), `domain/utils/RiskLevel.java` (enum LOW/MEDIUM/HIGH), `domain/model/User.java`.
- App ports IN: `application/port/in/security/DeviceSecurityPort.java`, `.../InputValidationPort.java`, `application/port/in/UserPortIn.java`.
- App ports OUT: `application/port/out/security/DeviceInspectorPort.java` (isRooted/isRunningOnEmulator/isProxyActive), `.../security/PasswordEncoderPortOut.java`, `application/port/out/UserPortOut.java`.
- Use cases: `application/usecase/security/DeviceSecurityUseCase.java` (implements DeviceSecurityPort), `.../InputValidationUsecase.java` (implements InputValidationPort — NO separate adapter, regex lives in the use case), `application/usecase/UserUseCase.java`.
- Exceptions: `application/exception/DeviceSecurityException.java` (wraps status), `.../InputValidationException.java` (wraps error list).
- Infra adapter: `infrastructure/adapter/out/device/DeviceInspectorAdapter.java` (ROOT_BINARIES array + Build.* emulator checks + ProxySelector). Only security adapter that exists.
- DI: `app/di/AppContainer.java` constructs DeviceSecurityUseCase + InputValidationUsecase; exposes getters.
- Protected services: `app/overlay/FloatingBubbleService.java` (overlay; idempotent `stopOverlay()` already exists -> removes views, stopForeground, stopSelf), `infrastructure/adapter/accessibility/AtomAccessibilityService.java` (static `getInstance()`, OS-owned lifecycle, `disableSelf()` available on API 24+).

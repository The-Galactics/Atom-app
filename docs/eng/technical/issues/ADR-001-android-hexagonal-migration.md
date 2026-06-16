# ADR-001 — Adopting a Hexagonal Core in the Atom Android App

- **Status:** Accepted
- **Date:** 2026-06-14
- **Deciders:** Architecture (Principal Architect), Mobile team (The-Galactics)
- **Context source:** Migration of backend hexagonal code (from the Spring Boot project `Atom`) into the Android app `Atom_app`.

---

## 1. Context

The Atom system has three parts:

1. **Android client** (`Atom_app`) — the app the user runs.
2. **Java/Spring Boot backend** (`Atom`) — hexagonal/ports-and-adapters service.
3. **Python AI agent** (`Atom-agent`) — the AI runtime, reached over **gRPC** (`ai.proto`, service `AtomAgentService`).

During development, a raw copy of the backend's `domain`, `application`, and `infrastructure` packages was pasted into the Android module under `src/main/java/com/atom/`. That code depends on **Spring, Lombok, Spring Data MongoDB, AspectJ, jakarta.validation, and Spring Security** — none of which are on the Android classpath. As a result:

- The Android module **does not compile**.
- `build.gradle.kts` is **malformed** (a duplicated dependency fragment and an orphan `}` dangle after the `dependencies` block).
- The gRPC adapter references generated stubs (`AtomAgentServiceGrpc`) that **are never generated** — there is no protobuf/grpc Gradle codegen wired up, despite the `io.grpc.*` runtime dependencies and `src/main/proto/ai.proto` being present.

The app already has a **working MVVM presentation layer** under `com.atom.app` (MainActivity, SettingsActivity, ChatViewModel with LiveData, ChatRepository over Retrofit, ApiService `GET api/chat?prompt=`, RetrofitClient using `BuildConfig.BASE_URL`).

We need a target architecture that (a) yields a **compilable** module, (b) **preserves the working UI**, and (c) **adopts the hexagonal core** properly so the mobile team can extend it cleanly.

---

## 2. Decision

Adopt an **Android-adapted hexagonal (Clean) architecture** with four layers, and **keep the existing MVVM as the presentation layer wrapping the hexagonal core** (rather than rewriting the UI on top of new abstractions). This is the lowest-risk path.

### 2.1 Layer map

```
com.atom.domain          <- enterprise rules. Pure Java POJOs. ZERO framework deps.
com.atom.application     <- application rules. Ports (in/out) + use-cases + exceptions. ZERO Android/framework deps.
com.atom.infrastructure  <- adapters. Talk to the outside world (gRPC, device sensors). MAY use Android APIs.
com.atom.app             <- presentation (EXISTING). Activities + ViewModel + Repository. Android UI.
```

**The dependency rule:** dependencies point inward only. `app -> infrastructure -> application -> domain`. `domain` depends on nothing. `application` depends only on `domain`. Nothing inner imports anything from `app` or `infrastructure`.

### 2.2 What gets MIGRATED as-is (framework-free, reusable)

- **Domain:** `Chat`, `Message`, `User`, `security/DeviceSecurityStatus`, `security/ValidationResult`, `utils/RiskLevel`. These are pure POJOs/records using only `java.*`.
- **Application ports:** all of `port/in/*`, `port/out/*`, `port/in/security/*`, `port/out/security/*`.
- **Application exceptions:** `DeviceSecurityException`, `InputValidationException`.
- **Use-cases (no Spring annotation already):** `ExternalCommandUseCase`, `ExternalMessageUseCase`, `UserUseCase`.

### 2.3 What gets ADAPTED (de-Springified keepers)

- **`InputValidationUsecase`** and **`DeviceSecurityUseCase`** — drop the `@Service` annotation; logic is portable as-is.
- **`InteractionGrpcAdapter`** — drop `@Component`; replace constructor `@Value("${grpc.agent.host/port}")` injection with **plain constructor parameters fed from `BuildConfig`**; replace `javax.annotation.@PostConstruct`/`@PreDestroy` lifecycle with **explicit `init()` / `shutdown()` methods** called by the composition root. gRPC channel logic is otherwise unchanged.
- **`DeviceInspectorAdapter`** — drop `@Component`. **Critically, reimplement `isRunningOnEmulator()` against `android.os.Build`** (FINGERPRINT / MODEL / MANUFACTURER / BRAND / PRODUCT / HARDWARE) rather than `System.getProperty("ro.product.*")`, which does not work on a real Android runtime. Root-binary and proxy checks port directly.

### 2.4 What is EXCLUDED (stays server-side) and why

| Excluded | Reason it stays on the backend |
|---|---|
| `SecurityController` (`@RestController`) | HTTP endpoint layer; the client *calls* APIs, it doesn't host them. |
| `UserEntity`/`ChatEntity`/`MessageEntity` | Spring Data MongoDB persistence; the database lives server-side. |
| `Argon2PasswordEncoderAdapter` | Spring Security crypto; **password hashing must happen on the server**, never on the client. |
| `DeviceSecurityAspect` + `@SecureOperation` | Spring AOP; not available on Android. Reimplement enforcement as an explicit guard call where needed. |
| `Securityexceptionhandler` (`@RestControllerAdvice`) | Global HTTP error mapping (`ProblemDetail`); a server concern. |
| `dto/req/*`, `dto/res/*` | `jakarta.validation` request/response DTOs bound to the HTTP contract. |
| `UserInAdapter`, `UserOutAdapter`, `UserMapper` | Empty stubs in source; nothing to migrate. |

### 2.5 Dependency Injection without Spring

Android has no Spring container. We replace it with a **manual composition root** (a hand-written factory / service-locator, e.g. `AppContainer`) that constructs the object graph once and hands ready-built use-cases to the presentation layer. This keeps constructor injection (testable, explicit) without adding Dagger/Hilt — appropriate for the current graph size. Hilt can be introduced later if the graph grows; this is a non-breaking future option.

### 2.6 How the presentation layer connects

The existing **`ChatRepository` becomes the bridge** between the Android UI and the hexagonal core. Instead of (or in addition to) its Retrofit call, it delegates to the inbound ports / use-cases obtained from the composition root. `ChatViewModel` and the Activities are unchanged in shape — they keep exposing LiveData. This preserves the working UI while routing real work through the hexagonal core.

> **Lowest-risk staging note:** the existing Retrofit `GET api/chat?prompt=` path is left intact and functional. The hexagonal/gRPC path is wired underneath so the team can switch the repository's backing implementation without touching the UI. This avoids a big-bang cutover (a *strangler-fig* style adoption).

---

## 3. Build-system decisions

1. **Fix `build.gradle.kts`** — remove the dangling duplicated dependency fragment and orphan brace so the script parses.
2. **Wire gRPC/protobuf codegen** — add the `com.google.protobuf` Gradle plugin with `protoc` + the gRPC Java + `javalite` codegen, targeting **`grpc-protobuf-lite`** (already a dependency — the lite runtime is the Android-appropriate choice). The proto's `java_package = com.atom.infrastructure.adapter.grpc` means generated stubs land exactly where `InteractionGrpcAdapter` expects.
3. **`BuildConfig` fields for gRPC** — add `GRPC_HOST` / `GRPC_PORT` build config fields (sourced from `local.properties`, same pattern as `BASE_URL`) so the gRPC adapter is configured without Spring property injection.
4. **AGP/Gradle alignment** — AGP 8.7.3 with Gradle 9.5.1 is a known mismatch; the build verification step must confirm resolution and adjust the wrapper or AGP if the build refuses to configure.

---

## 4. Consequences

**Positive**
- Module compiles; UI preserved; clean dependency rule enforced.
- Domain + application layers are **unit-testable on the JVM** with no Android/Spring dependencies.
- The gRPC contract stays shared and regenerated from the single source `ai.proto`.
- Clear, documented seam (`ChatRepository`) for swapping REST ↔ gRPC backends.

**Negative / costs**
- Manual DI factory is boilerplate the team must maintain (mitigated: small graph; Hilt is a later option).
- `DeviceInspectorAdapter` needs a real Android reimplementation of emulator detection (tracked as a TODO).
- Some duplication between domain models here and backend entities — acceptable; the client owns its own domain copy (a normal trait of distributed hexagonal systems).

**Risks / follow-ups**
- gRPC plaintext (`usePlaintext()`) is fine for local dev but **must use TLS in production**.
- The three API surfaces (Retrofit REST, backend `/api/v1/security`, gRPC) should converge over time; out of scope for this ADR.

---

## 5. Alternatives considered

1. **Rewrite the UI on top of new abstractions** — rejected: high risk, no functional gain now.
2. **Multi-module Gradle split (domain/app as separate modules)** — deferred: cleaner long-term, but more setup than needed for a single-team app today; the package-level dependency rule gives most of the benefit now.
3. **Keep Spring via a server-side BFF and let Android stay thin** — viable but doesn't address the immediate "make this module compile with the hexagonal core" goal; the client still needs client-side device-security and gRPC.

---

## 6. Update — decisions executed (2026-06-14)

Two follow-up decisions were taken and implemented after this ADR was accepted:

1. **Chat transport: gRPC cutover (supersedes the strangler-fig staging note in §2.6).** The Retrofit/REST path was removed entirely — `ApiService`, `RetrofitClient`, the `BASE_URL` build config fields, and the Retrofit/OkHttp/Gson dependencies are deleted. `ChatRepository` now delegates to `ExternalMessageUseCase` (`StreamChatPortIn`) over gRPC, collecting the server-streamed token `Stream<String>` into a single response so the UI is unchanged. The composition root described in §2.5 was implemented as `AppContainer`, hosted on an `AtomApp` `Application` subclass and injected into `ChatViewModel` via a `ChatViewModelFactory` (`ViewModelProvider.Factory`).

2. **Authentication / password hashing: server-side, out-of-scope for the app (deferred).** `UserUseCase` and `PasswordEncoderPortOut` are intentionally **not** wired into the composition root, and **no Android Argon2 adapter is built**. User authentication and password hashing remain a backend responsibility (consistent with the exclusion of `Argon2PasswordEncoderAdapter` in §2.4). Because the app has no user identity yet, `ChatRepository` uses a per-session random `userId`/`chatId` to satisfy the use-case contract. Wiring a real user identity is future work, gated on the auth strategy being defined.

> **Note on `usePlaintext()` / TLS:** the production-TLS follow-up from §4 still stands and is unaffected by the cutover.

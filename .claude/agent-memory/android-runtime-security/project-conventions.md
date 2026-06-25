---
name: project-conventions
description: Atom-app Android build/test/doc conventions that affect security code
metadata:
  type: project
---

- Android Gradle (`build.gradle.kts`), AGP 8.7.3, `com.android.application`. NOT Spring Boot, NOT Maven — ignore Spring references in older ATOM-33/ATOM-36 docs; those describe the server module.
- Language: Java 21 (source/target 21). minSdk 26, targetSdk/compileSdk 35. Namespace `com.atom.app`.
- Tests: JUnit 5 (Jupiter) via `useJUnitPlatform()`, Mockito 5 (`mockito-junit-jupiter`), AssertJ 3. Security tests live under `src/test/java/com/atom/hashSecurity/` and `src/test/java/com/atom/validationSecurity/`.
- DI: manual composition root `com.atom.app.di.AppContainer`, hosted in `AtomApp` (Application subclass). No Hilt/Dagger. Constructor injection.
- Docs: Spanish, under `Atom-app/docs/es/tecnico/issues/seguridad/` (implementation summaries) and `docs/es/tecnico/pruebas/seguridad/` (Gherkin). ADRs under `docs/es/tecnico/issues/`.
- Build commands run from `Atom-app/`: `./gradlew build`, `./gradlew test`.

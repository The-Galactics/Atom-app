# Android Runtime Security — Memory Index

- [Security Architecture Map](security-architecture-map.md) — where the security ports/adapters/use cases live in Atom-app and how they wire
- [Implementation Gaps](implementation-gaps.md) — what is NOT implemented yet (user creation, secure termination, password adapter)
- [Project Conventions](project-conventions.md) — Android Gradle, minSdk 26, JUnit5/Mockito/AssertJ, manual DI, doc location
- [Server-vs-Client Security Split](server-client-security-split.md) — ADR-001 decision: password hashing + user identity are server-side, deferred on Android

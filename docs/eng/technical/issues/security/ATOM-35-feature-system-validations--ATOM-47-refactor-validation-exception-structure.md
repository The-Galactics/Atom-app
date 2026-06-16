# ATOM-36 — Refactor Global Validation and Exception Handling Structure

## Metadata

| Field   | Value                                             |
|---------|---------------------------------------------------|
| Issue   | ATOM-36                                           |
| Type    | Refactor                                          |
| Sprint  | 1                                                 |
| Date    | June 2026                                         |
| Related | ATOM-35 (Input Validation & Device Security)      |
| Branch  | `refactor/ATOM-36-validation-exception-structure` |

---

## Context

During the development of ATOM-35, validation and exception handling components were generated and distributed across different packages:

- `infrastructure/handler/GlobalExceptionHandler.java` — generic handler for unhandled exceptions.
- `infrastructure/config/SecurityExceptionHandler.java` — handler for domain exceptions and Bean Validation.
- `infrastructure/adapter/in/web/dto/` — input and output DTOs scattered across adapter subfolders.
- `domain/exception/` — domain exceptions without a unified contract.

This distribution created two specific problems: duplicated responsibilities (`@RestControllerAdvice` in two different classes) and difficulty locating validation components as the project expanded.

---

## Objective

Consolidate all validation components and exception handlers into a unified package structure, without altering the application's behavior or the contracts of the exposed APIs.

---

## Changes made

### 1. Removal of duplicated class

`GlobalExceptionHandler.java` was removed from `infrastructure/handler/`.
Its single method (`@ExceptionHandler(Exception.class)`) was absorbed by
`SecurityExceptionHandler.java`, which becomes the project's only `@RestControllerAdvice`.

**Before — two classes with the same annotation:**
```
infrastructure/
├── handler/
│   └── GlobalExceptionHandler.java       ← @RestControllerAdvice (removed)
└── config/
    └── SecurityExceptionHandler.java     ← @RestControllerAdvice
```

**After — a single handler:**
```
infrastructure/
└── config/
    └── SecurityExceptionHandler.java     ← @RestControllerAdvice (unified)
```

---

### 2. New `common/validation` package

The `infrastructure/common/validation/` package was created to centralize the
reusable validation components that do not belong to a specific adapter.

```
infrastructure/
└── common/
    └── validation/
        ├── FormValidationRequestDto.java
        ├── ValidationResponseDto.java
        └── DeviceSecurityResponseDto.java
```

The security validation DTOs were moved out of `adapter/in/web/dto/`
because they are cross-cutting across multiple adapters and are not
exclusive to a single endpoint.

---

### 3. New `common/exception` package

The `infrastructure/common/exception/` package was created as a reference
point for mappings between domain exceptions and HTTP responses.
Domain exceptions remain in `domain/exception/` — this package contains
only infrastructure utilities related to error handling, if needed in the future.

```
domain/
└── exception/
    ├── InputValidationException.java     ← stays in the domain
    └── DeviceSecurityException.java      ← stays in the domain

infrastructure/
└── common/
    └── exception/                        ← reserved for error mappers/utils
```

---

### 4. Final package structure

```
com.atom
├── domain
│   ├── model
│   │   ├── ValidationResult.java
│   │   └── DeviceSecurityStatus.java
│   ├── port
│   │   ├── in
│   │   │   ├── InputValidationPort.java
│   │   │   └── DeviceSecurityPort.java
│   │   └── out
│   │       └── DeviceInspectorPort.java
│   └── exception
│       ├── InputValidationException.java
│       └── DeviceSecurityException.java
│
├── application
│   └── usecase
│       ├── InputValidationUseCase.java
│       └── DeviceSecurityUseCase.java
│
└── infrastructure
    ├── common
    │   ├── validation
    │   │   ├── FormValidationRequestDto.java
    │   │   ├── ValidationResponseDto.java
    │   │   └── DeviceSecurityResponseDto.java
    │   └── exception
    │       └── (reserved for future error mappers)
    ├── adapter
    │   ├── in
    │   │   └── web
    │   │       └── controller
    │   │           └── SecurityController.java
    │   └── out
    │       └── device
    │           └── DeviceInspectorAdapter.java
    ├── annotation
    │   └── SecureOperation.java
    └── config
        ├── SecurityExceptionHandler.java
        └── DeviceSecurityAspect.java
```

---

### 5. `SecurityExceptionHandler.java` — unified version

The resulting handler covers the four error scenarios in order of
specificity. Spring automatically resolves the most specific handler,
so `Exception.class` only acts as a final safety net.

[Related file](/com/atom/infrastructure/config/Securityexceptionhandler.java)

---

### 6. Updated imports in `SecurityController`

After moving the DTOs to `common/validation`, the controller updates its imports:

```java
// Before
import com.atom.infrastructure.adapter.in.web.dto.req.FormValidationRequestDto;
import com.atom.infrastructure.adapter.in.web.dto.res.ValidationResponseDto;
import com.atom.infrastructure.adapter.in.web.dto.res.DeviceSecurityResponseDto;

// After
import com.atom.infrastructure.common.validation.FormValidationRequestDto;
import com.atom.infrastructure.common.validation.ValidationResponseDto;
import com.atom.infrastructure.common.validation.DeviceSecurityResponseDto;
```

---

## Affected files

| Action      | File                                                            |
|-------------|-----------------------------------------------------------------|
| `deleted`   | `infrastructure/handler/GlobalExceptionHandler.java`            |
| `deleted`   | `infrastructure/handler/` (empty folder)                        |
| `moved`     | `adapter/in/web/dto/req/FormValidationRequestDto.java` → `common/validation/` |
| `moved`     | `adapter/in/web/dto/res/ValidationResponseDto.java` → `common/validation/`    |
| `moved`     | `adapter/in/web/dto/res/DeviceSecurityResponseDto.java` → `common/validation/`|
| `modified`  | `config/SecurityExceptionHandler.java` (absorbs generic handler)|
| `modified`  | `adapter/in/web/controller/SecurityController.java` (imports)   |
| `created`   | `infrastructure/common/validation/` (new package)              |
| `created`   | `infrastructure/common/exception/` (new reserved package)      |
| `unchanged` | `domain/exception/InputValidationException.java`               |
| `unchanged` | `domain/exception/DeviceSecurityException.java`                |
| `unchanged` | All use-cases and domain ports                                  |
| `unchanged` | All existing tests                                              |

---

## Verified acceptance criteria

| Criterion                                                                | Status |
|--------------------------------------------------------------------------|--------|
| Validation classes consolidated into a common package                    | ✅     |
| Domain exceptions centralized in `domain/exception`                      | ✅     |
| A single `@RestControllerAdvice` across the whole application            | ✅     |
| Duplicated `GlobalExceptionHandler` removed                              | ✅     |
| Imports and references updated in `SecurityController`                   | ✅     |
| API behavior and contracts unchanged                                    | ✅     |
| Existing tests unmodified                                               | ✅     |

---

## Technical decisions

**Why do domain exceptions stay in `domain/exception`
and are not moved to `infrastructure/common/exception`?**

The `InputValidationException` and `DeviceSecurityException` exceptions
represent business-rule violations. They are thrown by the domain's use-cases
and know nothing about HTTP. Moving them to infrastructure would break the
Dependency Rule of hexagonal architecture: the domain would depend on an
outer layer.

**Why do the validation DTOs go in `common/validation`
and not in `adapter/in/web/dto`?**

`FormValidationRequestDto`, `ValidationResponseDto` and `DeviceSecurityResponseDto`
are cross-cutting contracts that multiple adapters may need to reuse.
Placing them inside a specific adapter unnecessarily couples them to that
context and hinders their future reuse.

**Why wasn't a base exception hierarchy (`AtomException`) created?**

In the current state of the project, with two domain exceptions, a base
hierarchy would be over-engineering. It is reserved as a decision for a future
issue, when the number of exceptions justifies the abstraction.

---

## Commit

```
refactor(structure): centralize validation and exception handling packages

* Remove GlobalExceptionHandler from infrastructure/handler (duplicate)
* Absorb Exception.class fallback handler into SecurityExceptionHandler
* Move validation DTOs from adapter/in/web/dto to infrastructure/common/validation
* Create infrastructure/common/exception package for future error mappers
* Update SecurityController imports to reference common/validation package
* Domain exceptions remain in domain/exception per hexagonal architecture rules
```

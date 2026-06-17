# ATOM-36 — Refactor Global Validation and Exception Handling Structure

## Metadata

| Campo        | Valor                                              |
|--------------|----------------------------------------------------|
| Issue        | ATOM-36                                            |
| Tipo         | Refactor                                           |
| Sprint       | 1                                                  |
| Fecha        | Junio 2026                                         |
| Relacionada  | ATOM-35 (Input Validation & Device Security)       |
| Rama         | `refactor/ATOM-36-validation-exception-structure`  |

---

## Contexto

Durante el desarrollo de ATOM-35 se generaron componentes de validación y manejo
de excepciones distribuidos en paquetes distintos:

- `infrastructure/handler/GlobalExceptionHandler.java` — manejador genérico de excepciones no capturadas.
- `infrastructure/config/SecurityExceptionHandler.java` — manejador de excepciones de dominio y Bean Validation.
- `infrastructure/adapter/in/web/dto/` — DTOs de entrada y salida dispersos por subcarpetas de adaptadores.
- `domain/exception/` — excepciones de dominio sin un contrato unificado.

Esta distribución generaba dos problemas concretos: responsabilidades duplicadas
(`@RestControllerAdvice` en dos clases distintas) y dificultad para localizar
componentes de validación al crecer el proyecto.

---

## Objetivo

Consolidar todos los componentes de validación y manejo de excepciones en una
estructura de paquetes unificada, sin alterar el comportamiento de la aplicación
ni los contratos de las APIs expuestas.

---

## Cambios realizados

### 1. Eliminación de clase duplicada

`GlobalExceptionHandler.java` fue eliminado de `infrastructure/handler/`.
Su único método (`@ExceptionHandler(Exception.class)`) fue absorbido por
`SecurityExceptionHandler.java`, que pasa a ser el único `@RestControllerAdvice`
del proyecto.

**Antes — dos clases con la misma anotación:**
```
infrastructure/
├── handler/
│   └── GlobalExceptionHandler.java       ← @RestControllerAdvice (eliminado)
└── config/
    └── SecurityExceptionHandler.java     ← @RestControllerAdvice
```

**Después — un único handler:**
```
infrastructure/
└── config/
    └── SecurityExceptionHandler.java     ← @RestControllerAdvice (unificado)
```

---

### 2. Nuevo paquete `common/validation`

Se creó el paquete `infrastructure/common/validation/` para centralizar los
componentes reutilizables de validación que no pertenecen a un adaptador específico.

```
infrastructure/
└── common/
    └── validation/
        ├── FormValidationRequestDto.java
        ├── ValidationResponseDto.java
        └── DeviceSecurityResponseDto.java
```

Los DTOs de validación de seguridad se movieron fuera de
`adapter/in/web/dto/` porque son transversales a múltiples
adaptadores y no son exclusivos de un endpoint concreto.

---

### 3. Nuevo paquete `common/exception`

Se creó el paquete `infrastructure/common/exception/` como punto
de referencia para los mapeos entre excepciones de dominio y respuestas HTTP.
Las excepciones de dominio permanecen en `domain/exception/` — este paquete
contiene únicamente utilidades de infraestructura relacionadas con el manejo
de errores si se necesitan en el futuro.

```
domain/
└── exception/
    ├── InputValidationException.java     ← permanece en dominio
    └── DeviceSecurityException.java      ← permanece en dominio

infrastructure/
└── common/
    └── exception/                        ← reservado para mappers/utils de error
```

---

### 4. Estructura final de paquetes

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
    │       └── (reservado para futuros mappers de error)
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

### 5. `SecurityExceptionHandler.java` — versión unificada

El handler resultante cubre los cuatro escenarios de error en orden de
especificidad. Spring resuelve automáticamente el handler más específico,
por lo que `Exception.class` solo actúa como red de seguridad final.

[Archivo relacionado](/com/atom/infrastructure/config/Securityexceptionhandler.java)

---

### 6. Importaciones actualizadas en `SecurityController`

Al mover los DTOs a `common/validation`, el controller actualiza sus imports:

```java
// Antes
import com.atom.infrastructure.adapter.in.web.dto.req.FormValidationRequestDto;
import com.atom.infrastructure.adapter.in.web.dto.res.ValidationResponseDto;
import com.atom.infrastructure.adapter.in.web.dto.res.DeviceSecurityResponseDto;

// Después
import com.atom.infrastructure.common.validation.FormValidationRequestDto;
import com.atom.infrastructure.common.validation.ValidationResponseDto;
import com.atom.infrastructure.common.validation.DeviceSecurityResponseDto;
```

---

## Archivos afectados

| Acción      | Archivo                                                         |
|-------------|-----------------------------------------------------------------|
| `deleted`   | `infrastructure/handler/GlobalExceptionHandler.java`            |
| `deleted`   | `infrastructure/handler/` (carpeta vacía)                       |
| `moved`     | `adapter/in/web/dto/req/FormValidationRequestDto.java` → `common/validation/` |
| `moved`     | `adapter/in/web/dto/res/ValidationResponseDto.java` → `common/validation/`    |
| `moved`     | `adapter/in/web/dto/res/DeviceSecurityResponseDto.java` → `common/validation/`|
| `modified`  | `config/SecurityExceptionHandler.java` (absorbe handler genérico)|
| `modified`  | `adapter/in/web/controller/SecurityController.java` (imports)   |
| `created`   | `infrastructure/common/validation/` (nuevo paquete)             |
| `created`   | `infrastructure/common/exception/` (nuevo paquete reservado)    |
| `unchanged` | `domain/exception/InputValidationException.java`                |
| `unchanged` | `domain/exception/DeviceSecurityException.java`                 |
| `unchanged` | Todos los casos de uso y puertos de dominio                     |
| `unchanged` | Todos los tests existentes                                      |

---

## Criterios de aceptación verificados

| Criterio                                                                 | Estado |
|--------------------------------------------------------------------------|--------|
| Clases de validación consolidadas en paquete común                       | ✅     |
| Excepciones de dominio centralizadas en `domain/exception`               | ✅     |
| Un único `@RestControllerAdvice` en toda la aplicación                   | ✅     |
| `GlobalExceptionHandler` duplicado eliminado                             | ✅     |
| Imports y referencias actualizados en `SecurityController`               | ✅     |
| Comportamiento y contratos de API sin cambios                            | ✅     |
| Tests existentes sin modificaciones                                      | ✅     |

---

## Decisiones técnicas

**¿Por qué las excepciones de dominio permanecen en `domain/exception`
y no se mueven a `infrastructure/common/exception`?**

Las excepciones `InputValidationException` y `DeviceSecurityException`
representan violaciones de reglas de negocio. Son lanzadas por los casos de
uso del dominio y no conocen HTTP. Moverlas a infraestructura rompería la
Dependency Rule de la arquitectura hexagonal: el dominio dependería de una
capa externa.

**¿Por qué los DTOs de validación van en `common/validation`
y no en `adapter/in/web/dto`?**

`FormValidationRequestDto`, `ValidationResponseDto` y `DeviceSecurityResponseDto`
son contratos transversales que múltiples adaptadores pueden necesitar reutilizar.
Ubicarlos dentro de un adaptador específico los acopla innecesariamente a ese
contexto y dificulta su reutilización futura.

**¿Por qué no se creó una jerarquía de excepciones base (`AtomException`)?**

En el estado actual del proyecto, con dos excepciones de dominio, una jerarquía
base sería over-engineering. Se reserva como decisión para una issue futura cuando
el número de excepciones justifique la abstracción.

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

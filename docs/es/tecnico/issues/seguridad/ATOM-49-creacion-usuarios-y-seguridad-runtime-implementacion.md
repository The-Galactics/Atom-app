# ATOM-49 — Creación de usuarios y proceso de seguridad runtime (Implementación)

## Metadata

| Campo        | Valor                                                              |
|--------------|-------------------------------------------------------------------|
| Issue        | ATOM-49                                                            |
| Tipo         | Feature + Hardening de seguridad                                   |
| Sprint       | 2                                                                  |
| Fecha        | 2026-06-24                                                         |
| Relacionadas | ATOM-33 (hashing), ATOM-35 (validaciones), ATOM-36/47 (refactor excepciones) |
| ADRs         | [ADR-001](../ADR-001-migracion-hexagonal-android.md), [ADR-002](../ADR-002-ejecucion-orden-a-accion.md) |
| Módulo       | `Atom-app` (cliente Android, Java 21, hexagonal, manual DI)        |

> **Importante (alineación con ADR-001).** Este documento describe el estado **real**
> del módulo Android. La documentación previa de ATOM-33 describe una implementación
> **del lado del servidor** (Spring Boot / Maven / Argon2) cuyos archivos `java/...`
> **no existen** en este módulo Android. Aquí se distingue explícitamente lo
> **implementado** de lo **pendiente**, y se respeta la decisión de ADR-001 de que el
> hashing de contraseñas y la identidad de usuario son responsabilidad del **backend**.

> **Actualización 2026-06-24 — integración re-aplicada con fixes de revisión.** Tras el
> merge de los PR #27/#28 (`feature/app-actions`) y **ATOM-48** (loop autónomo en la
> burbuja), el hueco crítico se **cerró en código** con los fixes del review incorporados:
>
> 1. **Gate runtime conectado en los TRES servicios protegidos** (no solo el overlay):
>    `FloatingBubbleService.onStartCommand`, `WakeWordService.onStartCommand` y
>    `AtomAccessibilityService.onServiceConnected` consultan ahora el `DeviceSecurityGuard`
>    y, en veredicto inseguro, invocan `SecureShutdownCoordinator.shutdownProtectedComponents`
>    (apaga overlay + wake word + accesibilidad, vía `stopService` + `disableSelf`).
> 2. **Detección fuera del hilo principal y cacheada (fix ANR).** `AppContainer` lanza
>    `DeviceSecurityGuard.refresh()` en un hilo daemon al arrancar; los servicios leen el
>    veredicto **cacheado** (`isEnvironmentSafe()`) sin hacer `exec`/I-O de disco en su ruta
>    de arranque. Cold-cache degrada a evaluación síncrona en vez de fail-open.
> 3. **Fuga de información corregida.** `DeviceSecurityException` ya **no** incrusta `detail`
>    en `getMessage()`; el motivo queda accesible solo vía `getStatus()` para uso interno.
> 4. **Verdicto opaco + fail-closed** preservados; observabilidad mínima vía `Log.w` genérico
>    (sin `detail`) en cada servicio que rechaza el arranque.
>
> **Nueva superficie de ataque que justifica el gate (ATOM-48):** la burbuja ejecuta un
> **loop autónomo multi-paso**, enruta `send_message` a **WhatsApp/Telegram/SMS**, resuelve
> **contactos** (`READ_CONTACTS` vía `ContactsContractResolver`) y aplica
> `domain/action/DestructiveActionPolicy` para confirmar acciones destructivas. Un entorno
> comprometido podría automatizar acciones sensibles end-to-end → por eso se bloquea el
> arranque de los servicios en HIGH/fallo de detección.
>
> Los archivos núcleo (`DeviceSecurityUseCase`, `DeviceInspectorAdapter`, `User`,
> `UserUseCase`) **no** cambiaron; sus descripciones más abajo siguen vigentes.
> **Pendiente real:** versionar los archivos nuevos, *attestation* server-side (control no
> falsificable) y robustecer la detección de root (Magisk/SuperSU, `test-keys`).

---

## 1. Resumen, objetivo y modelo de amenazas

### 1.1 Objetivo

La issue cubre dos frentes que comparten el mismo núcleo hexagonal:

1. **Creación de usuarios** — modelo `User`, caso de uso `UserUseCase`, puertos
   `UserPortIn` / `UserPortOut`, y el contrato de hashing `PasswordEncoderPortOut`.
2. **Proceso de seguridad runtime** — atestación del entorno de ejecución
   (root / emulador / proxy) vía `DeviceSecurityUseCase` + `DeviceInspectorPort` +
   `DeviceInspectorAdapter`, validación sintáctica de entradas
   (`InputValidationUsecase` + `InputValidationPort`), y la **terminación segura** de
   los servicios protegidos (`FloatingBubbleService`, `AtomAccessibilityService`)
   cuando el entorno es inaceptable.

### 1.2 Modelo de amenazas (resumido)

**Activos a proteger**
- Integridad de ejecución del asistente que dibuja **sobre otras apps**
  (`FloatingBubbleService`, permiso `SYSTEM_ALERT_WINDOW`) y que controla el
  dispositivo vía **accesibilidad** (`AtomAccessibilityService`,
  `BIND_ACCESSIBILITY_SERVICE`). Un overlay + accesibilidad en un entorno
  comprometido es una superficie de *overlay attack* / automatización maliciosa.
- Integridad de las **órdenes** enviadas al backend (gRPC `ExecuteCommand`) y de los
  campos de formulario que el usuario introduce.
- **(Nuevo, post-ATOM-48)** Integridad del **loop autónomo multi-paso** que la burbuja
  ejecuta sin intervención, capaz de **enviar mensajes** (`send_message` →
  WhatsApp/Telegram/SMS), **llamar** resolviendo **contactos** (`READ_CONTACTS`) y
  **tocar elementos** de otras apps. La barrera de consentimiento es
  `DestructiveActionPolicy` (confirma borrar/llamar/pagar…); si el entorno está
  comprometido, esa barrera se puede neutralizar y la automatización se vuelve un vector
  de fraude/exfiltración.

**Atacantes y vectores**
- Usuario en dispositivo **rooteado**: puede leer/inyectar en el proceso, falsear
  binarios `su`, instalar gestores de superusuario (Magisk/SuperSU).
- **Emulador / granja de emuladores**: automatización de fraude, scripting masivo.
- **Proxy de intercepción** (mitmproxy/Burp en `localhost:8080`…): inspección y
  manipulación del tráfico gRPC.
- **Inyección por entrada**: SQL/XSS/Command injection en campos del overlay o de
  formularios reenviados al backend.

**Fronteras de confianza**
- El **dominio** y la **aplicación** son código puro y de confianza (testeable en JVM).
- La **infraestructura** (`DeviceInspectorAdapter`) toca el SO y es la única que
  cruza la frontera hacia Android.
- Todo lo que viene del **dispositivo del usuario** (entrada, entorno) es **no
  confiable**.

**Supuestos y límites (honestidad de diseño)**
- La detección en cliente es un **disuasivo**, no una prueba absoluta: un atacante con
  root puede hookear (Frida/Xposed) y forzar los retornos a `false`. La defensa real y
  no falsificable es **server-side** (Play Integrity / attestation). Ver §5 y §6.
- El hashing de contraseñas **no** ocurre en el cliente (ADR-001 §2.4): el cliente
  nunca debe almacenar credenciales en texto plano ni derivar hashes.

---

## 2. Diseño hexagonal: puertos, adaptadores y responsabilidades

Regla de dependencias (ADR-001): `app -> infrastructure -> application -> domain`.
El dominio no depende de nada; la aplicación solo del dominio; los adaptadores
implementan los puertos y son los únicos que tocan Android.

```
┌──────────────────────────── com.atom.domain (POJO/records, sin framework) ───────────────────────────┐
│ model/User                model/security/DeviceSecurityStatus   model/security/ValidationResult       │
│                           utils/RiskLevel (LOW/MEDIUM/HIGH)                                            │
└──────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                              ▲ depende de
┌──────────────────────── com.atom.application (puertos + casos de uso + excepciones) ──────────────────┐
│ PUERTOS IN   port/in/UserPortIn                 port/in/security/DeviceSecurityPort                    │
│              port/in/security/InputValidationPort                                                      │
│ PUERTOS OUT  port/out/UserPortOut               port/out/security/DeviceInspectorPort                 │
│              port/out/security/PasswordEncoderPortOut                                                  │
│ USE CASES    usecase/UserUseCase                usecase/security/DeviceSecurityUseCase                 │
│                                                 usecase/security/InputValidationUsecase                │
│ EXCEPTIONS   exception/DeviceSecurityException  exception/InputValidationException                     │
└──────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                              ▲ implementa / inyecta
┌──────────────────────── com.atom.infrastructure (adaptadores — sí usan Android) ──────────────────────┐
│ adapter/out/device/DeviceInspectorAdapter  (implements DeviceInspectorPort)                            │
│ adapter/accessibility/AtomAccessibilityService                                                         │
│ [PENDIENTE] adapter/out/security/* (PasswordEncoder, UserPortOut) — ver §3 y §8                        │
└──────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                              ▲ compone / consume
┌──────────────────────── com.atom.app (presentación + raíz de composición) ────────────────────────────┐
│ di/AppContainer (manual DI)     overlay/FloatingBubbleService (servicio protegido)                     │
└──────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Responsabilidad por pieza y por qué vive en su capa

| Pieza | Capa | Responsabilidad | Por qué aquí |
|---|---|---|---|
| `User` | dominio | Estado del usuario; `password` ya **hasheado** | POJO puro `java.*`, sin lógica de seguridad |
| `DeviceSecurityStatus` | dominio | Verdicto inmutable + scoring de riesgo (`resolveRisk`) | Regla de negocio pura; record inmutable |
| `RiskLevel` | dominio | Enum `LOW/MEDIUM/HIGH` | Vocabulario de dominio |
| `ValidationResult` | dominio | Resultado de validación inmutable | Record puro |
| `DeviceSecurityPort` (in) | aplicación | Contrato de verificación de integridad | El driver (app) depende de la abstracción |
| `InputValidationPort` (in) | aplicación | Contrato de validación de formularios | Idem |
| `UserPortIn` (in) | aplicación | Contrato de gestión de usuario | Idem |
| `DeviceInspectorPort` (out) | aplicación | Abstracción del SO (root/emulador/proxy) | **Aísla `android.os.Build`/`File`/`Runtime` del caso de uso** |
| `PasswordEncoderPortOut` (out) | aplicación | Abstracción de hashing | Permite mock en test; impl externa intercambiable |
| `UserPortOut` (out) | aplicación | Persistencia/lookup de usuario | Idem |
| `DeviceSecurityUseCase` | aplicación | **Lógica de verdicto**: agrega señales, decide bloqueo | Negocio testeable sin Android |
| `InputValidationUsecase` | aplicación | Regex de inyección + formato; lanza excepción | Negocio puro (sin adapter propio) |
| `UserUseCase` | aplicación | Orquesta hash + persistencia + autenticación | Negocio puro |
| `DeviceInspectorAdapter` | infraestructura | Lee `Build.*`, sondea archivos `su`, ejecuta `which su`, inspecciona `ProxySelector` | Único punto que toca Android |
| `FloatingBubbleService` | app | Overlay; destino de la **terminación segura** | Componente Android protegido |
| `AtomAccessibilityService` | infraestructura | Acciones globales; debe **autodeshabilitarse** en entorno inseguro | Servicio del SO |
| `AppContainer` | app | Raíz de composición; cablea adaptadores a casos de uso | Inyección por constructor manual (ADR-001 §2.5) |

> **Nota de simetría con `InputValidation`.** `InputValidationUsecase` implementa
> directamente el puerto-in y **no** tiene puerto-out ni adaptador: es validación
> sintáctica autocontenida (no toca el SO). Es una decisión deliberada y correcta;
> no requiere `DeviceInspectorPort`-style abstraction.

---

## 3. Flujo de creación de usuario (paso a paso)

> **Estado:** el dominio, el caso de uso y los puertos existen y están cubiertos por
> tests. La **ruta viva de registro NO está cableada en `AppContainer`** y **no existe
> adaptador de `PasswordEncoderPortOut` ni de `UserPortOut`** en Android — por decisión
> de ADR-001 (hashing e identidad = backend). Lo de abajo documenta el contrato
> portable + el cableado que **faltaría** si la creación local llegara a habilitarse.

### 3.1 Secuencia lógica (implementada en `UserUseCase`)

```
Driver (UI / backend)        InputValidationUsecase     UserUseCase        PasswordEncoderPortOut   UserPortOut
        │  registerUser(email, name, raw) │                   │                      │                  │
        │ (1) validateFormFields(fields,  │                   │                      │                  │
        │      "register")  ───────────────▶ valida vacío/    │                      │                  │
        │                                  │ inyección/formato │                      │                  │
        │ ◀── ValidationResult.success() ──│ (o lanza Input-   │                      │                  │
        │      o InputValidationException  │  ValidationExc.)  │                      │                  │
        │ (2) registerUser(...) ──────────────────────────────▶                      │                  │
        │                                  │                   │ (3) hashPassword(raw)─▶                  │
        │                                  │                   │ ◀── hash ─────────────│                  │
        │                                  │                   │ (4) new User(email,name,hash)            │
        │                                  │                   │ (5) save(user) ─────────────────────────▶│
        │ ◀────────────── User (con password = hash) ─────────────────────────────────────────────────── │
```

1. **Validación previa (recomendada).** El driver invoca
   `InputValidationUsecase.validateFormFields(fields, "register")` para los campos
   `email`/`name` (y nunca el password en claro a través de regex de inyección —
   ver nota de seguridad). Si hay error, lanza `InputValidationException` y el flujo
   se detiene **antes** de tocar el password.
2. **Registro.** `UserUseCase.registerUser(email, name, rawPassword)`.
3. **Hashing.** Delega en `passwordEncoder.hashPassword(rawPassword)` (puerto-out;
   nunca conoce el algoritmo concreto).
4. **Construcción de dominio.** `new User(email, name, hashedPassword)` — el `User`
   nace con `id` UUID, `createdAt`, `active=true` y **solo el hash**.
5. **Persistencia.** `userPortOut.save(user)` devuelve el usuario persistido.

`authenticateUser(email, raw)`: `findByEmail` → `verifyPassword(raw, user.getPassword())`,
devolviendo `false` si el usuario no existe (`Optional.map(...).orElse(false)`).

### 3.2 Contrato real existente

```java
// application/usecase/UserUseCase.java  (IMPLEMENTADO)
public User registerUser(String email, String name, String rawPassword) {
    String hashedPassword = passwordEncoder.hashPassword(rawPassword);
    User user = new User(email, name, hashedPassword);
    return userPortOut.save(user);
}
public boolean authenticateUser(String email, String rawPassword) {
    return userPortOut.findByEmail(email)
            .map(user -> passwordEncoder.verifyPassword(rawPassword, user.getPassword()))
            .orElse(false);
}
```

> **Nota de seguridad — el password no pasa por el filtro de inyección.** El regex
> `SQL_INJECTION` de `InputValidationUsecase` marca como malicioso cualquier valor con
> `'`, `;`, `"`, `--`. Una contraseña fuerte legítima los contiene. **No** se debe
> validar el campo password con `validateFormFields`; su complejidad se valida con
> reglas propias (longitud/clase de caracteres), idealmente en el backend.

### 3.3 Huecos en el flujo de usuario (ver §8)

- `UserPortIn.createUser(String email, String name)` **no recibe password** y
  `UserUseCase` **no implementa** `UserPortIn` → contrato inconsistente.
- No existe `PasswordEncoderPortOut` adapter (Argon2/BCrypt) en Android (por diseño).
- No existe `UserPortOut` adapter ni implementación de `UserPortIn`.
- `UserUseCase` **no** se cablea en `AppContainer`.

---

## 4. Flujo de seguridad runtime (paso a paso)

### 4.1 Detección y cálculo de verdicto (IMPLEMENTADO)

```
DeviceSecurityUseCase            DeviceInspectorPort (= DeviceInspectorAdapter)
        │ verifyDeviceIntegrity()        │
        │ (1) isRooted() ────────────────▶ sondea ROOT_BINARIES[] + exec "which su"
        │ (2) isRunningOnEmulator() ─────▶ Build.FINGERPRINT/MODEL/MANUFACTURER/BRAND/PRODUCT/HARDWARE
        │ (3) isProxyActive() ───────────▶ ProxySelector + env http(s)_proxy + puertos sospechosos
        │ ◀── booleans ──────────────────│
        │ (4) si todo false -> DeviceSecurityStatus.safe()    (RiskLevel.LOW)
        │     si no          -> DeviceSecurityStatus.risky(...) con scoring:
        │                       rooted=+3, emulator=+2, proxy=+1
        │                       score>=3 -> HIGH ; score>=1 -> MEDIUM ; else LOW
```

`shouldBlockOperation(status)`: si `status.isHighRisk()` lanza
`DeviceSecurityException(status)`; en caso contrario devuelve `false`.

### 4.2 Terminación segura de servicios — **CLASES CREADAS, CABLEADO PENDIENTE**

> **Estado actual (2026-06-24).** Las clases que cierran el hueco **ya existen** en el
> árbol: `DeviceSecurityGuard` (guard fail-closed / verdicto opaco),
> `SecureShutdownCoordinator` (apagado idempotente y silencioso) y su test unitario. Sin
> embargo, **el merge revirtió el cableado**: `AppContainer` ya no expone
> `getDeviceSecurityGuard()` y `FloatingBubbleService.onStartCommand` ya no consulta el
> guard. Por tanto **ningún componente de producción invoca aún la verificación** y no hay
> terminación segura activa. El `detail` (p. ej. `ROOT_ACCESS`) **sigue filtrando internos**
> en el mensaje de `DeviceSecurityException`. Esta sección define el flujo objetivo y el
> código a **re-aplicar** sobre la forma actual de los dos archivos.

#### 4.2.1 Diagrama objetivo `DeviceInspector → UseCase → verdicto → destrucción de servicio`

```
FloatingBubbleService.onStartCommand()
        │
        │ (1) guard = appContainer.getDeviceSecurityGuard()
        ▼
DeviceSecurityGuard.isEnvironmentSafe()        (NUEVO — envuelve fail-closed)
        │
        │ (2) try {
        │       status = deviceSecurityPort.verifyDeviceIntegrity()
        │       return !status.isHighRisk()
        │     } catch (Throwable t) { return false /* fail-closed */ }
        ▼
   ┌────────────┴─────────────┐
 seguro                     INSEGURO (HIGH risk  ó  fallo de detección)
   │                           │
   ▼                           ▼
startForeground +        SecureShutdownCoordinator.shutdownProtectedComponents()  (NUEVO)
showBubble()                  │  - FloatingBubbleService.stopOverlay()   (idempotente, YA existe)
                              │  - AtomAccessibilityService.getInstance().disableSelf()
                              │  - WakeWordService stop / overlays removidos
                              │  return START_NOT_STICKY  (no se reinicia el servicio)
                              ▼
                         (sin diálogo técnico; verdicto OPACO al usuario)
```

#### 4.2.2 Principios aplicados

- **Fail-closed (default deny).** Si la detección no completa de forma fiable
  (excepción en cualquier sonda), el guard devuelve `false` ⇒ se trata como inseguro.
  Hoy `DeviceInspectorAdapter` hace `catch(Exception ignored)` y devuelve `false`
  (fail-open) en cada sonda: el guard debe compensar esto a nivel de orquestación.
- **Verdicto opaco.** El usuario/atacante recibe, como mucho, un cierre silencioso o
  un mensaje genérico (`R.string.overlay_unavailable`). **Nunca** `ROOT_ACCESS` ni
  rutas de binarios. El `detail` se conserva solo para telemetría interna y **no debe
  loguearse** en builds release.
- **Idempotencia.** `FloatingBubbleService.stopOverlay()` ya es idempotente (quita
  vistas con guardas `isAttachedToWindow()`, `stopForeground(STOP_FOREGROUND_REMOVE)`,
  `stopSelf()`). El coordinador puede invocarlo varias veces sin crash.
- **Sin overlays colgados.** La destrucción reusa `stopOverlay()` →
  `removeView(...)` defensivo, garantizando que no quede una ventana
  `TYPE_APPLICATION_OVERLAY` huérfana.

#### 4.2.3 Validación de entrada en la ruta del overlay (refuerzo)

`FloatingBubbleService.dispatchPrompt(...)` hoy solo comprueba `text.isEmpty()`. Como
refuerzo, antes de `handlePrompt(text, status)` puede invocarse
`InputValidationUsecase.validateFormFields(Map.of("prompt", text), "overlay")` envuelto
en try/catch de `InputValidationException`, mostrando un mensaje **genérico** en
`status` (sin enumerar el patrón detectado). Esto es defensa en profundidad: el regex
de inyección no debe ser la única barrera, pero sí frena payloads triviales antes de
salir del dispositivo.

---

## 5. Contratos: interfaces, excepciones y snippets

### 5.1 Puertos y modelos existentes (verbatim del código actual)

```java
// application/port/out/security/DeviceInspectorPort.java  (IMPLEMENTADO)
public interface DeviceInspectorPort {
    boolean isRooted();
    boolean isRunningOnEmulator();
    boolean isProxyActive();
}

// application/port/in/security/DeviceSecurityPort.java  (IMPLEMENTADO)
public interface DeviceSecurityPort {
    DeviceSecurityStatus verifyDeviceIntegrity();
    boolean shouldBlockOperation(DeviceSecurityStatus status);
}

// application/port/in/security/InputValidationPort.java  (IMPLEMENTADO)
public interface InputValidationPort {
    ValidationResult validateFormFields(Map<String, String> fields, String context);
}

// application/port/out/security/PasswordEncoderPortOut.java  (PUERTO; sin adapter en Android)
public interface PasswordEncoderPortOut {
    String hashPassword(String rawPassword);
    boolean verifyPassword(String rawPassword, String encodedPassword);
}

// application/port/in/UserPortIn.java  (PUERTO; sin implementación)
public interface UserPortIn {
    User createUser(String email, String name);   // <-- nota: sin password (hueco §8)
    Optional<User> getUserById(String userId);
    Optional<User> getUserByEmail(String email);
    User updateUserProfile(String userId, String name);
    void deactivateUser(String userId);
    void activateUser(String userId);
}

// application/port/out/UserPortOut.java  (PUERTO; sin adapter)
public interface UserPortOut {
    User save(User user);
    Optional<User> findById(String userId);
    Optional<User> findByEmail(String email);
    void delete(User user);
}
```

```java
// domain/model/security/DeviceSecurityStatus.java  (IMPLEMENTADO — scoring real)
public record DeviceSecurityStatus(boolean rooted, boolean runningOnEmulator,
                                   boolean proxyDetected, RiskLevel riskLevel, String detail) {
    public boolean isHighRisk() { return riskLevel == RiskLevel.HIGH; }
    public static DeviceSecurityStatus safe() { /* LOW */ }
    public static DeviceSecurityStatus risky(boolean r, boolean e, boolean p, String detail) { /* scoring */ }
    // resolveRisk: rooted+=3, emulator+=2, proxy+=1 ; >=3 HIGH ; >=1 MEDIUM ; else LOW
}
```

### 5.2 Excepciones (existentes)

```java
// application/exception/DeviceSecurityException.java  (IMPLEMENTADO)
//   super("Operation blocked. High-risk device environment detected: " + status.detail());
//   ⚠ El mensaje INCLUYE detail (ROOT_ACCESS...) -> ver hueco de fuga de información §8.
public class DeviceSecurityException extends RuntimeException {
    public DeviceSecurityException(DeviceSecurityStatus status) { ... }
    public DeviceSecurityStatus getStatus() { ... }
}

// application/exception/InputValidationException.java  (IMPLEMENTADO)
public class InputValidationException extends RuntimeException {
    public InputValidationException(List<String> errors) { ... }
    public List<String> getValidationErrors() { ... }
}
```

### 5.3 Piezas de cierre del hueco (CREADAS — pendientes de re-cablear)

> **Estado (2026-06-24).** Los dos snippets de clases (`DeviceSecurityGuard`,
> `SecureShutdownCoordinator`) **ya están escritos** como archivos en el árbol (sin
> versionar). Los snippets de **cableado** (`AppContainer` + `FloatingBubbleService`) se
> habían aplicado pero **el merge los revirtió**, así que deben re-aplicarse sobre la
> forma actual de esos archivos. Abajo, el cableado está actualizado al `AppContainer`
> posterior al merge (constructor con `ContactResolver` + `ConversationRepository`).

```java
// application/usecase/security/DeviceSecurityGuard.java   (NUEVO — fail-closed, verdicto opaco)
package com.atom.application.usecase.security;

import com.atom.application.port.in.security.DeviceSecurityPort;
import com.atom.domain.model.security.DeviceSecurityStatus;

/** Verdicto booleano OPACO. No expone el motivo (detail) al exterior. */
public final class DeviceSecurityGuard {
    private final DeviceSecurityPort security;
    public DeviceSecurityGuard(DeviceSecurityPort security) { this.security = security; }

    /** true solo si el entorno es verificablemente seguro; cualquier fallo => false (deny). */
    public boolean isEnvironmentSafe() {
        try {
            DeviceSecurityStatus status = security.verifyDeviceIntegrity();
            return !status.isHighRisk();           // política: bloquear solo HIGH
        } catch (Throwable failClosed) {
            return false;                          // detección no fiable => inseguro
        }
    }
}
```

```java
// app/security/SecureShutdownCoordinator.java   (NUEVO — capa app, orquesta apagado)
package com.atom.app.security;

import android.content.Context;
import android.content.Intent;
import com.atom.app.overlay.FloatingBubbleService;
import com.atom.infrastructure.adapter.accessibility.AtomAccessibilityService;

/** Apagado idempotente y silencioso de los componentes protegidos. */
public final class SecureShutdownCoordinator {
    private SecureShutdownCoordinator() {}

    public static void shutdownProtectedComponents(Context ctx) {
        // 1) Overlay: reusa la ruta idempotente ACTION_STOP -> stopOverlay().
        try {
            ctx.startService(new Intent(ctx, FloatingBubbleService.class)
                    .setAction(FloatingBubbleService.ACTION_STOP));
        } catch (Throwable ignored) { /* nunca debe crashear la app */ }

        // 2) Accesibilidad: el servicio se autodeshabilita si está vivo.
        try {
            AtomAccessibilityService a11y = AtomAccessibilityService.getInstance();
            if (a11y != null) { a11y.disableSelf(); }   // API 24+ ; minSdk 26 OK
        } catch (Throwable ignored) { }
    }
}
```

```java
// FloatingBubbleService.onStartCommand(...)  (RE-APLICAR — punto de integración, guard al arrancar)
// Insertar JUSTO después del manejo de ACTION_STOP y ANTES de `overlayEnabled = true;`.
String action = intent != null ? intent.getAction() : ACTION_START;
if (ACTION_STOP.equals(action)) { stopOverlay(); return START_NOT_STICKY; }

if (!isEnvironmentSafe()) {
    // El servicio se lanza con startForegroundService(): hay que honrar el contrato FGS
    // (startForeground en ~5 s) antes de parar, o salta ForegroundServiceDidNotStartInTimeException.
    startForegroundWithNotification();
    SecureShutdownCoordinator.shutdownProtectedComponents(this);
    return START_NOT_STICKY;     // no relanzar; sin detalle al usuario/atacante
}
overlayEnabled = true;
// ... resto del arranque normal (ACTION_SHOW / ACTION_LISTEN / showBubble)

// helper en la misma clase (fail-closed también si el container fallara):
private boolean isEnvironmentSafe() {
    try {
        return app.getAppContainer().getDeviceSecurityGuard().isEnvironmentSafe();
    } catch (Throwable failClosed) {
        return false;
    }
}
```

```java
// AppContainer  (RE-APLICAR — exponer el guard sobre el use case existente; constructor post-merge)
private final DeviceSecurityGuard deviceSecurityGuard;
// en el constructor, tras crear deviceSecurityUseCase:
this.deviceSecurityUseCase = new DeviceSecurityUseCase(deviceInspectorPort);
this.deviceSecurityGuard   = new DeviceSecurityGuard(this.deviceSecurityUseCase);
// ...
public DeviceSecurityGuard getDeviceSecurityGuard() { return deviceSecurityGuard; }
```

> **Endurecimiento recomendado del `detail` (opcional).** Para no filtrar internos por
> la excepción, mover `detail` a un campo no incluido en `getMessage()` o sustituirlo
> por un código opaco (p. ej. un hash/secuencia) en builds release. La UI nunca debe
> renderizar `getStatus().detail()`.

---

## 6. Estrategia de pruebas (unitaria) y mapeo a Gherkin

Stack: JUnit 5 + Mockito + AssertJ (`./gradlew test`). El `DeviceInspectorPort` se
mockea para aislar el caso de uso del hardware; `InputValidationUsecase` se prueba puro.

### 6.1 Cobertura existente (verificada)

| Test | Escenario Gherkin (ATOM-35 / ATOM-33) | Estado |
|---|---|---|
| `DeviceSecurityUseCaseTest.shouldReturnSafeStatusOnCleanDevice` | "Dispositivo limpio permite operar" (LOW) | ✅ |
| `...shouldReturnHighRiskOnRootedDevice` | "Dispositivo rooteado bloquea operaciones críticas" (HIGH, ROOT_ACCESS) | ✅ |
| `...shouldReturnHighRiskOnAllFactors` | "Combinación de factores genera riesgo alto" | ✅ |
| `...shouldReturnMediumRiskOnEmulatorOnly` | "Emulador sin root genera riesgo medio" (MEDIUM) | ✅ |
| `...shouldThrowOnHighRiskDevice` | excepción de seguridad en HIGH | ✅ |
| `...shouldNotBlockOnLowRiskDevice` | no bloqueo en LOW | ✅ |
| `InputValidationUseCaseTest` (EmptyFields/InjectionDetection/FormatValidation) | campos vacíos, SQLi, XSS, CMDi, formato email/amount, caso feliz | ✅ |
| `UserUseCaseTest.shouldRegisterUserWithHashedPasswordAndAuthenticate` | "Generar hash", "Verificar válida/ inválida", registro+auth (encoder mock) | ✅ |

### 6.2 Casos faltantes (a añadir)

| Caso | Por qué | Componente |
|---|---|---|
| `DeviceSecurityGuard` devuelve `false` cuando `verifyDeviceIntegrity()` lanza | **fail-closed** | `DeviceSecurityGuard` (NUEVO) |
| `DeviceSecurityGuard` devuelve `true` solo en LOW/MEDIUM y `false` en HIGH | política de bloqueo | `DeviceSecurityGuard` (NUEVO) |
| `SecureShutdownCoordinator` no propaga excepción (Throwable swallowed) | no debe crashear | coordinador (NUEVO; test con Robolectric/instrumentado) |
| Terminación **idempotente**: doble `shutdownProtectedComponents` sin efecto adverso | idempotencia | coordinador (NUEVO) |
| `InputValidationUsecase` con contexto `overlay` y payload de inyección en `prompt` | refuerzo overlay | `InputValidationUsecase` |
| Email con SQLi pasa primero por filtro de inyección y **no** llega a hash | orden validación→hash | `UserUseCase` + `InputValidationUsecase` |
| `authenticateUser` devuelve `false` para email inexistente | rama `Optional.empty` | `UserUseCase` |
| El password fuerte (con `'`, `;`) **no** se valida con `validateFormFields` | evitar falso positivo | guía de uso (test negativo documental) |
| Verdicto opaco: el mensaje al usuario no contiene `ROOT_ACCESS`/rutas | no fuga de info | mapeo de excepción (NUEVO) |

---

## 7. Criterios de aceptación / Definition of Done

**Creación de usuarios (alcance cliente, alineado a ADR-001)**
- [x] `User` almacena únicamente el hash, nunca texto plano.
- [x] `UserUseCase.registerUser` hashea **antes** de construir/persistir el `User`.
- [x] `UserUseCase.authenticateUser` verifica vía `PasswordEncoderPortOut`, no compara en claro.
- [x] El core depende de puertos (`UserPortOut`, `PasswordEncoderPortOut`), no de implementaciones.
- [ ] (Diferido por ADR-001) Adapter de hashing + persistencia + cableado en `AppContainer`.
- [ ] (Hueco) `UserPortIn.createUser` recibe password y `UserUseCase` implementa `UserPortIn`.

**Seguridad runtime**
- [x] Detección de root (binarios + `which su`), emulador (`Build.*`) y proxy (ProxySelector/env).
- [x] Scoring agregado (defensa en profundidad) → `RiskLevel` → `DeviceSecurityStatus`.
- [x] La lógica de verdicto vive en el use case; el adapter no contiene reglas de negocio.
- [x] Cobertura unitaria de LOW/MEDIUM/HIGH y de bloqueo.
- [x] `DeviceSecurityGuard` (fail-closed, verdicto opaco) **creado, cableado y testeado** (4 casos: LOW/MEDIUM/HIGH/excepción + cacheo).
- [x] `SecureShutdownCoordinator` (apagado idempotente y silencioso) **creado e integrado**; usa `stopService` + `disableSelf` (robusto en background).
- [x] (Hueco crítico cerrado) Callers de producción invocan la verificación al arrancar: overlay, wake word y accesibilidad.
- [x] Terminación segura cubre los **tres** servicios protegidos (`FloatingBubbleService`, `WakeWordService`, `AtomAccessibilityService`), no solo el overlay.
- [x] Verdicto **opaco** end-to-end: `DeviceSecurityException` ya no expone `detail` en `getMessage()`; el guard nunca devuelve el motivo.
- [x] Detección **fuera del hilo principal** + cacheada: `AppContainer` lanza `refresh()` en hilo daemon; los servicios leen el veredicto cacheado (sin `exec`/I-O en `onStartCommand`).
- [ ] (Pendiente) Versionar los 3 archivos nuevos (`git add`) y CI verde de `./gradlew test`.
- [ ] (Pendiente, no falsificable) *Attestation* server-side en `Atom-agent` antes de `ExecuteCommand`.

**Calidad arquitectónica**
- [x] Dominio sin Android/framework; aplicación sin Android; adapter único que toca el SO.
- [x] Interfaces segregadas (sin interfaz "gorda").
- [ ] Sin valores hardcodeados inline en el adapter (hoy `Build.*` y rutas viven inline → extraer a config).

---

## 8. Huecos y pendientes detectados en el código (resumen accionable)

1. **✅ RESUELTO — Verdicto cableado.** `DeviceSecurityGuard` y `SecureShutdownCoordinator`
   están integrados en los tres servicios protegidos; la verificación se invoca al arrancar
   y hay terminación segura activa. *(Falta solo versionar los archivos nuevos con `git add`.)*
2. **✅ RESUELTO (a nivel de orquestación) — Fail-closed.** El `DeviceSecurityGuard` deniega
   ante veredicto HIGH **o** ante cualquier excepción de detección, compensando el
   `catch(Exception ignored)` fail-open que las sondas del `DeviceInspectorAdapter` aún tienen
   internamente. *(Endurecer las sondas en sí sigue siendo deseable; ver #6.)*
3. **✅ RESUELTO — Fuga de información.** `DeviceSecurityException` ya no incrusta `detail`
   en `getMessage()` (mensaje genérico); el motivo solo es accesible vía `getStatus()` para
   uso interno y el `DeviceSecurityGuard` nunca lo expone.
4. **Creación de usuario incompleta.** Sin adapter de `PasswordEncoderPortOut`, sin
   adapter de `UserPortOut`, sin implementación de `UserPortIn`, y `UserUseCase` no se
   cablea (decisión ADR-001: hashing/identidad = backend).
5. **Contrato `UserPortIn` inconsistente.** `createUser(email, name)` no recibe password
   y `UserUseCase` no implementa el puerto-in.
6. **Detección de root débil.** Sin lista de paquetes de superusuario (Magisk/SuperSU),
   sin chequeo de build `test-keys`, sin sonda de `/system` montado en `rw`.
7. **Valores hardcodeados inline.** Cadenas de emulador (`Build.*`), `ROOT_BINARIES` y
   puertos de proxy viven inline en el adapter; deberían moverse a constantes/config
   bien nombradas para cumplir la prohibición de hardcoding.
8. **Documentación previa desalineada.** `ATOM-33-password-hashing-resumen.md` y
   `ATOM-36` describen una implementación **Spring/Maven del servidor** (`java/...`,
   `Argon2PasswordEncoderAdapter`, `@RestControllerAdvice`) que **no existe** en este
   módulo Android. Este documento es la referencia válida para el cliente.
9. **(Parcial, post-ATOM-48) Superficie sensible — gate aplicado, barrera de contenido aún débil.**
   El gate runtime **ya cubre los tres servicios** (overlay, wake word y accesibilidad), así que
   un entorno comprometido no los arranca. Lo que **sigue pendiente**: la barrera de consentimiento
   de contenido es `DestructiveActionPolicy` (lista de verbos es/en por palabras-clave), evadible
   con sinónimos/idiomas no cubiertos. Endurecerla (o moverla a backend) es trabajo aparte. El
   control no falsificable sigue siendo *attestation* server-side en `Atom-agent` antes de
   `ExecuteCommand`.

# ATOM — Permisos de Overlay/Accesibilidad/Vista de Pantalla, Pruebas de Primer Arranque y Actualización del Contrato gRPC

## Metadata

| Campo    | Valor                                                                  |
|----------|-----------------------------------------------------------------------|
| Tipo     | Feature + Scaffolding + Sincronización de contrato                     |
| Sprint   | 1                                                                     |
| Fecha    | 2026-06-14                                                            |
| Relacionado | ATOM-30 (gRPC), ADR-001 (migración hexagonal Android)             |
| Rama     | `feature/migration`                                                  |
| Estado   | 🟡 Implementado (sin verificar build — no hay Android SDK en el entorno) |

---

## Contexto

El asistente Atom necesita operar *encima* del teléfono (una "super posición" / overlay en pantalla) y observar/actuar a través de otras apps. Eso requiere capacidades a nivel de SO que la app no declaraba antes: un permiso de **overlay**, un **Servicio de Accesibilidad** y — como trabajo a futuro — **vista de pantalla** mediante MediaProjection. En paralelo, el backend de Python (`Atom-agent`) publicó un contrato gRPC unificado que extiende el que tenía la app, por lo que el proto de Android tuvo que re-sincronizarse. Por último, la app nunca se había ejecutado de extremo a extremo, así que los botones existentes necesitaban handlers reales para permitir una primera prueba manual.

Este documento cubre cuatro cambios entregados juntos:

1. Permisos de overlay ("super posición") + Accesibilidad.
2. Scaffolding de vista de pantalla (MediaProjection) — **trabajo a futuro**.
3. Interacciones de los botones para pruebas de primer arranque.
4. Actualización del contrato gRPC para igualar al backend (fuente de la verdad).

---

## 1. Overlay ("super posición") y Accesibilidad

### Permisos y manifest

- `SYSTEM_ALERT_WINDOW` — permite al asistente dibujar sobre otras apps (el overlay de "super posición").
- Un `AccessibilityService` declarado con el permiso `BIND_ACCESSIBILITY_SERVICE` y el intent filter `android.accessibilityservice.AccessibilityService`, más un `<meta-data>` que apunta a su descriptor de capacidades.

### Archivos

- `src/main/AndroidManifest.xml` — declaraciones de permiso y `<service>`.
- `src/main/java/com/atom/infrastructure/adapter/accessibility/AtomAccessibilityService.java` — el servicio. Actualmente es scaffolding: registra la conexión y los eventos para verificar que está activo; aún no hay inspección de nodos ni despacho de gestos (**trabajo a futuro**).
- `src/main/res/xml/accessibility_service_config.xml` — declara los eventos escuchados, `canRetrieveWindowContent`, `canPerformGestures` y flags.
- `src/main/res/values/strings.xml` — `accessibility_service_description`.

### Flujo en tiempo de ejecución

Centralizado en `com.atom.app.permission.PermissionCoordinator`:

- **Overlay:** `canDrawOverlays(context)` verifica el permiso; `overlaySettingsIntent(context)` construye el intent `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` (URI `package:`). `MainActivity` lo lanza con un `ActivityResultLauncher` y vuelve a verificar al regresar.
- **Accesibilidad:** no se puede habilitar programáticamente. `isAccessibilityServiceEnabled(context)` lee `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`; `accessibilitySettingsIntent()` abre `Settings.ACTION_ACCESSIBILITY_SETTINGS` para que el usuario active Atom.

---

## 2. Vista de pantalla (MediaProjection) — TRABAJO A FUTURO

Solo scaffolding. La captura/codificación/streaming de pantalla **no** está implementada.

### Permisos y manifest

- `FOREGROUND_SERVICE` y `FOREGROUND_SERVICE_MEDIA_PROJECTION`.
- Un `<service>` con `android:foregroundServiceType="mediaProjection"`.

### Archivos

- `src/main/java/com/atom/infrastructure/adapter/screen/ScreenCaptureService.java` — stub de servicio en primer plano. Se inicia en foreground con una notificación y acepta el código/datos de consentimiento vía `EXTRA_RESULT_CODE` / `EXTRA_RESULT_DATA`, pero no captura nada.
- `PermissionCoordinator.screenCaptureIntent(context)` — construye el intent de consentimiento desde `MediaProjectionManager`.

### Flujo

`MainActivity` lanza el intent de consentimiento para resultado; con `RESULT_OK` inicia `ScreenCaptureService` con los extras del resultado. El trabajo restante (obtener el `MediaProjection`, `VirtualDisplay`, encoder/stream) está marcado como `FUTURE WORK` en el código.

---

## 3. Interacciones de botones para el primer arranque

`MainActivity` ahora conecta cada botón de `activity_main.xml` con un flujo real para poder hacer una prueba manual por primera vez:

| Botón          | Acción                                                              |
|----------------|--------------------------------------------------------------------|
| `btn_mic`      | Envía un mensaje real por gRPC `StreamChat` al backend de Python   |
| `btn_settings` | Abre `SettingsActivity`                                            |
| `btn_history`  | Solicita el permiso de overlay ("super posición")                 |
| `btn_keyboard` | Envía al usuario a habilitar el servicio de Accesibilidad         |
| `btn_volume`   | Solicita el consentimiento de captura de pantalla — scaffolding    |

Pruebas de extremo a extremo: con `Atom-agent` en ejecución y `GRPC_HOST`/`GRPC_PORT` apuntando a él (por defecto `10.0.2.2:50051` para el emulador), tocar el micrófono ejercita la ruta completa UI → ViewModel → `ChatRepository` → `StreamChatPortIn` → adaptador gRPC → backend y renderiza la respuesta. Los resultados de los flujos de permisos se muestran con toasts.

---

## 4. Actualización del contrato gRPC

El contrato del backend en `Atom-agent/proto/atom_agent.proto` es la fuente de la verdad. El `src/main/proto/ai.proto` de la app era un subconjunto estricto; se re-sincronizó.

### Qué cambió

- Mismo `package com.atom.proto`, mismos `java_package`/`java_outer_classname`, mismo nombre `AtomAgentService`.
- **RPCs sin cambios:** `ExecuteCommand` (unario) y `StreamChat` (server-streaming) — las clases de mensaje son idénticas, así que los métodos existentes de `InteractionGrpcAdapter` siguen siendo válidos.
- **RPCs agregados (voz):**
  - `Transcribe(TranscribeRequest) returns (TranscribeResponse)` — Speech-to-Text, unario.
  - `Synthesize(SynthesizeRequest) returns (stream SynthesizeResponse)` — Text-to-Speech, server-streaming.
- Nuevos mensajes: `TranscribeRequest/Response`, `SynthesizeRequest/Response` (copiados campo por campo, incluidos los números de tag, desde el backend).

### Cableado hexagonal

Para mantener los límites intactos, las nuevas capacidades se exponen por el out-port existente en lugar de filtrar los stubs generados:

- `ExternalInteractionPortOut` — se agregaron `transcribeAudio(...)` y `synthesizeSpeech(...)`.
- `InteractionGrpcAdapter` — los implementa contra los stubs generados `transcribe`/`synthesize` (usa `ByteString` para los campos `bytes`). Marcado `FUTURE WORK`: ningún UI/caso de uso los invoca todavía.

### Codegen

No hace falta cambiar `build.gradle.kts` — la misma ruta de proto y la misma configuración del plugin protobuf/grpc-java lite compilan los nuevos mensajes y RPCs.

---

## Estado de verificación

- Sintaxis del proto validada con `grpc_tools.protoc` (del venv del backend): **OK**.
- Firmas Java revisadas contra las convenciones de nombres generados (`transcribe`, `synthesize`, `setAudioBytes`, etc.) y el precedente existente.
- Manifest revisado por validez (declaraciones de servicio, permisos, tipo de servicio en primer plano).
- **No** compilado con el toolchain de Android — no hay Android SDK en este entorno. Se requiere un `./gradlew assembleDebug` para confirmar por completo.

## Acceso directo

- [Contrato proto](../../../../../src/main/proto/ai.proto)
- [Adaptador gRPC](../../../../../src/main/java/com/atom/infrastructure/adapter/grpc/InteractionGrpcAdapter.java)
- [Servicio de accesibilidad](../../../../../src/main/java/com/atom/infrastructure/adapter/accessibility/AtomAccessibilityService.java)
- [Servicio de captura de pantalla (scaffolding)](../../../../../src/main/java/com/atom/infrastructure/adapter/screen/ScreenCaptureService.java)
- [Coordinador de permisos](../../../../../src/main/java/com/atom/app/permission/PermissionCoordinator.java)

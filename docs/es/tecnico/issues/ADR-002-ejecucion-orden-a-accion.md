# ADR-002 — Ejecución Orden → Acción (Function Calling + Accesibilidad)

- **Estado:** Aceptado
- **Fecha:** 2026-06-16
- **Decisores:** Arquitectura (Arquitecto Principal), Equipo móvil (The-Galactics)
- **Fuente de contexto:** Añadir acciones sobre el dispositivo encima del núcleo hexagonal definido en [ADR-001](../../tecnico/issues/ADR-001-migracion-hexagonal-android.md).

---

## 1. Contexto

Hasta ahora la app solo podía **conversar**: texto de entrada → texto de la IA
(gRPC `StreamChat`). El objetivo de producto es que el usuario dé una **orden** en
lenguaje natural ("abre WhatsApp", "llama a mamá", "pon una alarma a las 7:30") y
que Atom la **ejecute** en el dispositivo.

El agente de IA en Python (`Atom-agent`) se amplió para interpretar la orden con
**function calling** (Google Gemini): clasifica la frase en una acción
estructurada y la devuelve por el RPC gRPC existente `ExecuteCommand`. El contrato
está documentado en `Atom-agent/INTENT_ACTIONS_CONTRACT.md` (única fuente de
verdad del catálogo de acciones).

En el lado Android debemos: recibir esa acción estructurada, decidir si necesita
confirmación del usuario y **ejecutarla** — algunas son `Intent` simples, otras
requieren un servicio del sistema o el **AccessibilityService**.

---

## 2. Decisión

Dividir la función con la misma regla de dependencias de ADR-001: **el
reconocimiento es remoto, la ejecución es local**, y ambos detrás de puertos para
que el dominio siga puro.

### 2.1 El contrato (`ai.proto`)

`CommandResponse` se amplió (números de campo retrocompatibles) para transportar
la acción resuelta:

```proto
message CommandResponse {
  bool   success = 1;
  string out_message = 2;            // respuesta para hablar/mostrar
  string action_type = 3;           // "OPEN_APP", "MAKE_CALL", … o "NONE"
  string parameters_json = 4;       // slots JSON, p.ej. {"app_name":"whatsapp"}
  float  confidence = 5;
  bool   requires_confirmation = 6; // las acciones sensibles confirman antes
}
```

El proto se comparte con el backend; el plugin protobuf-gradle regenera los stubs
Java al compilar. **`ai.proto` debe permanecer idéntico a
`Atom-agent/proto/atom_agent.proto`.**

### 2.2 Mapa de capas (piezas nuevas)

```
domain.action            ActionType (enum, refleja el catálogo del backend)
                         ResolvedAction (proyección inmutable de CommandResponse)
                         ActionOutcome (resultado de ejecutar una acción)
application.port.in      ExecuteCommandPortIn  -> ahora devuelve ResolvedAction
application.port.out     ExternalInteractionPortOut.commandResponse -> ResolvedAction
                         ActionExecutorPortOut (NUEVO: ejecuta un ResolvedAction)
application.usecase      ExternalCommandUseCase (delega el reconocimiento al backend)
infrastructure.adapter.grpc           InteractionGrpcAdapter (parsea parameters_json aquí)
infrastructure.adapter.action         AndroidActionExecutor (el despachador on-device)
infrastructure.adapter.accessibility  AtomAccessibilityService (ejecutor de acciones globales)
app.repository           CommandRepository (hilos de reconocimiento + ejecución)
app.viewmodel            ChatViewModel.sendOrder() / runAction() / pendingConfirmation
app                      MainActivity (entrada de texto = orden; diálogo de confirmación)
```

**Se mantiene la regla de dependencias:** JSON y protobuf viven solo en el
adaptador gRPC; las APIs de Android solo en `infrastructure.adapter.action` /
`.accessibility`. Las capas `domain` y `application` no importan ninguna.

### 2.3 Estrategia de ejecución (por acción)

| `action_type` | Slots | Confirma | Mecanismo Android |
|---|---|:--:|---|
| `OPEN_APP` | `app_name` | no | `PackageManager.getLaunchIntentForPackage` (match difuso etiqueta/paquete) |
| `MAKE_CALL` | `target` | **sí** | `ACTION_CALL` (perm `CALL_PHONE`); si no se concede, cae a `ACTION_DIAL` |
| `SEND_MESSAGE` | `recipient`, `body`, `app?` | **sí** | `ACTION_SENDTO` (`smsto:`) compositor pre-rellenado (sin perm) |
| `SET_ALARM` | `time`, `label?` | no | `AlarmClock.ACTION_SET_ALARM` |
| `SET_TIMER` | `duration_seconds`, `label?` | no | `AlarmClock.ACTION_SET_TIMER` |
| `TOGGLE_SETTING` | `setting`, `state` | no | linterna: `CameraManager.setTorchMode`; wifi/bt/DnD: panel/pantalla de Ajustes |
| `NONE` | — | no | sin acción; mostrar `out_message` |

**Por qué un AccessibilityService.** Los Intents cubren la mayoría de acciones de
forma fiable. Pero las apps **no pueden alternar radios en silencio**
(Wi-Fi/Bluetooth) en Android 10+ (API 29+), y algunos flujos necesitan navegación
del sistema. `AtomAccessibilityService` es el ejecutor de respaldo: expone
`back()/home()/recents()/quickSettings()` vía `performGlobalAction`, accesible por
`AtomAccessibilityService.getInstance()`. Lo habilita el usuario desde Ajustes del
sistema (lo comprueba `PermissionCoordinator`). Por ahora `TOGGLE_SETTING` abre el
panel de Ajustes correspondiente en vez de accionar el toggle por accesibilidad —
más seguro y predecible; el servicio queda cableado y listo para flujos por gestos.

### 2.4 UX de confirmación

`requires_confirmation` (true para `MAKE_CALL`, `SEND_MESSAGE`) hace que el
ViewModel emita `pendingConfirmation`; `MainActivity` muestra un `AlertDialog` con
`out_message` y solo llama a `runAction` con **Sí**. Las acciones no sensibles se
ejecutan de inmediato; `NONE` muestra `out_message` como respuesta conversacional.

### 2.5 Flujo

```
El usuario escribe/dice una orden
  → ChatViewModel.sendOrder(text)
  → CommandRepository.recognize()         [hilo en segundo plano]
  → ExecuteCommandPortIn → gRPC ExecuteCommand → ResolvedAction
  → si NONE: mostrar out_message
    si no, si requires_confirmation: AlertDialog → (Sí) →
    CommandRepository.run() → ActionExecutorPortOut → AndroidActionExecutor
  → ActionOutcome (éxito/mensaje) → texto de estado
```

---

## 3. Consecuencias

**Positivas**
- Reconocimiento y ejecución son testeables por separado; `ActionExecutorPortOut`
  y `ExecuteCommandPortIn` son mockeables sin Android ni un backend en vivo.
- Añadir una acción es un cambio localizado (ver §4).
- Retrocompatible: un `action_type` desconocido de un backend más nuevo degrada a
  `NONE` (`ActionType.fromWire`), así una app antigua nunca falla.

**Negativas / riesgos**
- La resolución de `OPEN_APP` es heurística (etiqueta/paquete contiene); nombres
  ambiguos pueden resolverse mal. Mitigación: gana el match exacto de etiqueta; si
  no, el primer "contiene".
- Los toggles de radio no son silenciosos (restricción de plataforma) — el usuario
  termina el cambio en el panel de Ajustes.
- Nuevos permisos en runtime (`CALL_PHONE`, `ACCESS_NOTIFICATION_POLICY`) deben
  solicitarse vía `PermissionCoordinator` antes de las acciones correspondientes.
- Visibilidad de paquetes: se requiere un bloque `<queries>` (Android 11+) para
  que `OPEN_APP` vea las apps lanzables.

---

## 4. Cómo añadir una acción nueva (mantener ambos lados sincronizados)

1. **Backend** — añade un `ActionSpec` en `Atom-agent/domain/intent/catalog.py`
   (nombre de tool, slots, `requires_confirmation`). Se vincula solo como tool.
2. **Android** — añade la constante al enum `ActionType`, un `case` en
   `AndroidActionExecutor.execute`, y cualquier permiso nuevo en el manifest.
3. Actualiza la tabla del catálogo aquí y en `INTENT_ACTIONS_CONTRACT.md`.
4. No hace falta cambiar el proto salvo que añadas **campos de respuesta** nuevos.

---

## 5. Notas de pruebas

- `InteractionGrpcAdapterTest` cubre el mapeo wire → `ResolvedAction`
  (conversacional `NONE` y una acción ejecutable) sobre un servidor gRPC
  in-process.
- El parseo de parámetros usa `org.json`, que es solo un stub en pruebas unitarias
  de JVM; por eso las pruebas ejercitan la ruta sin parámetros. El parseo de slots
  se cubre con pruebas instrumentadas / verificación manual en dispositivo.
- **Hueco preexistente conocido:** el source set de tests referencia JUnit
  Jupiter, Mockito, AssertJ y grpc-testing, que **no están declarados** en
  `build.gradle.kts` (`testImplementation` solo tiene JUnit 4). El source set de
  pruebas unitarias no compila hasta añadir esas dependencias. El código de
  producción (`compileDebugJavaWithJavac`) compila sin errores.

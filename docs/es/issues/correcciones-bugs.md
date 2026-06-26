# Registro de Correcciones de Bugs — Atom App (Android / Java)
**Versión:** 1.0.0
**Proyecto:** Atom — Asistente de IA para Dispositivos Móviles
**Componente:** `Atom-app` (Android · Java · gRPC · Accessibility Service · EncryptedTokenStore)
**Arquitectura:** Hexagonal (Domain – Application – Infrastructure)

---

> **Propósito:** Este documento registra de forma retroactiva los bugs detectados durante el desarrollo (fase QA) y las correcciones aplicadas en la app móvil. Cada entrada documenta síntoma, causa raíz, solución, reproducción y verificación.
>
> **Convención de IDs:**
> - Corrección: `FIX-APP-[NNN]`
> - Referencia a la Historia de Usuario (US) y al commit de Git que aplicó el cambio.
>
> **Niveles de severidad:** `Crítico` · `Alto` · `Medio` · `Bajo`

---

## Índice

| ID | Título | Severidad | US | Commit |
|----|--------|-----------|----|--------|
| FIX-APP-001 | Refresco de token en el hot path de cada RPC autenticada | Alto | US-E3 | `9198a36` |
| FIX-APP-002 | Token vencido enviado antes del loop de comandos autónomo | Alto | US-E3 | `cf74bea` |
| FIX-APP-003 | Token vencido enviado al enviar un mensaje de chat | Alto | US-E3 | `a882c30` |
| FIX-APP-004 | Regresión de test por deadlines gRPC (`withDeadlineAfter`) | Bajo | US-E2 | `018a412` |
| FIX-APP-005 | El `userId` verificado por el servidor se perdía | Medio | US-D1 | `943eb5d` |
| FIX-APP-006 | Comando y chat usaban sesiones distintas | Medio | US-D2 | `73da360` |
| FIX-APP-007 | RPC bloqueantes sin deadline (cuelgues/ANR) | Alto | US-E2 | `40c3c55` |
| FIX-APP-008 | Crash-loop al arranque por keystore invalidado | Crítico | US-E1 | `6ff15aa` |
| FIX-APP-009 | Imprecisiones de automatización por accesibilidad | Alto | — | `1d1426e` |
| FIX-APP-010 | Abre la foto de perfil en vez del chat (WhatsApp) | Alto | — | `92b9a79` |
| FIX-APP-011 | Deep link de WhatsApp falla sin formato E.164 | Alto | — | `fd4fb2e` |
| FIX-APP-012 | Reconocimiento de voz en idioma incorrecto | Medio | — | `3830103` |
| FIX-APP-013 | Mensaje de validación incorrecto en login vacío | Bajo | — | `f55169e` |

---

> **Épica US-E3 (FIX-APP-001 → 002 → 003):** estos tres fixes comparten una sola decisión de diseño desplegada por etapas. `FIX-APP-001` saca el refresco de token del hot path (interceptor cache-only), lo que abre intencionalmente un hueco funcional que `FIX-APP-002` (comandos) y `FIX-APP-003` (chat) cierran en cada superficie autenticada. **Toda nueva superficie autenticada debe acordarse de llamar `refreshIfNeeded()` antes del flujo, o reintroducirá el bug.**

---

## FIX-APP-001 — Refresco de token en el hot path de cada RPC autenticada

- **Commit:** `9198a36` ("fix(auth): move token refresh off the call path") · **US:** US-E3
- **Severidad:** Alto
- **Módulos afectados:** `app/di/AppContainer.java`, `application/usecase/security/AuthUseCase.java`, test `security/TokenRefreshOffPathTest.java`

### Descripción del bug
El interceptor gRPC leía el token mediante `AuthUseCase::getValidAccessToken`, un método `synchronized` que dispara un refresco de token bloqueante (RPC). Cualquier llamada protegida podía quedar bloqueada esperando una red lenta, y bajo un lock que serializa al resto de llamadas (riesgo de ANR / latencia en cadena en la UI).

### Causa raíz
El supplier del interceptor (`tokenSupplierHolder`) apuntaba a la lógica refresh-aware y bloqueante: el refresco ocurría **dentro** del camino crítico de la llamada.

### Solución aplicada
- Se añade `getCachedAccessToken()`: lee `tokenStore` sin emitir RPC (puede devolver un token expirado).
- Se renombra/aísla la lógica bloqueante como `refreshIfNeeded()` para invocarla **fuera** del hot path.
- El holder del interceptor pasa a apuntar a `getCachedAccessToken`, de modo que el interceptor nunca bloquea.

### Pasos para reproducir (bug original)
Con red lenta o inestable, lanzar una RPC autenticada justo cuando el token está por expirar → el hilo llamante se bloquea mientras se refresca.

### Verificación / pasos de prueba
Test `cachedAccessToken_neverCallsGateway_evenWhenExpired`: devuelve el token cacheado (aunque esté "expired") sin tocar el gateway. Confirmar que el interceptor no emite RPC de refresh.

### Impacto / Áreas afectadas
Camino de toda RPC autenticada. Responsividad de la UI bajo red degradada.

### Notas (regresiones / dependencias)
Introduce de forma intencionada un hueco funcional: si nadie llama `refreshIfNeeded()` antes del flujo, se envía un token cacheado/expirado. Esto es exactamente lo que cierran `FIX-APP-002` y `FIX-APP-003`.

---

## FIX-APP-002 — Token vencido enviado antes del loop de comandos autónomo

- **Commit:** `cf74bea` ("fix(auth): refresh-ahead before the authenticated command loop") · **US:** US-E3 (follow-up)
- **Severidad:** Alto
- **Módulos afectados:** `app/repository/CommandRepository.java`, `app/di/AppContainer.java`, `app/overlay/FloatingBubbleService.java`, `app/viewmodel/ChatViewModelFactory.java`, `application/port/in/security/AuthPortIn.java`, `AuthUseCase.java`, test `CommandRepositoryAutonomousTest`

### Descripción del bug
Tras pasar el interceptor a *cache-only* (`FIX-APP-001`), ejecutar un comando autónomo justo después de la expiración del token enviaba un token stale al backend → la primera RPC protegida del loop fallaba por autenticación.

### Causa raíz
`CommandRepository` no tenía referencia a `AuthPortIn` y no primaba el token antes del loop de comandos.

### Solución aplicada
- Se inyecta `AuthPortIn` en todos los constructores de `CommandRepository`.
- Al inicio de `executeAutonomous`, en el executor de fondo, se llama `authUseCase.refreshIfNeeded()` (con null-check) antes de la primera RPC. Un fallo de refresh cae por el catch compartido como aborto normal.
- Se expone `refreshIfNeeded()` en la interfaz `AuthPortIn`.

### Pasos para reproducir (bug original)
Esperar a que expire el token y ejecutar un comando de voz autónomo → sin el fix, la primera ejecución falla por token vencido.

### Verificación / pasos de prueba
Test `refreshesTokenBeforeFirstCommandRpc` con `InOrder`: `refreshIfNeeded()` se invoca antes de `useCase.execute(...)`.

### Impacto / Áreas afectadas
Loop de comandos autónomos (ReAct). Continuidad de la sesión autenticada.

### Notas (regresiones / dependencias)
Cambia firmas de constructor de `CommandRepository` (actualizadas en este commit). El null-check sugiere que algún path puede pasar `authUseCase` nulo. Depende de `FIX-APP-001`.

---

## FIX-APP-003 — Token vencido enviado al enviar un mensaje de chat

- **Commit:** `a882c30` ("fix(auth): refresh-ahead before chat send") · **US:** US-E3 (chat follow-up)
- **Severidad:** Alto
- **Módulos afectados:** `app/repository/ChatRepository.java`, `app/overlay/FloatingBubbleService.java`, `app/viewmodel/ChatViewModelFactory.java`, test `ChatRepositoryRefreshTest.java`

### Descripción del bug
Enviar un mensaje de chat inmediatamente tras la expiración del token mandaba un token cacheado stale → la RPC `messageChat` fallaba por autenticación. Es el mismo hueco que `FIX-APP-002`, pero en la superficie de chat (que aquel no cubrió).

### Causa raíz
`ChatRepository` no conocía `AuthPortIn` y no primaba el token (espejo exacto del problema de comandos).

### Solución aplicada
- Se inyecta `AuthPortIn` en `ChatRepository`.
- En `askAtom`, dentro del executor de fondo, se llama `refreshIfNeeded()` antes de `streamChatUseCase.messageChat(...)`, replicando el patrón de `CommandRepository`.

### Pasos para reproducir (bug original)
Expirar el token y enviar un mensaje de chat → sin el fix, la respuesta falla por token vencido.

### Verificación / pasos de prueba
Test `refreshesTokenBeforeChatRpc` con `CountDownLatch` + `InOrder`: `refreshIfNeeded()` antes de `messageChat(...)`.

### Impacto / Áreas afectadas
Superficie de chat. Completa la cobertura de US-E3 junto con `FIX-APP-002`.

### Notas (regresiones / dependencias)
Cambia la firma del constructor de `ChatRepository` (4 argumentos). Depende de `FIX-APP-001`.

---

## FIX-APP-004 — Regresión de test por deadlines gRPC (`withDeadlineAfter`)

- **Commit:** `018a412` ("fix(test): stub withDeadlineAfter") · **US:** US-E2 (regresión)
- **Severidad:** Bajo (solo afecta a tests; sin cambio de producción)
- **Módulos afectados:** `test/.../grpc/AuthGrpcAdapterTest.java`

### Descripción del bug
Tras añadir deadlines gRPC (`FIX-APP-007`), `AuthGrpcAdapterTest` fallaba: el adapter ahora encadena `stub.withDeadlineAfter(...)` antes de cada RPC, y el mock del stub devolvía `null` por defecto en ese método → `NullPointerException` al encadenar `.login()/.register()` sobre `null`.

### Causa raíz
El mock no tenía stub para `withDeadlineAfter`; Mockito devolvía `null` en lugar del propio stub, rompiendo la cadena fluida.

### Solución aplicada
`@BeforeEach` que stubea `when(stub.withDeadlineAfter(anyLong, any(TimeUnit))).thenReturn(stub)`, de modo que la cadena fluida resuelva al mismo mock.

### Pasos para reproducir (bug original)
Hacer checkout entre `FIX-APP-007` (`40c3c55`) y este commit, y correr `AuthGrpcAdapterTest` → `NullPointerException`.

### Verificación / pasos de prueba
La suite de `AuthGrpcAdapterTest` vuelve a pasar.

### Impacto / Áreas afectadas
Solo el árbol de tests. Ningún impacto funcional en la app.

### Notas (regresiones / dependencias)
Regresión de test causada por `FIX-APP-007` (US-E2). No es un bug de la aplicación.

---

## FIX-APP-005 — El `userId` verificado por el servidor se perdía

- **Commit:** `943eb5d` ("fix(auth): persist and expose server-verified user id") · **US:** US-D1
- **Severidad:** Medio
- **Módulos afectados:** `domain/security/TokenPair.java`, `infrastructure/adapter/grpc/AuthGrpcAdapter.java`, `application/port/out/security/TokenStore.java`, `infrastructure/adapter/out/security/EncryptedTokenStore.java`, `application/usecase/security/AuthUseCase.java`, test `ServerUserIdTest.java`

### Descripción del bug
El `userId` verificado por el servidor (presente en `AuthResponse`) se perdía: no se mapeaba ni se persistía, así que la app no podía exponerlo. Sin él, comando y chat no pueden compartir una identidad de backend (habilitador de `FIX-APP-006`).

### Causa raíz
`TokenPair` no tenía campo `userId`; `AuthGrpcAdapter.toPair` no leía `r.getUserId()`; `TokenStore`/`EncryptedTokenStore` no tenían slot para él.

### Solución aplicada
- Se añade `userId` a `TokenPair` (constructor sobrecargado + `fromExpiresIn` con `userId`, retrocompatible vía `null`).
- `AuthGrpcAdapter` lo propaga.
- `EncryptedTokenStore` persiste `server_user_id` (y lo limpia en `clear()`).
- `AuthUseCase.persist` guarda el id si no es vacío y expone `getServerUserId()`.

### Pasos para reproducir (bug original)
Login exitoso y luego consultar la identidad del usuario → antes del fix no había forma de obtener el id de servidor (siempre `null`/aleatorio).

### Verificación / pasos de prueba
Test `login_persistsServerUserId_andExposesIt`: verifica `saveUserId` y `getServerUserId`.

### Impacto / Áreas afectadas
Persistencia de identidad de usuario. Prerrequisito de la sesión compartida (`FIX-APP-006`).

### Notas (regresiones / dependencias)
`clear()` debe borrar también el user id (cubierto). Prerrequisito de `FIX-APP-006`.

---

## FIX-APP-006 — Comando y chat usaban sesiones distintas

- **Commit:** `73da360` ("fix(session): share one session id across command & chat") · **US:** US-D2
- **Severidad:** Medio
- **Módulos afectados:** `app/di/AppContainer.java`, `app/repository/CommandRepository.java`, `app/overlay/FloatingBubbleService.java`, `app/viewmodel/ChatViewModelFactory.java`, `AuthPortIn.java`/`AuthUseCase.java`, tests `CommandSessionIdTest.java`, `CommandRepositoryAutonomousTest`

### Descripción del bug
`CommandRepository` generaba su propio `UUID.randomUUID()` por instancia, distinto del session id usado por chat. Resultado: comando y chat no compartían sesión/contexto en el backend (aparecían como dos usuarios/sesiones distintas).

### Causa raíz
Id de sesión auto-generado localmente (`private final UUID sessionUserId = UUID.randomUUID();`) en `CommandRepository`, en vez de inyectar uno compartido.

### Solución aplicada
- Se elimina el random interno y se **inyecta** `sessionUserId` en todos los constructores.
- `AppContainer.getSessionUserId()` deriva un UUID estable del `getServerUserId()` (US-D1) vía `UUID.nameUUIDFromBytes(serverId)`, con fallback al UUID persistido del dispositivo pre-login. Así command y chat comparten una sola sesión tras autenticarse.

### Pasos para reproducir (bug original)
Loguearse, enviar un mensaje de chat y luego un comando → en backend aparecen como dos usuarios/sesiones distintas (ids no coincidentes).

### Verificación / pasos de prueba
Test `autonomousLoop_usesTheInjectedSharedSessionId`: la RPC `execute` recibe el id inyectado, no un random.

### Impacto / Áreas afectadas
Coherencia de sesión entre comando y chat. Contexto compartido en el backend.

### Notas (regresiones / dependencias)
Cambia múltiples constructores de `CommandRepository` (visibilidad ampliada a `public`; `MainThreadPoster` ahora `public`). Depende de `FIX-APP-005`. El derivado por `nameUUIDFromBytes` cambia el id efectivo entre pre-login y post-login (transición de sesión esperada).

---

## FIX-APP-007 — RPC bloqueantes sin deadline (cuelgues/ANR)

- **Commit:** `40c3c55` ("fix(grpc): deadlines on all blocking RPCs") · **US:** US-E2
- **Severidad:** Alto
- **Módulos afectados:** `infrastructure/adapter/grpc/AuthGrpcAdapter.java`, `infrastructure/adapter/grpc/InteractionGrpcAdapter.java`, test `InteractionGrpcAdapterTest`

### Descripción del bug
Ninguna RPC bloqueante (`register`/`login`/`authenticateWithGoogle`/`refreshToken` y `executeCommand`/`transcribe`) tenía deadline → ante una red colgada o un servidor que no responde, la llamada se bloqueaba sin límite (ANR / spinner eterno).

### Causa raíz
Los stubs gRPC bloqueantes se invocaban sin `withDeadlineAfter(...)`.

### Solución aplicada
Deadlines explícitos: 15 s en las RPC de auth y 30 s (`COMMAND_DEADLINE_SECONDS`/`TRANSCRIBE_DEADLINE_SECONDS`) en interacción, encadenando `withDeadlineAfter(...)` antes de cada llamada.

### Pasos para reproducir (bug original)
Simular un servidor que acepta la conexión pero no responde → sin deadline la llamada nunca retorna.

### Verificación / pasos de prueba
Test `executeCommand_setsADeadlineOnTheCall`: captura el `Deadline` del `Context` en el fake service y exige que no sea `null`.

### Impacto / Áreas afectadas
Todas las RPC bloqueantes. Robustez ante red degradada.

### Notas (regresiones / dependencias)
- Causó la regresión de test arreglada en `FIX-APP-004` (`018a412`).
- Posible: comandos legítimos largos que superen 30 s ahora se cancelan (`DEADLINE_EXCEEDED`); conviene manejar ese status en el mapeo de errores.

---

## FIX-APP-008 — Crash-loop al arranque por keystore invalidado

- **Commit:** `6ff15aa` ("fix(security): recover from invalidated keystore") · **US:** US-E1
- **Severidad:** Crítico
- **Módulos afectados:** `infrastructure/adapter/out/security/EncryptedTokenStore.java`, test `EncryptedTokenStoreRecoveryTest.java`

### Descripción del bug
Si la master key del Keystore quedaba invalidada (reset de credenciales del dispositivo, restore a un dispositivo nuevo), `EncryptedSharedPreferences.create` lanzaba excepción y el constructor relanzaba `IllegalStateException` → la app crasheaba al lanzar, **en bucle**, sin recuperación. La app quedaba inutilizable sin reinstalar.

### Causa raíz
Apertura de los prefs cifrados sin estrategia de recuperación: una sola tentativa, y el fallo era fatal.

### Solución aplicada
`openWithRecovery(factory, wipe)`: intenta abrir (intento 0); si falla, ejecuta `wipeCorruptStore` (borra el archivo de prefs y la entrada de master key `_androidx_security_master_key_` del AndroidKeyStore, best-effort) y reintenta (intento 1). Si el reintento también falla, lanza `IllegalStateException`. El usuario simplemente vuelve a iniciar sesión en vez de quedar bloqueado.

### Pasos para reproducir (bug original)
Invalidar la master key (cambiar/eliminar el bloqueo de pantalla en ciertos dispositivos, o restore a un dispositivo nuevo) y abrir la app → crash al inicio antes del fix.

### Verificación / pasos de prueba
Tests `recoversOnceWhenFirstAttemptFails` (2 intentos + wipe) y `rethrowsWhenRecoveryAlsoFails`.

### Impacto / Áreas afectadas
Arranque de la app y almacenamiento seguro de tokens. Elimina el crash-loop.

### Notas (regresiones / dependencias)
- El wipe borra los tokens persistidos → el usuario pierde la sesión y debe re-loguear (comportamiento aceptado/intencionado).
- El alias hardcodeado `_androidx_security_master_key_` depende de la versión de `androidx.security`; si cambia, el borrado de la key fallaría silenciosamente (aunque el borrado del archivo suele bastar).

---

## FIX-APP-009 — Imprecisiones de automatización por accesibilidad

- **Commit:** `1d1426e` ("fix(a11y): accent-folding, word-boundary tap ranking, multi-window editable resolution, submit fallback")
- **Severidad:** Alto (fix compuesto)
- **Módulos afectados:** `infrastructure/adapter/accessibility/AtomAccessibilityService.java`, test `AtomAccessibilityServiceRankTest.java`

### Descripción del bug
Cuatro fallos de precisión de automatización corregidos a la vez:
1. Matching de nombres sensible a acentos/caso: no encontraba "María" si el needle venía sin acento.
2. Un needle corto como "ana" hacía substring-match contra "susana" → tocaba el contacto equivocado.
3. Al teclear en buscadores (p. ej. Google), la ventana activa es el popup del IME, no el campo editable → no resolvía el campo y no escribía.
4. Enviar la búsqueda fallaba en apps con IME no estándar (p. ej. TikTok), donde solo había `IME_ENTER` en API 30+.

### Causa raíz
1. Usaba `toLowerCase` sin normalizar acentos.
2. Ranking solo exact/prefix/substring, sin tier de palabra ni guarda de longitud.
3. `resolveEditable` solo miraba `getRootInActiveWindow`.
4. `submit` solo intentaba `ACTION_IME_ENTER` y luego un botón localizado.

### Solución aplicada
1. `TextNormalizer.fold` (lowercase + quitar acentos) aplicado a haystack y needle.
2. Nuevo tier `RANK_WORD` (token entero) entre prefix y substring, y `MIN_SUBSTRING_NEEDLE=4` que bloquea el substring-match de needles cortos.
3. `resolveEditable` ahora recorre **todas** las ventanas (`getWindows()`) vía `editableInRoot`.
4. `submit` añade un fallback intermedio `ACTION_CLICK` sobre el campo enfocado, con logging por rama. Además, mejor selección del target de tap y corrección de gestión de nodos (`findScrollable` devuelve `obtain` y recicla correctamente, evitando use-after-recycle/leaks).

### Pasos para reproducir (bug original)
1. Pedir abrir el chat de "Maria" sin acento.
2. Pedir "ana" con un contacto "Susana" en la lista → abre Susana.
3. Dictar texto en el buscador de Google → no escribe.
4. Buscar en TikTok → el texto se pone pero no se envía.

### Verificación / pasos de prueba
Tests de ranking (`rankFor`): exact/prefix/word/substring y guarda de needle corto ("ana" vs "susana" = no match). Verificación manual de escritura multi-ventana y submit por click.

### Impacto / Áreas afectadas
Servicio de accesibilidad: matching de contactos, escritura y envío en apps de terceros.

### Notas (regresiones / dependencias)
- El fallback de `submit` por `ACTION_CLICK` podría disparar comportamientos no deseados en campos donde un click no equivale a enviar.
- La gestión de reciclado de nodos cambió en varias rutas (riesgo de regresión si hay rutas no cubiertas).
- `MIN_SUBSTRING_NEEDLE=4` puede excluir matches legítimos de needles de 3 letras.
- Introduce `TextNormalizer.fold`, del que depende `FIX-APP-010`.

---

## FIX-APP-010 — Abre la foto de perfil en vez del chat (WhatsApp)

- **Commit:** `92b9a79` ("fix(a11y): skip profile-image avatar nodes in contact DFS")
- **Severidad:** Alto
- **Módulos afectados:** `infrastructure/adapter/accessibility/AtomAccessibilityService.java`

### Descripción del bug
En WhatsApp, la DFS de búsqueda por nombre matcheaba el nodo del avatar (un `ImageView` cuyo `contentDescription` es "Foto de perfil de María") y lo tocaba → se abría la foto de perfil en vez del chat.

### Causa raíz
El DFS evaluaba como candidato cualquier nodo cuyo texto/descripción matcheara, sin distinguir el avatar (que contiene el nombre en su descripción) de la fila de chat.

### Solución aplicada
`isStructuralImage(node)` (vía `isProfileImageDesc` con marcadores foldeados: "foto de perfil", "avatar", "profile picture", etc.) excluye el avatar de ser elegido como match, pero **sigue recorriendo** sus hijos. No se descartan todos los `ImageView` (los botones de icono "Ajustes"/"Historial" siguen siendo accionables por su `contentDescription`).

### Pasos para reproducir (bug original)
Pedir abrir el chat de un contacto en WhatsApp cuya fila tiene avatar con descripción "Foto de perfil de X" → se abre la foto.

### Verificación / pasos de prueba
Sin test unitario en el commit; verificación manual (abre el chat, no la foto) e inspección de `isProfileImageDesc`. Limitación: la cobertura de marcadores es por idioma (es/en); otros idiomas no están cubiertos.

### Impacto / Áreas afectadas
Acción de abrir chat de contacto en WhatsApp vía accesibilidad.

### Notas (regresiones / dependencias)
- Lista de marcadores hardcodeada y dependiente del idioma/versión de WhatsApp; si WhatsApp cambia el texto del `contentDescription`, el skip deja de aplicar.
- Construye sobre `TextNormalizer.fold` introducido en `FIX-APP-009` (dependencia).

---

## FIX-APP-011 — Deep link de WhatsApp falla sin formato E.164

- **Commit:** `fd4fb2e` ("fix(action): normalize WhatsApp number to E.164 and fall back to home-screen search")
- **Severidad:** Alto
- **Módulos afectados:** `infrastructure/adapter/action/AndroidActionExecutor.java`, `app/di/AppContainer.java`, `infrastructure/adapter/out/device/TelephonyE164Normalizer.java` (nuevo), `application/port/out/PhoneNumberNormalizerPortOut.java` (nuevo), `res/values*/strings.xml`

### Descripción del bug
El deep link `wa.me/<numero>` se construía con el número local crudo (`digitsOnly`). `wa.me` **exige formato E.164**; un número local no E.164 hacía que WhatsApp abriera su pantalla de inicio (o un `wa.me` sin destino) en vez del chat. Sin número, además, usaba un `wa.me/?text=` sin destinatario.

### Causa raíz
Falta de normalización a E.164 y un fallback inadecuado (`wa.me` sin destino).

### Solución aplicada
- Nuevo `PhoneNumberNormalizerPortOut` + adapter `TelephonyE164Normalizer` (infiere la región del dispositivo), inyectado en `AndroidActionExecutor`.
- `sendWhatsApp` normaliza a E.164; si hay E.164, usa el deep link al chat; si no hay número normalizable, **abre la pantalla de inicio de WhatsApp** (launch intent del paquete `com.whatsapp`) para que el loop ReAct del backend busque al contacto por nombre en el siguiente turno.
- Si WhatsApp no está instalado, falla con un mensaje claro. Strings nuevas `action_whatsapp_search_fallback` (es/en).

### Pasos para reproducir (bug original)
Pedir enviar un WhatsApp a un contacto cuyo número está en formato local (sin prefijo de país) → abre el home de WhatsApp / compose sin destino en vez del chat.

### Verificación / pasos de prueba
Sin test en el diff; verificación manual con número local (debe abrir el chat) y con nombre sin número (debe abrir el home de WhatsApp con el mensaje de fallback). Recomendado: test del normalizer.

### Impacto / Áreas afectadas
Acción de envío de mensajes por WhatsApp.

### Notas (regresiones / dependencias)
- Cambia la firma del constructor de `AndroidActionExecutor` (3 argumentos).
- La inferencia de región depende del SIM/locale del dispositivo: un número local de otro país puede normalizarse mal.

---

## FIX-APP-012 — Reconocimiento de voz en idioma incorrecto

- **Commit:** `3830103` ("fix(voice): recognize speech in the app's chosen language")
- **Severidad:** Medio
- **Módulos afectados:** `infrastructure/adapter/voice/AndroidSpeechRecognizer.java`

### Descripción del bug
El reconocedor pasaba un objeto `Locale` (`Locale.getDefault()`) a `EXTRA_LANGUAGE`, que **debe ser un String BCP-47**. El objeto se ignoraba silenciosamente → el servicio caía a su default (en-US) y mis-transcribía el español en teléfonos cuyo sistema estaba en otro idioma.

### Causa raíz
Tipo incorrecto en el extra (`Locale` en vez de un String tag) y, además, seguía el locale del dispositivo en lugar de la preferencia de idioma de la app.

### Solución aplicada
`resolveLanguageTag()` lee `AtomPreferences.getLanguage()` (fuente de verdad): `en`/`es` explícito gana; `system` o `null` → `Locale.getDefault().toLanguageTag()`. Se asigna como String en `EXTRA_LANGUAGE` y también en `EXTRA_LANGUAGE_PREFERENCE`.

### Pasos para reproducir (bug original)
Teléfono en inglés, app configurada en español, dictar en español → se transcribe como inglés (texto incorrecto).

### Verificación / pasos de prueba
Sin test unitario (depende del servicio Android); verificación manual: app en español + teléfono en inglés transcribe correctamente el español.

### Impacto / Áreas afectadas
Reconocimiento de voz (STT) para usuarios no-inglés.

### Notas (regresiones / dependencias)
Depende de que `AtomPreferences` exponga `getLanguage()`/`LANGUAGE_SYSTEM`. Bajo riesgo de regresión.

---

## FIX-APP-013 — Mensaje de validación incorrecto en login vacío

- **Commit:** `f55169e` ("fix(auth): show proper validation message for empty login fields")
- **Severidad:** Bajo
- **Módulos afectados:** `app/LoginActivity.java`, `res/values/strings.xml`

### Descripción del bug
Con campos de login vacíos, el mensaje de error mostraba `auth_email_hint` (el literal "Email") en lugar de un mensaje de validación útil.

### Causa raíz
String key equivocada en la rama de campos vacíos de `submit()`.

### Solución aplicada
Se usa la nueva string `auth_fields_required` ("Completa todos los campos.").

### Pasos para reproducir (bug original)
Pulsar login con el email o la contraseña vacíos → muestra "Email".

### Verificación / pasos de prueba
Manual: dejar un campo vacío y enviar → muestra "Completa todos los campos.".

### Impacto / Áreas afectadas
Mensajería de validación en la pantalla de login. Defecto cosmético/UX.

### Notas (regresiones / dependencias)
La nueva string se agregó en `values/strings.xml`; conviene confirmar que exista también en `values-es` (no aparece en el diff — posible falta de localización, aunque el valor por defecto ya está en español).

---

_Última actualización: 2026-06-26._

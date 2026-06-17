# Proyecto Atom: Movimiento del Overlay Flotante — Identidad de la Burbuja, Animación del Micrófono y Ocultamiento en el Borde

> Complemento de [`Atom-Redesign.md`](./Atom-Redesign.md) §4 (el overlay flotante
> del sistema) y de [`Atom-View.md`](./Atom-View.md) (la visión original "Aether").
> Esa sección estableció la burbuja arrastrable y el flujo de expansión al panel;
> este documento cubre la pasada de **movimiento e identidad** construida encima:
> la burbuja ahora lleva la marca Atom, el micrófono respira mientras escucha, y la
> burbuja puede esconderse en un borde de la pantalla.

Las tres funciones tocan una sola superficie de pantalla — la burbuja colapsada y su
panel de entrada — y se implementan sin nuevas dependencias (sin Lottie, sin cambios
en Gradle), usando solo animadores de propiedades de `android.animation` y un drawable
vectorial.

---

## 1. Identidad de la burbuja — la marca Atom

La burbuja colapsada mostraba antes `ic_mic` sobre el disco con degradado lavanda, lo
que se leía como "un micrófono" en vez de "Atom". Ahora muestra la marca para que la
presencia flotante coincida con el icono de la app.

| Elemento | Antes | Después |
|---|---|---|
| Glifo de la burbuja | `ic_mic` | `ic_atom_glyph` |
| Tinte | `on_accent` | `white` (el glifo resalta sobre el disco lavanda) |
| Fondo del disco | `bg_bubble` | `bg_bubble` (sin cambios) |

### 1.1 La marca compartida

`ic_atom_glyph.xml` reutiliza el **path exacto** de `ic_launcher_foreground.xml`, así
que la burbuja y el icono de la app son la misma ilustración. El path del icono está
autorado para la zona segura del icono adaptativo (un viewport `108x108` con la marca
ubicada en la región interior); reutilizarlo tal cual dejaría el glifo de la burbuja
pequeño y descentrado.

Por eso el glifo se re-proyecta en una caja ajustada de `48x48` mediante una
transformación `<group>`, y el relleno se fija en **blanco** para que el sitio de uso
controle el color a través de `app:tint`:

```
viewport 48x48, relleno #FFFFFF (controlado por tinte)
group: scaleX/Y 0.55, translateX -37.82, translateY -5.66
  -> el contenido del path se centra en (24, 24)
  -> abarca x ~7..41, y ~2..46  (centrado, sin recorte, margen parejo)
```

> Regla de diseño conservada: la ilustración de marca vive en **un solo** path. Si el
> logo cambia, actualiza `ic_launcher_foreground` y copia el path en `ic_atom_glyph`
> — la transformación y el tinte se mantienen igual.

---

## 2. Animación del micrófono

El micrófono del panel (`R.id.overlay_mic` en `view_overlay_panel.xml`) gana dos
movimientos distintos, ambos gobernados desde `FloatingBubbleService`. Reutilizan el
lenguaje del "núcleo que respira" de la pantalla principal para que el overlay se
sienta como el mismo organismo.

### 2.1 Estados

```
REPOSO         escala 1.0, alpha 1.0           inactivo, esperando un toque
  │  toque
  ▼
HUNDIMIENTO    escala 1.0 -> 0.86 -> 1.0       ~90 ms de hundir y recuperar (acuse táctil)
  │
  ▼
ESCUCHANDO     escala 1.0 <-> 1.18             pulso de respiración infinito +
               alpha 1.0 <-> 0.55             desvanecido, mientras hay una petición en curso
  │  respuesta (éxito o error)
  ▼
REPOSO         escala 1.0, alpha 1.0           pulso cancelado, micrófono reiniciado
```

### 2.2 Implementación

- El **hundimiento al pulsar** usa un `ViewPropertyAnimator` (`view.animate()`): una
  breve bajada de escala a `0.86` y de vuelta, acusando el toque antes de que salga la
  petición.
- El **pulso de escucha** es un `ObjectAnimator` sobre `SCALE_X`, `SCALE_Y` y `ALPHA`
  (`PropertyValuesHolder`), con `REPEAT_MODE = REVERSE`, `REPEAT_COUNT = INFINITE` y un
  `AccelerateDecelerateInterpolator` para una suavidad orgánica. Un medio ciclo dura
  `620 ms`.
- El pulso **arranca** al tocar el micrófono (`startMicPulse`) y **se detiene** tanto
  en el callback `onSuccess` como en `onError` de `askAtom` (`stopMicPulse`), que
  además reinicia escala y alpha al reposo. La vista del micrófono se pasa a `askAtom`
  para que el callback pueda alcanzarla.
- La vista puede desacoplarse a mitad de la petición (panel colapsado, app en primer
  plano). `stopMicPulse` tolera una vista `null` / desacoplada, replicando las guardas
  existentes de `status.isAttachedToWindow()`. Las rutas de desmontaje
  (`collapseToBubble`, `hideOverlayViews`, `stopOverlay`, `onDestroy`) cancelan el
  animador vía `cancelAnimations`.

> Hasta que llegue la captura de voz real, el micrófono sigue disparando el prompt fijo
> de prueba (ver [`Atom-Redesign.md`](./Atom-Redesign.md) §5). El pulso es el sustituto
> visual del estado de "escucha" y se mapeará directamente sobre el viaje de STT en
> vivo cuando se conecten `RECORD_AUDIO` + `transcribeAudio`.

---

## 3. Anclaje al borde, forzar-para-ocultar y el asa del borde

La burbuja se puede anclar a un borde de la pantalla, **forzarse contra un borde para
ocultarla**, o guardarse a voluntad desde el panel. Ocultar nunca la hace desaparecer:
se contrae a una pequeña **pestaña con flecha anclada al borde**, de modo que deja de
tapar otras apps mientras queda a un toque de distancia.

### 3.1 Comportamiento

```
ARRASTRE ─────────────────────────────────────────────────────────────►
  soltar cerca de un borde         soltar empujada MÁS ALLÁ del borde
        │                                  │
        ▼                                  ▼
  ANCLAJE (en reposo)                OCULTAR -> asa del borde
  se desliza al ras del borde,       la burbuja se contrae a una
  totalmente visible, margen 8dp     pequeña pestaña con flecha
                                            │  tocar la flecha
                                            ▼
                                     la burbuja vuelve al mismo punto

Boton de ocultar del PANEL (flecha) ───────► misma ruta de OCULTAR
```

- Al soltar el arrastre, la burbuja **se ancla al borde horizontal más cercano**
  (izquierda/derecha según su centro frente a la línea media de la pantalla),
  deslizándose al ras con un margen de 8dp.
- Si el arrastre empuja la burbuja más del `40%` de su ancho más allá de ese borde,
  **se contrae al asa del borde**: una línea blanca fina y luminosa (a juego con el logo
  de Atom) al estilo Game Turbo / panel Edge. La línea visible mide `handle_width` 11dp ×
  `handle_bar_height` 67dp con un halo blanco suave, pegada al ras del borde; la ventana
  táctil es una zona de agarre
  transparente más ancha (`handle_touch_width` 36dp) a su alrededor para que el toque de
  restauración llegue. La burbuja y el panel se retiran, pero el servicio sigue corriendo.
- El panel expandido lleva un botón de **ocultar** (una flecha, junto a Cerrar) que
  dispara la contracción idéntica — de modo que el gesto de mostrar/ocultar queda a un
  toque en ambas direcciones.
- **El asa es arrastrable, igual que la burbuja** — muévela a donde quieras; al soltar se
  desliza al ras del borde más cercano. Permanece **atenuada** (`HANDLE_IDLE_ALPHA` 0.5)
  mientras no se toca y sube a opacidad total en cuanto la tocas, para que sea discreta.
- **Tocar el asa restaura la burbuja** en el borde y la altura donde se dejó por última
  vez. La notificación persistente sigue siendo una vía secundaria de regreso (tocar o
  **Mostrar**), y **Apagar** detiene el overlay por completo. El estado oculto/abierto
  sobrevive a que la app pase a primer plano y regrese: `onAppBackground` vuelve a mostrar
  el estado en que el usuario la dejó.

### 3.2 Implementación

- El anclaje se anima con un `ValueAnimator` (0 → 1) que interpola `bubbleParams.x` /
  `bubbleParams.y`, empujando cada cuadro a través de `windowManager.updateViewLayout`.
  Duración `220 ms`, `DecelerateInterpolator`.
- Ocultar (`hideToHandle`) registra el borde/Y de reposo, quita la burbuja/panel, marca
  `collapsedToHandle` y añade la ventana del asa (`view_overlay_handle.xml`). La línea
  (`handle_bar`) se ancla al borde correspondiente con `applyHandleSide` (su gravity
  alterna start/end), mientras la zona de agarre transparente es contra la que se acopla
  la ventana. `restoreBubble` quita el asa y llama a `showBubbleAt(onLeft, y)`.
- **Restaura al tocar, no con deslizamiento desde el borde**, a propósito: un deslizamiento
  desde el borde chocaría con el gesto Atrás de Android 10+, así que la línea restaura al
  tocar y se reubica al arrastrar.
- El asa tiene su propio `HandleTouchListener` (arrastre / toque), reflejando el de la
  burbuja: el toque la sube a opacidad total, el arrastre mueve la ventana y al soltar se
  ejecuta `settleHandleToEdge` — un `ValueAnimator` que se ancla al borde más cercano
  (usando el ancho **medido** del asa para que el destino del borde derecho quede pegado)
  mientras se atenúa de vuelta a `HANDLE_IDLE_ALPHA`. Un toque sin arrastre llama a
  `restoreBubble`; `ACTION_CANCEL` (gesto robado por el sistema) asienta o atenúa de
  vuelta en lugar de dejar el asa encendida y atascada.
- `onStartCommand` maneja `ACTION_SHOW` (notificación) enrutando a `restoreBubble`;
  `buildNotification(boolean hidden)` mapea el intent de contenido y una acción `Mostrar`
  a `ACTION_SHOW`, y una acción `Apagar` a `ACTION_STOP` (con códigos de petición
  distintos).
- La posición vertical se acota a la pantalla para que ni la burbuja ni el asa queden
  varadas arriba o abajo. Los límites de pantalla vienen de
  `WindowManager.getCurrentWindowMetrics` en API 30+, con un respaldo
  `getDefaultDisplay().getMetrics(...)` por debajo.
- **Adaptabilidad al dispositivo / "nunca varada":** el `BubbleTouchListener` de la burbuja
  maneja `ACTION_CANCEL` (un arrastre robado por la navegación por gestos del borde)
  asentándola al borde; ambos animadores de anclaje aplican su posición final en
  `onAnimationEnd`, de modo que el elemento llega al borde aunque el sistema omita cuadros
  de animación (escala de duración 0 / ahorro de batería); y `onConfigurationChanged`
  reancla y reajusta al rotar o ante cualquier cambio de tamaño de pantalla. Juntos evitan
  que el overlay quede flotando en medio de la pantalla.
- Un respaldo de medición (`bubbleSpan`) usa `R.dimen.bubble_size` para el cuadro raro
  en que la vista aún no fue medida.
- El animador de anclaje en curso se cancela ante un nuevo toque y en cada ruta de
  desmontaje, junto con el pulso del micrófono.

### 3.3 Tokens y tamaño

| Token | Valor | Propósito |
|---|---|---|
| `bubble_size` | `44dp` | disco compacto y poco intrusivo (antes 60dp) |
| `bubble_edge_margin` | `8dp` | separación en reposo respecto al borde |
| `handle_width` | `11dp` | ancho de la línea **visible** del borde |
| `handle_bar_height` | `67dp` | largo de la línea visible del borde |
| `handle_touch_width` | `36dp` | zona de agarre transparente alrededor de la línea para que el toque llegue |
| `handle_height` | `96dp` | alto de la zona de agarre |

Las constantes de movimiento (duraciones, escala del pulso, fracción de empuje) viven
como campos `private static final` en `FloatingBubbleService` — son comportamiento, no
layout, y están documentadas en línea.

---

## 4. Archivos tocados

| Archivo | Cambio |
|---|---|
| `src/main/res/drawable/ic_atom_glyph.xml` | Marca re-centrada para la burbuja |
| `src/main/res/drawable/ic_chevron.xml` | Flecha para el botón de ocultar del panel |
| `src/main/res/drawable/bg_edge_handle.xml` | Fondo de línea blanca luminosa (halo + núcleo) |
| `src/main/res/layout/view_overlay_bubble.xml` | La burbuja muestra `ic_atom_glyph` |
| `src/main/res/layout/view_overlay_panel.xml` | Botón de ocultar con flecha en la cabecera |
| `src/main/res/layout/view_overlay_handle.xml` | Línea de borde fina y luminosa + zona de agarre transparente mostrada al ocultar |
| `src/main/res/values/dimens.xml` | `bubble_size` más pequeño; `bubble_edge_margin`; `handle_*` |
| `src/main/res/values/strings.xml` | Cadenas de ocultar / acciones de notificación + descripciones de contenido |
| `src/main/java/com/atom/app/overlay/FloatingBubbleService.java` | Pulso del micrófono + hundimiento; anclaje; forzar-para-ocultar al asa del borde; restaurar; mostrar/apagar desde la notificación; limpieza de animadores |

> Nota de compilación: a diferencia del rediseño original, esta pasada fue autorada
> **con** el toolchain de Android disponible y verificada con
> `./gradlew compileDebugJavaWithJavac`. La nota de deprecación del respaldo
> `getDefaultDisplay()` previo a API 30 es esperada e intencional.

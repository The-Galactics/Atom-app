# Proyecto Atom: Rediseño de UI — Sistema de diseño, pantallas y overlay flotante

> Complemento de [`Atom-View.md`](./Atom-View.md). Ese documento capturó la visión
> original "Aether"; este documenta el sistema de diseño **refinado** y las
> pantallas rediseñadas, la barra de entrada in-app y el overlay flotante del
> sistema que lo materializan. La inspiración se tomó del **asistente móvil
> Gemini**, conservando la identidad de Atom (lienzo ónix profundo, núcleo
> lavanda, voz serif).

Los colores y la tipografía siguientes están **renderizados usando esos mismos
colores y fuentes** (muestras HTML en línea y especímenes serif/sans), de modo
que esta página es en sí misma una especificación viva.

---

## 1. Sistema de diseño (única fuente de verdad)

Los tokens viven en `src/main/res/values/`:
`colors.xml`, `dimens.xml`, `styles.xml`, además de drawables reutilizables en
`src/main/res/drawable/`. Todo lo demás en la app los consume.

### 1.1 Paleta de colores

#### Base de marca (identidad — sin cambios)

<table>
  <tr>
    <td style="background:#0A0A0C;width:120px;height:56px;border:1px solid #333"></td>
    <td><code>deep_onyx</code> / <code>surface_base</code><br/><b>#0A0A0C</b> — lienzo de pantalla</td>
  </tr>
  <tr>
    <td style="background:#BF94FF;width:120px;height:56px"></td>
    <td><code>purple_lavender</code> / <code>accent</code><br/><b>#BF94FF</b> — núcleo, FAB de micrófono, foco, resaltes</td>
  </tr>
  <tr>
    <td style="background:#121214;width:120px;height:56px;border:1px solid #333"></td>
    <td><code>surface_dark</code> / <code>surface_1</code><br/><b>#121214</b> — tarjetas y campos de entrada</td>
  </tr>
</table>

#### Rampa de elevación de superficies (nuevo)

<table>
  <tr><td style="background:#0A0A0C;width:120px;height:40px;border:1px solid #333"></td><td><code>surface_base</code> <b>#0A0A0C</b></td></tr>
  <tr><td style="background:#121214;width:120px;height:40px;border:1px solid #333"></td><td><code>surface_1</code> <b>#121214</b> — tarjetas / entradas</td></tr>
  <tr><td style="background:#191920;width:120px;height:40px;border:1px solid #333"></td><td><code>surface_2</code> <b>#191920</b> — tarjetas elevadas, panel overlay</td></tr>
  <tr><td style="background:#22222B;width:120px;height:40px;border:1px solid #333"></td><td><code>surface_3</code> <b>#22222B</b> — presionado / con foco</td></tr>
</table>

#### Rampa de acento y par de degradado (nuevo)

<table>
  <tr><td style="background:#D6B8FF;width:120px;height:40px"></td><td><code>accent_bright</code> <b>#D6B8FF</b> — resalte del degradado</td></tr>
  <tr><td style="background:#BF94FF;width:120px;height:40px"></td><td><code>accent</code> <b>#BF94FF</b></td></tr>
  <tr><td style="background:#8C5CF0;width:120px;height:40px"></td><td><code>accent_deep</code> <b>#8C5CF0</b> — extremo profundo del degradado</td></tr>
</table>

El FAB de micrófono, la burbuja y el halo del núcleo usan el degradado
`accent_bright → accent → accent_deep` para que el lavanda se lea como un único
material luminoso.

#### Tintes de texto sobre superficie y estados

<table>
  <tr><td style="background:#0A0A0C;color:#FFFFFF;width:200px;height:34px;padding:0 8px">on_surface_high — 100%</td><td><b>#FFFFFF</b> texto primario</td></tr>
  <tr><td style="background:#0A0A0C;color:#B3B3B3;width:200px;height:34px;padding:0 8px">on_surface_medium — 70%</td><td><b>#B3FFFFFF</b> secundario</td></tr>
  <tr><td style="background:#0A0A0C;color:#808080;width:200px;height:34px;padding:0 8px">on_surface_label — 50%</td><td><b>#80FFFFFF</b> etiquetas</td></tr>
  <tr><td style="background:#0A0A0C;color:#FF8A80;width:200px;height:34px;padding:0 8px">status_error</td><td><b>#FF8A80</b></td></tr>
  <tr><td style="background:#0A0A0C;color:#7EE6A8;width:200px;height:34px;padding:0 8px">status_success</td><td><b>#7EE6A8</b></td></tr>
</table>

### 1.2 Tipografía (escala tipográfica)

Regla de identidad fija: **serif** para títulos de display (logotipo, estado,
títulos de sección, la "voz" de la IA); **sans del sistema** para cuerpo,
etiquetas, botones y leyendas. No se incluyen archivos de fuente — refinamos las
familias `serif` y `sans-serif` de la plataforma solo mediante tamaño, espaciado
y peso. Los estilos viven en `styles.xml`.

| Estilo | Familia | Tamaño | Espécimen |
|---|---|---|---|
| `TextAppearance.Atom.Wordmark` | serif | 26sp / +0.28 | <span style="font-family:Georgia,serif;font-size:26px;letter-spacing:6px">ATOM</span> |
| `TextAppearance.Atom.Display` | serif | 30sp | <span style="font-family:Georgia,serif;font-size:28px">¿En qué te ayudo?</span> |
| `TextAppearance.Atom.Status` | serif (acento) | 22sp | <span style="font-family:Georgia,serif;font-size:22px;color:#BF94FF">Escuchando</span> |
| `TextAppearance.Atom.Title` | serif | 20sp | <span style="font-family:Georgia,serif;font-size:20px">Ajustes</span> |
| `TextAppearance.Atom.Body` | sans | 16sp | <span style="font-family:Helvetica,Arial,sans-serif;font-size:16px">Pregúntale a Atom con tus propias palabras.</span> |
| `TextAppearance.Atom.Label` | sans-medium | 14sp | <span style="font-family:Helvetica,Arial,sans-serif;font-size:14px;font-weight:600">Pro</span> |
| `TextAppearance.Atom.Caption` | sans | 12sp | <span style="font-family:Helvetica,Arial,sans-serif;font-size:12px;color:#808080">Tu nombre</span> |
| `TextAppearance.Atom.Overline` | sans-medium, mayús. | 11sp / +0.18 | <span style="font-family:Helvetica,Arial,sans-serif;font-size:11px;letter-spacing:2px;color:#808080">ESPERANDO PROMPT</span> |

### 1.3 Drawables reutilizables

| Drawable | Propósito |
|---|---|
| `bg_core_glow` | Halo radial lavanda detrás del núcleo Lottie |
| `bg_mic_fab` | Disco de degradado de acento + ripple — el FAB de micrófono |
| `bg_pill_input` | Fondo de entrada tipo píldora con estado de **foco** de acento |
| `bg_surface_card` / `bg_surface_raised` | Superficies redondeadas en elevación 1 / 2 |
| `ripple_borderless` / `ripple_card` | Ripples de acento para íconos fantasma / tarjetas |
| `bg_bubble` | Disco de degradado lavanda para la burbuja del overlay |
| `bg_send_button` | Disco pequeño de acento detrás del ícono de enviar |

---

## 2. Rediseño de la pantalla principal

Archivo: `src/main/res/layout/activity_main.xml` (IDs sin cambios; el cableado de
`MainActivity` intacto).

**Inspiración Gemini:** amplio espacio para respirar, un único héroe luminoso,
una acción primaria circular rellena flanqueada por controles "fantasma"
discretos, y una píldora de entrada que sube desde abajo.

**Identidad de Atom conservada:** el "núcleo atómico" Lottie sigue siendo el
protagonista, ahora sobre un halo radial suave (`bg_core_glow`); la línea de
estado serif ("Escuchando", respuestas) mantiene la voz contemplativa del
asistente; el lienzo ónix profundo y el acento lavanda permanecen intactos.

En concreto:
- La barra superior usa íconos fantasma (`Widget.Atom.IconButton.Ghost`) con el
  logotipo `ATOM` refinado centrado entre Historial y Ajustes.
- El núcleo se ancla algo por encima del centro (guía al 42%) con el halo detrás
  para dar profundidad.
- Estado (`status_text`) + subestado (`sub_status_text`) usan el estilo serif
  `Status` y el `Overline` en mayúsculas; los observers existentes los controlan
  en los estados inactivo / pensando / respondió / error.
- La fila de control centra un **FAB de micrófono relleno de acento**
  (`bg_mic_fab`) entre los botones fantasma de teclado y volumen.

---

## 3. Barra de entrada in-app

Archivos: `src/main/res/layout/view_input_bar.xml` (incluido en la pantalla
principal), cableado en `MainActivity`.

Reemplaza el antiguo flujo de `AlertDialog` por una píldora redondeada estilo
Gemini: micrófono en línea, un `EditText` flexible con el hint **"Ask Atom…"**, y
un disco de enviar de acento relleno. Enfocar el campo activa el borde de acento
de la píldora (`bg_pill_input`, `state_activated`).

**Decisión del disparador — alternable, no persistente.** `btnKeyboard`
muestra/oculta la barra (y el teclado). Razón: la pantalla principal es
voz-primero con el núcleo como héroe; una barra fija competiría con el FAB de
micrófono y recargaría la composición. Alternarla mantiene a `btnKeyboard` como
un disparador con sentido y aun así entrega la experiencia de escritura pulida
bajo demanda.

El envío se enruta por la **misma** ruta gRPC `viewModel.sendMessage()` →
`ChatRepository` → `StreamChatPortIn` que el micrófono — solo difiere la fuente
de entrada. La acción "Enviar" del IME refleja el botón de enviar.

---

## 4. Overlay flotante del sistema (configurar → usar)

> **Ver tambien:** [`Atom-Overlay-Motion.md`](./Atom-Overlay-Motion.md) — la pasada de
> movimiento e identidad sobre este overlay: la burbuja lleva la marca Atom, el
> microfono respira mientras escucha, y la burbuja puede esconderse en un borde de la
> pantalla.

Archivos: `FloatingBubbleService.java` (`com.atom.app.overlay`),
`view_overlay_bubble.xml`, `view_overlay_panel.xml`; entrada de servicio en el
manifest con `foregroundServiceType="specialUse"`.

### 4.1 El flujo UX configurar-luego-usar

> *"La persona primero configura y luego usa; después, cuando necesita la app,
> tiene el sistema de burbuja."*

```
Primer uso (dentro de la app)
  ┌─────────────────────────────────────────────┐
  │ 1. CONFIGURAR                                │
  │    Ajustes → GUARDAR → ensureAssistantPermissions()
  │    concede: overlay, accesibilidad, captura de pantalla
  │ 2. ACTIVAR                                   │
  │    Ajustes → "Activar burbuja flotante"      │
  │    (condicionado al permiso de overlay)      │
  └─────────────────────────────────────────────┘
                       │
                       ▼
Uso cotidiano (encima de cualquier app)
  ┌─────────────────────────────────────────────┐
  │ Burbuja Atom arrastrable sobre otras apps    │
  │ toque → panel de entrada compacto (texto + mic)
  │ enviar → misma ruta gRPC / StreamChat        │
  └─────────────────────────────────────────────┘
```

### 4.2 Arquitectura

- `FloatingBubbleService` es un `Service` en **primer plano** (notificación
  persistente, tipo `specialUse` para Android 14+). Añade una burbuja
  arrastrable al `WindowManager` usando `TYPE_APPLICATION_OVERLAY`.
- El manejo táctil distingue **arrastre** (movimiento más allá del touch-slop →
  reposicionar) de **toque** (→ expandir). La expansión cambia la burbuja por un
  panel de entrada enfocable (`view_overlay_panel.xml`) que recibe teclado.
- El panel reutiliza el sistema de diseño (superficie elevada, píldora de
  entrada, enviar de acento) y despacha prompts mediante un `ChatRepository`
  construido desde `AppContainer.getExternalMessageUseCase()` — **el mismo
  contrato gRPC `StreamChat`** que usa la UI in-app, de modo que hay una sola
  ruta de backend, no dos.
- `SettingsActivity` inicia (`ACTION_START`) / detiene (`ACTION_STOP`) el
  servicio y mantiene la etiqueta del botón sincronizada con el permiso de
  overlay y el estado de ejecución.

---

## 5. Trabajo futuro

- **Captura de voz real.** Los micrófonos (FAB principal, micrófono de la barra,
  micrófono del overlay) envían hoy un prompt fijo de prueba. Cablear
  `RECORD_AUDIO` + el round-trip `transcribeAudio` (ya en
  `ExternalInteractionPortOut`) es el siguiente paso; la reproducción TTS vía
  `synthesizeSpeech` le sigue.
- **Núcleo reactivo al estado.** Controlar velocidades/colores Lottie distintos
  para inactivo / escuchando / pensando desde los observers existentes.
- **Historial de conversaciones** (almacén + pantalla; el botón Historial sigue
  siendo un placeholder).
- **Persistencia** del nombre de perfil / nombre del asistente / volumen.
- **Restauración de sesión de la burbuja** tras la muerte del proceso.

> Nota de compilación: este rediseño se elaboró sin el SDK de Android disponible
> en el entorno, por lo que está **sin verificar la compilación**. Cada ID de
> recurso y símbolo referenciado se verificó a mano contra las fuentes.

# Interacciones UX de la Pantalla Principal [Atom Mobile]

Este documento describe las interacciones de calidad de vida en la pantalla principal (`MainActivity`) y la fila de controles (`activity_main.xml`). Todos los cambios de estado pasan por los helpers existentes `fadeSwap`/`applyCoreState` para que el núcleo de Atom, el texto de estado y el subestado se mantengan sincronizados.

## Silenciar el micrófono

- **Gesto:** mantener pulsado el FAB central del micrófono (`btn_mic`).
- **Comportamiento:** alterna el indicador de silencio persistido. El icono cambia entre `ic_mic` ↔ `ic_mic_off`, se actualizan la descripción de accesibilidad y el subestado, y un háptico `LONG_PRESS` confirma el cambio.
- **Pulsar estando silenciado:** no escucha; muestra el toast `mic_muted_hint`.
- **Silenciar durante la escucha:** desmonta el reconocedor activo y devuelve el núcleo al estado de reposo.
- **Persistencia:** se guarda en `AtomPreferences` (`mic_muted`), por lo que el icono es correcto tras rotar, volver de Ajustes o un arranque en frío. Se restaura una vez en `onCreate` mediante `applyMicMutedState`.
- **Identidad del núcleo:** `applyMicMutedState` también llama a `AtomCoreView.setMuted`, que aplica un filtro de color casi en escala de grises (`MUTED_SATURATION`) y atenuado (`MUTED_ALPHA`) sobre la capa de software del núcleo, de modo que un micrófono silenciado se ve distinto del reposo lavanda vívido.

## Cancelar con un toque durante la escucha

- **Gesto:** tocar el FAB del micrófono mientras hay un reconocimiento en curso.
- **Comportamiento:** cancela el reconocimiento a través del helper compartido `tearDownRecognizer()` (que llama a `destroy()`, ya que `AndroidSpeechRecognizer` no tiene `stopListening()`), detiene el pulso del micrófono y devuelve el núcleo al reposo.
- **Estado:** controlado por el indicador `isListening`, activado en `startListening()` y limpiado al terminar el habla, ante un error o al cancelar.

## Habilitación del botón de enviar

- Un `TextWatcher` sobre el campo de entrada habilita el disco de enviar solo cuando hay texto sin espacios.
- Mientras está deshabilitado se atenúa a `SEND_DISABLED_ALPHA` (0.4). Esto reemplaza el toast anterior "escribe un mensaje primero" como affordance principal (el toast permanece como salvaguarda).

## Retroalimentación háptica

- Toque del micrófono: `VIRTUAL_KEY`.
- Pulsación larga para silenciar: `LONG_PRESS`.
- Enviar: `VIRTUAL_KEY` (solo en un envío válido).

## Recuperación automática del estado de error

- Tras mostrar un error, un `errorRecoverRunnable` diferido devuelve la línea de estado al reposo después de `ERROR_AUTO_RECOVER_MS` (4 s).
- La recuperación se omite si el usuario ha vuelto a escuchar, se cancela cuando inicia un nuevo reconocimiento y se elimina en `onDestroy` para que no se dispare tras el desmontaje. El subestado de reposo respeta el estado de silencio actual.

## Persistencia de estado ante cambios de configuración

- `onSaveInstanceState` guarda el texto de estado visible, el subestado y la última energía/brillo del núcleo (rastreados en `currentEnergy` / `currentGlow`, actualizados por `applyCoreState`).
- `restoreUiState` los reaplica en `onCreate`, de modo que una rotación a mitad de "Pensando" (o cualquier estado) ya no vuelve de golpe a "Listo".

## Silencio en toda la app (overlay)

- La burbuja flotante (`FloatingBubbleService`) lee el mismo indicador persistido `mic_muted`.
- Al construir el panel, `applyOverlayMicMuted` ajusta el icono/etiqueta de `overlay_mic`. Una pulsación larga alterna el indicador compartido (paridad con la pantalla principal) y `startVoiceCapture` se niega a escuchar mientras está silenciado, mostrando `mic_muted_hint`.
- Como el indicador vive en `AtomPreferences`, silenciar en cualquiera de las dos superficies silencia ambas.

## Justificación del permiso de micrófono

- Ante una denegación, `onMicPermissionDenied` comprueba `shouldShowRequestPermissionRationale`:
  - se puede volver a pedir → muestra `mic_permission_rationale` (reintento en el siguiente toque);
  - denegado permanentemente → un `AlertDialog` lleva a los ajustes del sistema de la app mediante `openAppSettings` (`ACTION_APPLICATION_DETAILS_SETTINGS`), para que el micrófono no quede sin salida.

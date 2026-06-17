# Documentación técnica: Prueba de conexión gRPC (Backend) [ATOM].

> **Author:** Atom Team.
> **Contenido:** Prueba de conexión gRPC de extremo a extremo contra el agente Python.
> **Estado:** 🟢 Verificado (con fakes) · 🟡 Ruta de IA real pendiente de claves de API.

---

Este documento describe cómo se probó de extremo a extremo la conexión gRPC entre el cliente Android y el agente de IA en Python (`Atom-agent`), la evidencia obtenida y las brechas que quedan como trabajo futuro. Complementa al documento de pruebas unitarias del lado Android (`ATOM-30-gRPC-gherkin.md`), que valida el adaptador de forma aislada; aquí el foco es el **servidor real** respondiendo sobre un **socket real**.

## 1. Alineación del contrato (sin desincronización):

La conexión solo funciona si ambos extremos comparten el mismo contrato proto. Se compararon los dos archivos campo por campo:

- **Lado App:** `Atom_app/src/main/proto/ai.proto`
- **Lado Backend (fuente de verdad):** `Atom-agent/proto/atom_agent.proto`

Ambos declaran `package com.atom.proto`, `java_package com.atom.infrastructure.adapter.grpc`, `java_outer_classname AiProto`, el servicio `AtomAgentService`, los mismos cuatro RPC (`ExecuteCommand`, `StreamChat`, `Transcribe`, `Synthesize`), y campos de mensaje y números de etiqueta idénticos. **No se encontró desincronización (drift).** Las únicas diferencias son comentarios explicativos en el lado de la App.

## 2. Arquitectura de la prueba:

Para ejercitar la conexión sin levantar proveedores externos (NVIDIA Gemma, Qdrant, Kokoro), se inicia el servicer **real** contra un contenedor simulado:

- **Servicer real:** `AtomGrpcService` de `Atom-agent/infrastructure/grpc/server.py` — la misma clase cableada en la app en ejecución.
- **Contenedor simulado:** provee un `chat_use_case` falso cuyo `execute()` devuelve una respuesta predefinida, para que `StreamChat` se ejecute sin clave de API.
- **Socket real:** `grpc.aio.server()` con `add_insecure_port("[::]:0")` y un `grpc.aio.insecure_channel(...)` — reflejando el transporte de desarrollo en texto plano (`usePlaintext()`) del cliente Android.
- **Stubs generados:** las llamadas del cliente pasan por `AtomAgentServiceStub`, el mismo código generado del que depende la app.

### 2.1 Métodos bajo prueba:

**ExecuteCommand (unario):** Marcador de posición puro en el servidor (sin dependencia externa); siempre confirma el comando. Valida el ida y vuelta unario y la serialización de `CommandRequest` / `CommandResponse`.

**StreamChat (server-streaming):** Ejecuta el método real del servidor con un `chat_use_case` falso. Valida la tubería de streaming (`MessageRequest` de entrada, `stream MessageResponse` de salida) y que `script_token` / `status` / `finished` se completen correctamente.

## 3. Ejecución de la prueba y evidencia:

La prueba vive en el repositorio del backend y se ejecuta como un runner `asyncio` independiente (el proyecto aún no depende de `pytest-asyncio`):

```bash
cd Atom-agent
PYTHONPATH=. .venv/bin/python tests/integration/test_grpc_connection.py
```

Salida observada:

```text
[server] AtomAgentService started on insecure port 33451
[ExecuteCommand] success=True out_message='Agent acknowledged command: open camera'
[StreamChat] token='Echo: Hello Atom, can you help me?' status='success' finished=True

RESULT: PASS
```

El mensaje usado en `StreamChat` (`"Hello Atom, can you help me?"`) es exactamente el prompt que envía el botón del micrófono de la app, por lo que la ruta validada aquí coincide con la prueba manual en el dispositivo. La suite existente del backend (`pytest -q`) también permanece en verde: **11 aprobadas**.

---
> **Direct Access:**
> [Prueba de conexión](../../../../../../Atom-agent/tests/integration/test_grpc_connection.py)
> [Servicer gRPC](../../../../../../Atom-agent/infrastructure/grpc/server.py)
> [Contrato proto](../../../../../src/main/proto/ai.proto)

## 4. Brechas conocidas / trabajo futuro:

| Área | Estado actual | Trabajo futuro |
|:-----|:--------------|:---------------|
| **IA real (`StreamChat`)** | Probado con un `chat_use_case` falso. | Ejecutar contra la ruta real LangGraph/NVIDIA Gemma; requiere `NVIDIA_API_KEY` y un Qdrant accesible. |
| **`Transcribe` / `Synthesize`** | Definidos en el contrato; aún sin prueba de conexión. | Añadir pruebas de conexión de TTS (streaming) y STT (unario) cuando Faster Whisper / Kokoro estén cableados. |
| **`ExecuteCommand`** | Devuelve una confirmación fija. | Implementar la ejecución real de comandos en el agente. |
| **Seguridad del transporte** | `usePlaintext()` / `insecure_port` — solo desarrollo. | Añadir TLS antes de cualquier despliegue no local. |
| **Auth/identidad** | La app envía un `user_id` aleatorio por sesión (ver ADR-001). | Cablear la identidad real de usuario cuando llegue la autenticación. |
| **Ruta de micrófono real** | El botón del micrófono envía un prompt fijo. | Capturar audio (`RECORD_AUDIO`) y hacer ida y vuelta por `Transcribe`. |

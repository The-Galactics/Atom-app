# Technical Documentation: gRPC Connection Test (Backend) [ATOM].

> **Author:** Atom Team.
> **Content:** End-to-end gRPC connection test against the Python agent.
> **Status:** 🟢 Verified (with fakes) · 🟡 Live AI path pending API keys.

---

This document describes how the gRPC connection between the Android client and the Python AI agent (`Atom-agent`) was tested end to end, the evidence obtained, and the known gaps that remain as future work. It complements the Android-side unit-test document (`ATOM-30-gRPC-gherkin.md`), which validates the adapter in isolation; here the focus is the **real server** answering over a **real socket**.

## 1. Contract alignment (no drift):

The connection only works if both ends share the same proto contract. The two files were compared field by field:

- **App side:** `Atom_app/src/main/proto/ai.proto`
- **Backend side (source of truth):** `Atom-agent/proto/atom_agent.proto`

Both declare `package com.atom.proto`, `java_package com.atom.infrastructure.adapter.grpc`, `java_outer_classname AiProto`, the service `AtomAgentService`, the same four RPCs (`ExecuteCommand`, `StreamChat`, `Transcribe`, `Synthesize`), and identical message fields and tag numbers. **No drift was found.** The only differences are explanatory comments on the app side.

## 2. Test architecture:

To exercise the connection without standing up external providers (NVIDIA Gemma, Qdrant, Kokoro), the **real** servicer is started against a stub container:

- **Real servicer:** `AtomGrpcService` from `Atom-agent/infrastructure/grpc/server.py` — the exact class wired into the running app.
- **Stub container:** provides a fake `chat_use_case` whose `execute()` returns a canned answer, so `StreamChat` runs without an API key.
- **Real socket:** `grpc.aio.server()` with `add_insecure_port("[::]:0")` and a `grpc.aio.insecure_channel(...)` — mirroring the Android client's plaintext (`usePlaintext()`) dev transport.
- **Generated stubs:** the client calls go through `AtomAgentServiceStub`, the same generated code the app relies on.

### 2.1 Methods under test:

**ExecuteCommand (unary):** Pure placeholder on the server (no external dependency); always acknowledges the command. Validates the unary request/response round-trip and serialization of `CommandRequest` / `CommandResponse`.

**StreamChat (server-streaming):** Drives the real server method with a fake `chat_use_case`. Validates the streaming pipeline (`MessageRequest` in, `stream MessageResponse` out) and that `script_token` / `status` / `finished` are populated correctly.

## 3. Test execution and evidence:

The test lives in the backend repo and runs as a standalone `asyncio` runner (the project does not yet depend on `pytest-asyncio`):

```bash
cd Atom-agent
PYTHONPATH=. .venv/bin/python tests/integration/test_grpc_connection.py
```

Observed output:

```text
[server] AtomAgentService started on insecure port 33451
[ExecuteCommand] success=True out_message='Agent acknowledged command: open camera'
[StreamChat] token='Echo: Hello Atom, can you help me?' status='success' finished=True

RESULT: PASS
```

The `StreamChat` message used (`"Hello Atom, can you help me?"`) is the exact prompt the app's mic button sends, so the path validated here matches the manual smoke test on the device. The existing backend suite (`pytest -q`) also stays green: **11 passed**.

---
> **Direct Access:**
> [Connection test](../../../../../../Atom-agent/tests/integration/test_grpc_connection.py)
> [gRPC servicer](../../../../../../Atom-agent/infrastructure/grpc/server.py)
> [Proto contract](../../../../../src/main/proto/ai.proto)

## 4. Known gaps / future work:

| Area | Current state | Future work |
|:-----|:--------------|:------------|
| **Live AI (`StreamChat`)** | Tested with a fake `chat_use_case`. | Run against the real LangGraph/NVIDIA Gemma path; requires `NVIDIA_API_KEY` and a reachable Qdrant. |
| **`Transcribe` / `Synthesize`** | Defined in the contract; not yet covered by a connection test. | Add streaming TTS and unary STT connection tests once Faster Whisper / Kokoro are wired. |
| **`ExecuteCommand`** | Returns a hardcoded acknowledgement. | Implement real command execution on the agent. |
| **Transport security** | `usePlaintext()` / `insecure_port` — dev only. | Add TLS before any non-local deployment. |
| **Auth/identity** | App sends a random per-session `user_id` (see ADR-001). | Wire real user identity once auth lands. |
| **Real microphone path** | App mic button sends a fixed prompt. | Capture audio (`RECORD_AUDIO`) and round-trip through `Transcribe`. |

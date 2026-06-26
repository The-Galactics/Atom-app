# Technical Stack Documentation - []Atom]:

This document details the software architecture, implemented technologies, and technical justification of the tools that make up the **Atom** ecosystem, ensuring compliance with performance goals (inferences under 4 seconds), accessibility, scalability, and security.

---

## 1. Development Environment and Methodology
* **Operating System:** Linux.
  * *Benefit:* Provides a highly stable and native development environment for executing Bash scripts, optimizing system resource management during concurrent compilation of multiple microservices and databases.
* **Integrated Development Environment (IDE):** IntelliJ IDEA 2026.
  * *Benefit:* Advanced real-time support for Java 21 features, facilitating deep debugging of the Android client (hexagonal core with manual dependency injection via `AppContainer`) and comprehensive project management.
* **Management Tools:** Jira, Git, and Docusaurus.
  * *Benefit:* Jira centralizes Backlog management and Scrum boards, Git with strict branch nomenclature (`feature/*`) ensures version control, and Docusaurus maintains minimalist and easily navigable documentation.

---

## 2. Frontend and UI (Native Mobile)
* **Platform and Language:** Android SDK (minimum Android 8.0 Oreo / API 26, compiled against and targeting Android 15 / API 35) using Java 21.
  * *Benefit:* Native development eliminates the latency of cross-platform frameworks, providing direct access to operating system accessibility services and allowing granular control of hardware (microphone, touch event injection).
* **UI and Reactivity:** XML layouts with Material Design 3 (Material Components), and the ViewModel and LiveData Android Architecture Components.
  * *Benefit:* Implementation of an absolute dark mode (Pure Black `#0A0A0C` optimized for OLED screens) that reduces battery consumption and cognitive load. ViewModel and LiveData manage UI state and real-time asynchronous voice streams, exposing ephemeral transcriptions to the view reactively and in a lifecycle-aware way.
* **Typography and Animation:** Hand-drawn custom Android Views (Canvas), Geist Serif (Titles), and Lora (Body).
  * *Benefit:* A custom View (`AtomCoreView`) animates the interface's "Core/Atom" with total fluidity (60 fps) using Canvas drawing and radial/sweep gradients, without penalizing the processor or requiring external dependencies. The typography combines an elegant technical profile with high legibility for continuous reading.

---

## 3. Backend and Orchestration Logic
* **Framework:** Python 3.12 with FastAPI and Uvicorn.
  * *Benefit:* FastAPI + Uvicorn expose the AI service asynchronously and with high performance, serving as the foundation of the agent that communicates with Large Language Models (LLMs) and manages memory, tools, and conversation state.
* **Transport:** gRPC (proto3) on port 50051.
  * *Benefit:* The Android client communicates with the backend via gRPC (`ai.proto`, `AtomAgentService`), a low-latency binary transport with streaming, ideal for a real-time assistant without exhausting resources.
* **AI Orchestration:** LangChain and LangGraph (multi-agent system).
  * *Benefit:* LangGraph coordinates a graph of specialized agents that classify intents, plan, and execute tasks, robustly managing state and tools.
* **Architectural Design:** Hexagonal Architecture and semantic skill memory.
  * *Benefit:* Decoupling (domain/application/adapters/infrastructure/ports/api) ensures that business logic (the core) remains isolated. The engine that allows Atom to "learn" is the semantic memory in Qdrant (`skills` collection): generated skills and flows are stored as vectors and retrieved by similarity, so LangGraph agents reuse previous solutions without retraining or restarting the service.

---

## 4. Data Persistence
* **Document Database (NoSQL):** MongoDB (async *motor* driver).
  * *Benefit:* Flexible and scalable storage for user profiles and personalization parameters (nicknames, personality). Credentials are protected with Argon2id hashing and authentication uses JWT RS256 (with Google OIDC and Redis for sessions).
* **Vector Database:** Qdrant.
  * *Benefit:* Fundamental for AI. It stores mathematical representations (vectors) of existing workflows and skills (`memory` and `skills` collections). When a user gives a command, Qdrant retrieves the exact function by semantic similarity without having to rewrite code.

---

## 5. AI Architecture (Google Gemini)
The AI core relies on **Google Gemini** models, integrated via `langchain-google-genai`, which offer multimodal capabilities and low latency without requiring proprietary inference infrastructure.

* **Orchestrator AI, LLM, and Vision: Google Gemini 3.1 Flash Lite**
  * **Role:** The main multimodal brain (`models/gemini-3.1-flash-lite`) that processes input, classifies intents, analyzes the interface (Vision), plans the sequence of clicks/gestures, and coordinates the LangGraph agents. Embeddings are generated with `models/gemini-embedding-2` and web grounding is performed with native Google Search.
  * **Justification:** Its multimodal nature and low latency allow it to understand the system's visual structure and program a dynamic solution with high precision, meeting the sub-4-second response standard.
* **Speech-to-Text (STT): faster-whisper**
  * **Role:** Transcribes the audio stream captured by the Android device into text.
  * **Implementation:** `small` model running on CPU with int8 quantization.
    * *How it works:* The audio stream is sent to the backend, where faster-whisper performs transcription efficiently without relying on GPU or third-party services, feeding the orchestrator clean text.
  * **Justification:** Enables backend-local voice recognition that is low-cost and privacy-controlled, contributing to the sub-4-second response goal.

---

## 6. Text-to-Speech (TTS) Integration for Efficient UX
The architecture employs a **hybrid TTS system** for natural auditory feedback:

* **Main Option: Kokoro (self-hosted TTS)**
  * **Analysis:** Self-hosted CPU voice synthesis service (`kokoro-fastapi-cpu`), exposed by the backend through the `/v1/audio/speech` endpoint with the `af_heart` voice.
  * **UX Benefit:** Natural voice with no per-character costs or dependency on external providers, keeping full control of the data.
* **Fallback Option (Ultra-Fast): Android Native TTS (`android.speech.tts`)**
  * **Analysis:** Native integration at the SDK level of the Android client (`AndroidTextToSpeech`), 100% free and offline.
  * **UX Benefit:** Response latency under 100ms for quick confirmations, prioritizing task execution if the connection is unstable.

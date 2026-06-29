# User Story Backlog [Atom]:

This document aims to present our user story repository and how story points will be classified to motivate developers every day.

Story points will be assigned to each US using a measurement based on the Fibonacci sequence (1, 2, 3, 5, 8), where 1 is a very simple task and 8 is a task with significantly higher complexity.

> The **ID** column corresponds to the real task key on Atom's Jira board (ATOM-XX), so every story is directly traceable from this documentation.

## **Sprint 1: Foundation and Connectivity:**

**Objective:** Establish the base infrastructure, the Java-Python bridge, and initial voice/text capture.

| ID | Component | User Story (US) | Story Points (SP) |
| :--- | :--- | :--- | :---: |
| **ATOM-24** | FRONTEND | Landing Page layout with Atom's visual identity. | 3 |
| **ATOM-25** | MOBILE | Minimalist Android interface (low cognitive load). | 5 |
| **ATOM-28** | JAVA-CORE | Definition of Domain Entities (User, Intent). | 5 |
| **ATOM-29** | JAVA-CORE | Implementation of Input/Output Ports (Interfaces). | 5 |
| **ATOM-30** | INFRA | Python-Java Bridge (gRPC/REST) for service communication. | 8 |
| **ATOM-31** | AI-PYTHON | Base Voice Module (STT & TTS) for transcription and spoken responses. | 8 |
| **ATOM-33** | SECURITY | Password hashing for user credentials. | 3 |
| **ATOM-34** | BACKEND | Global exception handler (GlobalExceptionHandler). | 5 |
| **ATOM-35** | SECURITY | Robust validation and runtime environment check. | 8 |
| **ATOM-47** | SECURITY | Refactor of the global validation and exception-handling structure (subtask). | — |

### User Stories — Sprint 1

#### ATOM-24 · FRONTEND — Landing Page layout
**Story:** As a user interested in the project, I want to see an attractive, modern Landing Page, so that I can learn about Atom's value proposition and visual identity.
**Acceptance criteria:** detailed in the Jira issue (attached document).

#### ATOM-25 · MOBILE — Minimalist Android interface
**Story:** As a mobile app user, I want a clean, uncluttered interface with few on-screen elements, so that my experience is intuitive and free of unnecessary cognitive load.
**Acceptance criteria:** detailed in the Jira issue (attached document).

#### ATOM-28 · JAVA-CORE — Domain Entities
**Story:** As a backend developer, I want to define the pure domain entities (User and Intent), so that Atom's business rules remain isolated from frameworks and databases.
**Acceptance criteria:** detailed in the Jira issue (attached document).

#### ATOM-29 · JAVA-CORE — Ports (Interfaces)
**Story:** As a backend developer, I want to create the interfaces for the input ports (use cases) and output ports (repositories/services), so that the Core defines the contracts for communicating with the outside world.
**Acceptance criteria:** detailed in the Jira issue (attached document).

#### ATOM-30 · INFRA — Python-Java Bridge
**Story:** As an infrastructure developer, I want to configure a REST endpoint or a basic gRPC channel between Java and Python, so that both environments can exchange data from the start of the project.
**Acceptance criteria:** detailed in the Jira issue (attached document).

#### ATOM-31 · AI-PYTHON — Base Voice Module (STT & TTS)
**Story:** As an AI developer, I want to set up a base Python script that integrates Speech-to-Text (STT) and Text-to-Speech (TTS) modules using local services or external APIs (such as Nvidia Riva/Whisper), so that the system can both transcribe the user's voice and generate spoken responses.
**Acceptance criteria:**
- *STT:* Given a short test audio file (.wav or .mp3), when it is processed by the STT module, then it must return a string with the exact or highly accurate transcription.
- *TTS:* Given a test text string, when it is processed by the TTS module, then it must generate and save a playable audio file with the natural reading of that text.

#### ATOM-33 · SECURITY — Password hashing
**Story:** As Atom's security lead, I want to implement a one-way hashing mechanism (using BCrypt or Argon2) with salt in an isolated way, so that user credentials are processed securely and no plaintext passwords exist in any layer of the system.
**Acceptance criteria:** detailed in the Jira issue (attached document).

#### ATOM-34 · BACKEND — Global exception handler
**Story:** As a project developer, I want to implement a global handler for uncaught exceptions at the application level, so that the app does not crash unexpectedly on unforeseen errors and the integrity of the banking flow on third-party devices is protected.
**Acceptance criteria:** detailed in the Jira issue (attached document).

#### ATOM-35 · SECURITY — Robust validation and environment check
**Story:** As Atom's security administrator, I want to establish a strict system of syntactic validations and environment control, so that the risks of fraud and vulnerabilities are mitigated given that we have no physical control over the user's device.
**Acceptance criteria:** detailed in the Jira issue (attached document).

#### ATOM-47 · SECURITY — Validation & exception refactor (subtask)
**Story:** As a developer, I want to centralize validation components and exception handling into a unified package structure, so that the codebase is more maintainable, consistent, and easier to extend.
**Acceptance criteria:**
- Validation-related classes are consolidated into a common package structure.
- Custom exceptions are centralized in a single package.
- A unified global exception handler is used across the application.
- Duplicate or redundant validation and exception classes are removed.
- All affected imports and references are updated accordingly.
- Existing application behavior and error responses remain unchanged.
- The application builds successfully and all existing tests continue to pass.

## **Sprint 2: Functionality and Connections:**

**Objective:** Connect the services (Android ↔ Spring ↔ Python AI), enable the vector database, and the action inference flow.

| ID | Component | User Story (US) | Story Points (SP) |
| :--- | :--- | :--- | :---: |
| **ATOM-38** | ANDROID | Base Accessibility Service for gesture injection. | 5 |
| **ATOM-39** | API-SPRING | Hexagonal architecture: adapters for mobile clients. | 3 |
| **ATOM-40** | API-SPRING | Integration with the vector database. | 5 |
| **ATOM-41** | API-SPRING | Client orchestrator: Spring Boot ↔ Python AI connection. | 5 |
| **ATOM-42** | AI-PYTHON | Embeddings and vector connectivity module. | 8 |
| **ATOM-43** | JAVA-SPRING | Exposure of the STT/TTS module through a local API. | 5 |
| **ATOM-44** | CORE | Action inference flow (LLM + screen context). | 8 |
| **ATOM-45** | INFRASTRUCTURE | Vector database initialization and configuration. | 5 |
| **ATOM-46** | BACKEND | External API integration and fallback orchestrator. | 8 |

### User Stories — Sprint 2

#### ATOM-38 · ANDROID — Base Accessibility Service
**Story:** As an Android developer, I want to register and configure an AccessibilityService in the app's manifest, so that I can obtain operating system permissions to simulate taps, scrolls, and screen readings from third-party applications (like TikTok).
**Acceptance criteria:** Given that the user grants the Accessibility permission in the phone settings, when the app receives a test order, then the service must be able to programmatically scroll or interact with an active screen element.

#### ATOM-39 · API-SPRING — Hexagonal architecture (mobile adapters)
**Story:** As a Spring Boot developer, I want to create the Hexagonal Architecture package structure for the REST controllers (Driving Adapters) that will receive requests from the mobile app (audios, commands, and screen state).
**Acceptance criteria:** Given the Spring Boot project with Maven, when the input port interfaces are generated, then the business logic (Core/Domain) must be completely isolated from the Spring web layer.

#### ATOM-40 · API-SPRING — Integration with the vector DB
**Story:** As a Spring Boot developer, I want to configure the connector and infrastructure for the Vector Database, so that I can persist and query the numerical representations (embeddings) of the user context and the mapped actions.
**Acceptance criteria:** Given an active Vector DB instance, when Spring Boot runs initialization, then it must establish the connection correctly and allow the creation of collections/indexes to store vectors.

#### ATOM-41 · API-SPRING — Client orchestrator (Spring ↔ Python AI)
**Story:** As a Spring Boot developer, I want to create the Driven Adapter to connect to the Python AI module using HTTP (FastAPI) or gRPC requests, delegating speech processing (STT/TTS) and inference.
**Acceptance criteria:** Given a flow that requires AI, when Spring Boot sends the payload to the Python microservice, then it must receive the structured response (transcript or action to execute) without inefficiently blocking the main thread.

#### ATOM-42 · AI-PYTHON — Embeddings module
**Story:** As an AI developer, I want to develop a Python script that takes text strings (user commands or texts extracted from the screen) and transforms them into high-dimensional vectors using a language model (Embedding Model), so that I can interact with the Vector DB.
**Acceptance criteria:** Given a text sent to the AI module, when the model processes it, then it must return an array of floats (the vector) ready to be indexed or compared by cosine similarity.

#### ATOM-43 · JAVA-SPRING — Exposing STT/TTS via local API
**Story:** As a developer, I want to wrap the base voice scripts (US-06 STT and TTS) in a lightweight web framework (like FastAPI) to expose them as local endpoints that can be cleanly consumed by Spring Boot.
**Acceptance criteria:** Given the FastAPI server running, when it receives a request at /stt with an audio file, then it must return the JSON with the transcribed text with the lowest possible latency.

#### ATOM-44 · CORE — Action inference flow
**Story:** As a component of the operating system on the mobile device, capturing the node tree of the active screen is required only at the exact moment a command or action request is detected, so that excessive consumption of battery, processing, and memory in the background is avoided.
**Acceptance criteria:**
- Given that the app has the necessary accessibility permissions granted, when activation is detected by a command or input event, then the accessibility service must perform a single capture of the current state of the interface (node tree / UI hierarchy).
- Once the capture has been taken, when the node tree is serialized, then the service must immediately stop scanning the screen and package the data for sending to the backend, ensuring the capture stream does not remain open in a loop.

#### ATOM-45 · INFRASTRUCTURE — Vector database initialization
**Story:** As the system, building and configuring a vector database instance is required, so that it provides an indexed persistence mechanism to store and query the embeddings generated from the screen context and the commands received.
**Acceptance criteria:**
- Given the project's infrastructure environment, when the vector database container or service is deployed (e.g. Qdrant, Milvus, or PGVector), then the service must be active and accessible on the configured port.
- Given the active vector database service, when the initialization script runs, then the initial collections or indexes must be created with the specific dimension required by the embedding model.

#### ATOM-46 · BACKEND — External API integration and fallback
**Story:** As the backend service layer, a centralizing component is required to manage requests to the different external APIs (LLMs, voice providers, or cloud services), implementing retry policies and alternative responses (fallback), so that the assistant does not hang if an external API fails or causes latency.
**Acceptance criteria:** Given that the backend makes a request to an external API (e.g. the LLM), when that API returns an error (500, Rate Limit, or Timeout), then the system must retry the request in a controlled manner or execute a default action (fallback), notifying the client cleanly without breaking the app.

## **Sprint 3: Polishing, Refining, and Quality:**

**Objective:** Refine the experience: real-time screen reading, gesture automation, private local persistence, and voice/memory optimization.

| ID | Component | User Story (US) | Story Points (SP) |
| :--- | :--- | :--- | :---: |
| **ATOM-48** | ANDROID | Real-time screen reading via accessibility. | 8 |
| **ATOM-49** | ANDROID | Touch simulation and gesture automation on third-party apps. | 5 |
| **ATOM-51** | ANDROID | Local history and cache persistence optimization for absolute privacy. | 5 |
| **ATOM-52** | AI-BACKEND | Semantic memory optimization and vector search in Qdrant. | 8 |
| **ATOM-55** | ANDROID | Migration and configuration of the new unified voice model for TTS. | 2 |
| **ATOM-56** | ANDROID | Background voice detonator using the "ATOM" keyword. | 5 |

### User Stories — Sprint 3

#### ATOM-48 · ANDROID — Real-time screen reading
**Story:** As an Atom user, I want the application to be able to read and understand what's on my screen, regardless of which app I have open, so that the assistant knows exactly what context I'm in and can help me without my having to visually explain what I see.
**Acceptance criteria:**
- Create and register the ScreenReaderAccessibilityService in the manifest with the required permissions.
- Asynchronously capture the text and content descriptions of the active view (using Virtual Threads to avoid blocking the UI thread).
- Send the structured text from the screen to the application port to provide context for the inference flow.
- Automatically stop screen scanning when the overlay is minimized to optimize battery life.

#### ATOM-49 · ANDROID — Gesture automation on third-party apps
**Story:** As an Atom user, I want the assistant to be able to press buttons, scroll, and navigate within my third-party apps for me, so that it can perform complex tasks and workflows on my phone in a fully automated way simply by asking it with my voice.
**Acceptance criteria:**
- Implement the ability to search for specific nodes by text or resource ID within the foreground app.
- Use the Android dispatchGesture() API to simulate precise clicks on the interface.
- Map the responses from the inference use case (Order-to-Action) to executable physical commands (e.g., "Click the Send button").
- Handle exceptions if the third-party app changes state unexpectedly during automation.

#### ATOM-51 · ANDROID — Private local history & persistence
**Story:** As an Atom user, I want my chat history to be securely stored exclusively in my device's local memory, so that maximum privacy of my personal data is ensured and my conversations are not recorded on external servers.
**Acceptance criteria:**
- Design the local storage structure using an integrated SQLite database or a native Android encrypted caching mechanism.
- Develop the local persistence infrastructure adapter in the Android layer that connects to the application's output port.
- Ensure the use case consumes this local adapter to save and retrieve messages chronologically using the session identifier.
- Validate through unit tests that sensitive chat data is destroyed from the local cache if the user clears the session or the overlay is securely closed.

#### ATOM-52 · AI-BACKEND — Semantic memory & vector search
**Story:** As an Atom user, I want the assistant to instantly associate my voice commands with the correct actions it should take on the screen, so that its responses and executions are ultra-fast, accurate, and tailored to my specific needs.
**Acceptance criteria:**
- Configure the collection in Qdrant using the cosine distance metric aligned with the AI model vectors.
- Develop or refine the infrastructure adapter to expose optimized semantic search and upsert methods.
- Inject the results returned by the Qdrant adapter directly into the ActionInferenceFlow use case to accelerate the agent's decision-making.

#### ATOM-55 · ANDROID — New unified voice model (TTS)
**Story:** As an Atom user, I want the assistant to respond to me using a much more natural, clear, and professional tone of voice agreed upon by the team, so that the voice interaction is pleasant and doesn't feel like a generic or flat robot.
**Acceptance criteria:**
- Configure the initialization parameters of the native Android TTS engine within the voice infrastructure adapter.
- Adjust the pitch and speech rate properties in the voice control use case according to the standards defined by the team.
- Implement a fallback mechanism that selects a high-quality native Spanish voice if the specific model is not downloaded on the device.
- Validate the correct synchronization of the audio output stream concurrently with the virtual threads to avoid interruptions in long responses.

#### ATOM-56 · ANDROID — Background "ATOM" voice detonator
**Story:** As an Atom user, I want the application to automatically activate and open its floating interface whenever I say the word "ATOM," so that I can use the assistant completely hands-free without having to touch the screen or manually open the app.
**Acceptance criteria:**
- Configure a persistent background service using native recognition to passively listen to the microphone's audio stream.
- Implement the logic in the speech infrastructure adapter to process the audio locally and detect the exact match of the keyword "ATOM."
- Connect successful detection to the activation use case, which should immediately trigger the FloatingBubbleService.
- Optimize the passive listening algorithm using Virtual Threads for audio processing, ensuring background battery consumption is kept to a minimum according to the project's efficiency standards.

## **Sprint 4: Refinement, QA, Testing, and Deployment:**

**Objective:** Stabilize the MVP: device security and integrity, authentication, multi-OEM compatibility, stress testing, and Play Store release.

| ID | Component | User Story (US) | Story Points (SP) |
| :--- | :--- | :--- | :---: |
| **ATOM-36** | LANDING | Implementation of sections and download links. | 2 |
| **ATOM-50** | SECURITY | Device integrity validation system and anti-fraud security. | 8 |
| **ATOM-53** | UI/UX | Aesthetic optimization and radical minimalism of the overlay interface. | 5 |
| **ATOM-54** | SECURITY | Secure authentication via email and profile persistence in MongoDB. | 5 |
| **ATOM-57** | ANDROID | Agent adaptation for Android variants (HyperOS, Oppo, Vivo, etc.). | 8 |
| **ATOM-58** | OPTIMIZATION | Stress testing and AI process flow optimization. | 5 |
| **ATOM-59** | DEPLOYMENT | Google Play Console configuration and beta launch preparation. | 5 |
| **ATOM-60** | DOCUMENTATION | Technical documentation of detected bugs and MVP stabilization manual. | 3 |
| **ATOM-61** | BACKEND | Error sanitization: preventing native exception leakage (gRPC + HTTP) (subtask). | — |

### User Stories — Sprint 4

#### ATOM-36 · LANDING — Sections and download links
**Story:** As Thomas (end user), I want to lay out the interactive landing page in TypeScript with the assistant's information and APK download buttons, so that users can learn about the app and install it.
**Acceptance criteria:** Given the previous design of the landing, when the user browses the web, then they should be able to view the assistant's features and click the download button, initiating the download of the APK (temporarily stored on the server or storage).

#### ATOM-50 · SECURITY — Device integrity validation
**Story:** As an Atom user, I want to be certain that my device has a secure, malware-free runtime environment, so that my sensitive data displayed on the screen and my automations are not compromised or intercepted by third parties.
**Acceptance criteria:**
- Implement production logic in the DeviceInspectorAdapter to detect `su` binaries and superuser applications.
- Implement hardware system property verification to identify common emulators (ro.kernel.qemu, ro.product.model).
- Connect the result to the initialization use case: if the environment is detected as unsafe, the FloatingBubbleService must be destroyed immediately and securely.
- Develop unit tests with Mockito to simulate and validate responses in both secure and unsafe environments.

#### ATOM-53 · UI/UX — Radical minimalism of the overlay
**Story:** As an Atom user, I want an extremely clean, dark, and uncluttered interface in the floating bubble, so that it doesn't obstruct my view while using other applications and consumes as little battery power as possible on my device.
**Acceptance criteria:**
- Modify the FloatingBubbleService XML layouts to strictly apply the color #0A0A0C (pure black), optimizing power consumption on OLED screens.
- Remove redundant visual components and simplify transitions using only essential animations optimized in Lottie.
- Run tests with the Android Profiler to ensure the overlay does not generate CPU spikes or keep active wakelocks in the background.

#### ATOM-54 · SECURITY — Email authentication & MongoDB profiles
**Story:** As an Atom user, I want to be able to securely log in using my email address within the application, so that I feel confident my account is private and can securely sync my custom settings to the server.
**Acceptance criteria:**
- Implement the authentication use case to validate email credentials and encrypted passwords.
- Develop the infrastructure adapter in the backend to store and query user profiles in a new, dedicated MongoDB collection.
- Configure the secure transfer of authentication data over the existing gRPC communication channel.
- Implement robust hashing in the backend to securely store passwords without plaintext.

#### ATOM-57 · ANDROID — Adaptation to Android variants (OEM)
**Story:** As a user of a Xiaomi, Oppo, or Vivo device, I want Atom to run its background services and floating bubble without the operating system unexpectedly closing or blocking them, so that I can use the assistant normally, regardless of my phone's custom UI layer.
**Acceptance criteria:** detailed in the Jira issue.

#### ATOM-58 · OPTIMIZATION — Stress testing and AI flow
**Story:** As an Atom user, I want my voice commands to be processed and my screen read without lag or phone overheating, so that I can enjoy a smooth automation workflow that responds quickly during continuous use.
**Acceptance criteria:** detailed in the Jira issue.

#### ATOM-59 · DEPLOYMENT — Google Play Console & beta launch
**Story:** As a community end-user, I want to be able to securely download the Atom app directly from the Google Play Store, so that I can easily install MVP updates and trust that the app meets store standards.
**Acceptance criteria:** detailed in the Jira issue.

#### ATOM-60 · DOCUMENTATION — Bugs & MVP stabilization manual
**Story:** As a user (developer or tester on the Atom team), I want a clear record of the bugs found in the MVP alongside technical documentation of the current architecture, so that I can quickly fix issues during the beta phase and ensure the app remains stable in production.
**Acceptance criteria:** detailed in the Jira issue.

#### ATOM-61 · BACKEND — Error sanitization (subtask)
**Story:** As a developer, I want to sanitize errors to prevent native exceptions from leaking to the client (gRPC + HTTP), so that no internal system details are exposed in error responses.
**Acceptance criteria:** pending documentation in the Jira issue.

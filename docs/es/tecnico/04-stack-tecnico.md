# Documentación del Stack Técnico - []Atom]:

Este documento detalla la arquitectura de software, las tecnologías implementadas y la justificación técnica de las herramientas que componen el ecosistema de **Atom**, garantizando el cumplimiento de los objetivos de rendimiento (inferencias menores a 4 segundos), accesibilidad, escalabilidad y seguridad.

---

## 1. Entorno de Desarrollo y Metodología
* **Sistema Operativo:** Linux.
  * *Beneficio:* Proporciona un entorno de desarrollo altamente estable y nativo para la ejecución de scripts Bash, optimizando la gestión de recursos del sistema durante la compilación concurrente de múltiples microservicios y bases de datos.
* **Entorno de Desarrollo Integrado (IDE):** IntelliJ IDEA 2026.
  * *Beneficio:* Soporte avanzado y en tiempo real para las características de Java 21, facilitando la depuración profunda del cliente Android (núcleo hexagonal con inyección de dependencias manual mediante `AppContainer`) y la gestión integral del proyecto.
* **Herramientas de Gestión:** Jira, Git y Docusaurus.
  * *Beneficio:* Jira centraliza la administración del *Backlog* y los tableros *Scrum*, Git con nomenclatura estricta en ramas (`feature/*`) asegura el control de versiones, y Docusaurus permite mantener una documentación minimalista y fácilmente navegable.

---

## 2. Frontend y UI (Móvil Nativo)
* **Plataforma y Lenguaje:** Android SDK (mínimo Android 8.0 Oreo / API 26, compilado y dirigido a Android 15 / API 35) utilizando Java 21.
  * *Beneficio:* El desarrollo nativo elimina la latencia de los frameworks multiplataforma, brindando acceso directo a los servicios de accesibilidad del sistema operativo y permitiendo un control granular del hardware (micrófono, inyección de eventos táctiles).
* **UI y Reactividad:** Layouts XML con Material Design 3 (Material Components), y los Android Architecture Components ViewModel y LiveData.
  * *Beneficio:* Implementación de un modo oscuro absoluto (Pure Black `#0A0A0C` optimizado para pantallas OLED) que reduce el consumo de batería y la carga cognitiva. ViewModel y LiveData gestionan el estado de la UI y los flujos de voz asíncronos en tiempo real, exponiendo a la vista transcripciones efímeras de forma reactiva y consciente del ciclo de vida.
* **Tipografía y Animación:** Vistas de Android personalizadas dibujadas a mano (Canvas), Geist Serif (Títulos) y Lora (Cuerpo).
  * *Beneficio:* Una Vista personalizada (`AtomCoreView`) anima el "Núcleo/Átomo" de la interfaz con total fluidez (60 fps) mediante dibujo en Canvas y gradientes radiales/sweep, sin penalizar el procesador ni requerir dependencias externas. La tipografía combina un perfil técnico elegante con alta legibilidad para lecturas continuas.

---

## 3. Backend y Lógica de Orquestación
* **Framework:** Python 3.12 con FastAPI y Uvicorn.
  * *Beneficio:* FastAPI + Uvicorn exponen el servicio de IA de forma asíncrona y de alto rendimiento, sirviendo como base del agente que se comunica con los Modelos de Lenguaje (LLMs) y gestiona memoria, herramientas y estado de la conversación.
* **Transporte:** gRPC (proto3) en el puerto 50051.
  * *Beneficio:* El cliente Android se comunica con el backend mediante gRPC (`ai.proto`, `AtomAgentService`), un transporte binario de baja latencia con streaming, ideal para un asistente que trabaja en tiempo real sin agotar recursos.
* **Orquestación IA:** LangChain y LangGraph (sistema multi-agente).
  * *Beneficio:* LangGraph coordina un grafo de agentes especializados que clasifican intenciones, planifican y ejecutan tareas, gestionando estado y herramientas de forma robusta.
* **Diseño Arquitectónico:** Arquitectura Hexagonal y memoria semántica de habilidades.
  * *Beneficio:* El desacoplamiento (domain/application/adapters/infrastructure/ports/api) garantiza que la lógica de negocio (el núcleo) quede aislada. El motor que permite a Atom "aprender" es la memoria semántica en Qdrant (colección `skills`): las habilidades y flujos generados se almacenan como vectores y se recuperan por similitud, de modo que los agentes LangGraph reutilizan soluciones previas sin reentrenar ni reiniciar el servicio.

---

## 4. Persistencia de Datos
* **Base de Datos de Documentos (NoSQL):** MongoDB (driver asíncrono *motor*).
  * *Beneficio:* Almacenamiento flexible y escalable para los perfiles de usuario y parámetros de personalización (apodos, personalidad). Las credenciales se protegen con hashing Argon2id y la autenticación emplea JWT RS256 (con Google OIDC y Redis para sesiones).
* **Base de Datos Vectorial:** Qdrant.
  * *Beneficio:* Fundamental para la IA. Almacena las representaciones matemáticas (vectores) de los flujos de trabajo y habilidades existentes (colecciones `memory` y `skills`). Cuando el usuario da una orden, Qdrant recupera por similitud semántica la función exacta sin tener que reescribir código.

---

## 5. Arquitectura de IA (Google Gemini)
El núcleo de IA se apoya en los modelos de **Google Gemini**, integrados mediante `langchain-google-genai`, que ofrecen capacidades multimodales y baja latencia sin requerir infraestructura propia de inferencia.

* **IA Orquestadora, LLM y Visión: Google Gemini 3.1 Flash Lite**
  * **Rol:** Es el cerebro principal multimodal (`models/gemini-3.1-flash-lite`) que procesa la entrada, clasifica intenciones, analiza la interfaz (Visión), planifica las secuencias de clics/gestos y coordina los agentes LangGraph. Los embeddings se generan con `models/gemini-embedding-2` y el grounding web se realiza con Google Search nativo.
  * **Justificación:** Su naturaleza multimodal y su baja latencia le permiten comprender la estructura visual del sistema y generar una solución dinámica con alta precisión, cumpliendo con el estándar de respuesta menor a 4 segundos.
* **Reconocimiento de Voz (STT): faster-whisper**
  * **Rol:** Transcribe a texto el flujo de audio capturado por el dispositivo Android.
  * **Implementación:** Modelo `small` ejecutado en CPU con cuantización int8.
    * *Funcionamiento:* El flujo de audio se envía al backend, donde faster-whisper realiza la transcripción de forma eficiente sin depender de GPU ni de servicios de terceros, alimentando al orquestador con texto limpio.
  * **Justificación:** Permite un reconocimiento de voz local al backend, de bajo costo y con privacidad controlada, contribuyendo al objetivo de respuesta menor a 4 segundos.

---

## 6. Integración Text-to-Speech (TTS) para UX Eficiente
La arquitectura emplea un **sistema TTS híbrido** para una retroalimentación auditiva natural:

* **Opción Principal: Kokoro (TTS self-hosted)**
  * **Análisis:** Servicio de síntesis de voz auto-alojado en CPU (`kokoro-fastapi-cpu`), expuesto por el backend a través del endpoint `/v1/audio/speech` con la voz `af_heart`.
  * **Beneficio UX:** Voz natural sin costos por carácter ni dependencia de proveedores externos, manteniendo el control total de los datos.
* **Opción de Respaldo (Fallback / Ultra-Fast): Android Native TTS (`android.speech.tts`)**
  * **Análisis:** Integración nativa a nivel de SDK del cliente Android (`AndroidTextToSpeech`), 100% gratuita y offline.
  * **Beneficio UX:** Latencia de respuesta inferior a 100ms para confirmaciones rápidas, priorizando la ejecución de la tarea si la conexión es inestable.

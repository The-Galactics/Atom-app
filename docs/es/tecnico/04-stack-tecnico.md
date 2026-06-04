
# Documentación del Stack Técnico - []Atom]:

Este documento detalla la arquitectura de software, las tecnologías implementadas y la justificación técnica de las herramientas que componen el ecosistema de **Atom**, garantizando el cumplimiento de los objetivos de rendimiento (inferencias menores a 4 segundos), accesibilidad, escalabilidad y seguridad.

---

## 1. Entorno de Desarrollo y Metodología
* **Sistema Operativo:** Linux.
  * *Beneficio:* Proporciona un entorno de desarrollo altamente estable y nativo para la ejecución de scripts Bash, optimizando la gestión de recursos del sistema durante la compilación concurrente de múltiples microservicios y bases de datos.
* **Entorno de Desarrollo Integrado (IDE):** IntelliJ IDEA 2026.
  * *Beneficio:* Soporte avanzado y en tiempo real para las características de Java 21, facilitando la depuración profunda de hilos virtuales e integraciones con el framework de Spring Boot.
* **Herramientas de Gestión:** Jira, Git y Docusaurus.
  * *Beneficio:* Jira centraliza la administración del *Backlog* y los tableros *Scrum*, Git con nomenclatura estricta en ramas (`feature/*`) asegura el control de versiones, y Docusaurus permite mantener una documentación minimalista y fácilmente navegable.

---

## 2. Frontend y UI (Móvil Nativo)
* **Plataforma y Lenguaje:** Android SDK (Mínimo Android 10) utilizando Java 21.
  * *Beneficio:* El desarrollo nativo elimina la latencia de los frameworks multiplataforma, brindando acceso directo a los servicios de accesibilidad del sistema operativo y permitiendo un control granular del hardware (micrófono, inyección de eventos táctiles).
* **UI y Reactividad:** XML con Material Design 3, View Binding y RxJava 3.
  * *Beneficio:* Implementación de un modo oscuro absoluto (Pure Black `#0A0A0C` optimizado para pantallas OLED) que reduce el consumo de batería y la carga cognitiva. RxJava 3 maneja eficientemente los flujos de voz asíncronos en tiempo real, conectándose con LiveData para transcripciones efímeras.
* **Tipografía y Animación:** Lottie for Android, Geist Serif (Títulos) y Lora (Cuerpo).
  * *Beneficio:* Lottie permite animar el "Núcleo/Átomo" de la interfaz con total fluidez (60 fps) mediante archivos JSON ultraligeros, sin penalizar el procesador. La tipografía combina un perfil técnico elegante con alta legibilidad para lecturas continuas.

---

## 3. Backend y Lógica de Orquestación
* **Framework:** Spring Boot acoplado con LangChain4.
  * *Beneficio:* LangChain4 actúa como el orquestador perfecto entre el código empresarial en Java y la comunicación directa con los Modelos de Lenguaje (LLMs), gestionando memoria, herramientas y estado de la conversación.
* **Concurrencia:** Hilos Virtuales (Project Loom - Java 21).
  * *Beneficio:* Extremadamente ligeros. Permiten que la aplicación mantenga miles de procesos paralelos de espera (ej. solicitudes a la API de IA) sin agotar la RAM, un factor crítico para un asistente que trabaja en tiempo real.
* **Diseño Arquitectónico:** Arquitectura Hexagonal y Dynamic Class Loading.
  * *Beneficio:* El desacoplamiento garantiza que la lógica de negocio (el núcleo) quede aislada. El *Dynamic Class Loading* es el motor que permite a Atom "aprender": la aplicación compila, carga y ejecuta nuevas habilidades (`.class`) generadas por la IA sin necesidad de reiniciar la app, persistiendo el código final localmente para total privacidad.
* **Bridge de Procesamiento:** Python 3.13.
  * *Beneficio:* Actúa como un puente especializado para los algoritmos iniciales de procesamiento y clasificación, enrutando los datos limpios hacia el servidor Java.

---

## 4. Persistencia de Datos
* **Base de Datos Relacional (SQL):** PostgreSQL / Supabase.
  * *Beneficio:* Almacenamiento seguro, transaccional y robusto para los perfiles de usuario y parámetros de personalización (apodos, personalidad). Las consultas optimizadas aseguran respuestas en menos de 100ms, y los datos se protegen con cifrado AES-256.
* **Base de Datos Vectorial:** ChromaDB.
  * *Beneficio:* Fundamental para la IA. Almacena las representaciones matemáticas (vectores) de los flujos de trabajo y métodos existentes. Cuando el usuario da una orden, ChromaDB recupera por similitud semántica la función exacta sin tener que reescribir código.

---

## 5. Arquitectura de IA (NVIDIA NIM Capa Gratuita)
El uso de la infraestructura **NVIDIA NIM** (NVIDIA Inference Microservices) permite acceder a modelos optimizados mediante TensorRT-LLM sin costos iniciales, ofreciendo endpoints compatibles y una latencia increíblemente baja.

* **IA Orquestadora y STT Nativo: Gemma 4**
  * **Rol:** Es el cerebro principal multimodal que procesa la entrada de audio, clasifica intenciones y coordina los agentes.
  * **Implementación de STT:** Se aprovecha la capacidad nativa de Gemma 4 para procesar tokens de audio directamente a través del endpoint de NVIDIA NIM. 
    * *Funcionamiento:* El flujo de audio capturado por el dispositivo Android se envía vía streaming al modelo. Gemma 4 realiza la transcripción y el entendimiento semántico en un solo paso (Audio-to-Intent), eliminando la latencia de servicios de terceros y ahorrando tiempo de procesamiento al evitar la conversión intermedia a texto plano.
  * **Justificación:** Esto garantiza que la inferencia comience incluso antes de que el usuario termine de hablar, cumpliendo con el estándar de respuesta menor a 4 segundos.
* **IA Desarrolladora: Qwen (Familia 3.5 / Coder-VL)**
  * **Rol:** Se encarga de la ingeniería inversa, analizando la interfaz (Visión), planificando la secuencia de clics/gestos y generando el código de automatización en tiempo real.
  * **Justificación:** Qwen posee una arquitectura nativa multimodal excepcional. Su entrenamiento intensivo en código y flujos de agentes le permite comprender un DOM o una estructura visual del sistema y programar una solución dinámica con alta precisión.

---

## 6. Integración Text-to-Speech (TTS) para UX Eficiente
La arquitectura emplea un **sistema TTS híbrido** para una retroalimentación auditiva natural:

* **Opción Principal: Google Cloud TTS (Neural2 / WaveNet)**
  * **Análisis:** Capa gratuita mensual de hasta 1 millón de caracteres. Ofrece voces neurales altamente humanas.
  * **Beneficio UX:** Alta calidad percibida para respuestas complejas o informativas.
* **Opción de Respaldo (Fallback / Ultra-Fast): Android Native TTS (`android.speech.tts`)**
  * **Análisis:** Integración nativa a nivel de SDK, 100% gratuita y offline.
  * **Beneficio UX:** Latencia de respuesta inferior a 100ms para confirmaciones rápidas, priorizando la ejecución de la tarea si la conexión es inestable.
* **Alternativa Estética: ElevenLabs (Capa Free)**
  * **Uso:** Limitado a 10,000 caracteres para respuestas de sistema pre-configuradas de alta expresividad ("¡Entendido!", "Tarea completada").

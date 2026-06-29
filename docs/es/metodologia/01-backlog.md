# Backlog historias de usuario [Atom]:

En este documento se busca dar a conocer el repositorio de nuestras historias de usuario y cómo se clasifican los story points para motivar a los desarrolladores cada día.

Los story points serán otorgados a cada HU por medio de una medición basada en la secuencia de Fibonacci (1, 2, 3, 5, 8), siendo el número 1 una tarea más sencilla y el número 8 una tarea con muchísima más complejidad.

> La columna **ID** corresponde a la clave real de la tarea en el tablero de Jira de Atom (ATOM-XX), por lo que cada historia es trazable directamente desde esta documentación.

## **Sprint 1: Cimentación y Conectividad:**

**Objetivo:** Establecer la infraestructura base, el bridge Java-Python y la captura inicial de voz/texto.

| ID | Componente | Historia de Usuario (HU) | Story Points (SP) |
| :--- | :--- | :--- | :---: |
| **ATOM-24** | FRONTEND | Maquetado de la Landing Page con la identidad visual de Atom. | 3 |
| **ATOM-25** | MOBILE | Interfaz minimalista de Android (baja carga cognitiva). | 5 |
| **ATOM-28** | JAVA-CORE | Definición de las Entidades de Dominio (Usuario, Intención). | 5 |
| **ATOM-29** | JAVA-CORE | Implementación de los Puertos (Interfaces) de entrada/salida. | 5 |
| **ATOM-30** | INFRA | Bridge Python-Java (gRPC/REST) para la comunicación entre servicios. | 8 |
| **ATOM-31** | IA-PYTHON | Módulo base de voz (STT & TTS) para transcripción y respuestas habladas. | 8 |
| **ATOM-33** | SEGURIDAD | Hashing de contraseñas para credenciales de usuario. | 3 |
| **ATOM-34** | BACKEND | Manejador global de excepciones (GlobalExceptionHandler). | 5 |
| **ATOM-35** | SEGURIDAD | Validación robusta y verificación del entorno de ejecución. | 8 |
| **ATOM-47** | SEGURIDAD | Refactor de la estructura global de validación y manejo de excepciones (subtarea). | — |

### Historias de usuario — Sprint 1

#### ATOM-24 · FRONTEND — Maquetado de la Landing Page
**Historia:** Como usuario interesado en el proyecto, quiero visualizar una Landing Page atractiva y moderna, para conocer la propuesta de valor de Atom y su identidad visual.
**Criterios de aceptación:** detallados en el issue de Jira (documento adjunto).

#### ATOM-25 · MOBILE — Interfaz minimalista de Android
**Historia:** Como usuario de la aplicación móvil, quiero una interfaz limpia, despejada y con pocos elementos en pantalla, para que mi experiencia de uso sea intuitiva y libre de carga cognitiva innecesaria.
**Criterios de aceptación:** detallados en el issue de Jira (documento adjunto).

#### ATOM-28 · JAVA-CORE — Entidades de Dominio
**Historia:** Como desarrollador backend, quiero definir las entidades de dominio puras (Usuario e Intención), para que las reglas de negocio de Atom queden aisladas de frameworks y bases de datos.
**Criterios de aceptación:** detallados en el issue de Jira (documento adjunto).

#### ATOM-29 · JAVA-CORE — Puertos (Interfaces)
**Historia:** Como desarrollador backend, quiero crear las interfaces de los puertos de entrada (casos de uso) y de salida (repositorios/servicios), para que el Core defina los contratos de comunicación con el exterior.
**Criterios de aceptación:** detallados en el issue de Jira (documento adjunto).

#### ATOM-30 · INFRA — Bridge Python-Java
**Historia:** Como desarrollador de infraestructura, quiero configurar un endpoint REST o un canal gRPC básico entre Java y Python, para que ambos entornos puedan intercambiar datos desde el inicio del proyecto.
**Criterios de aceptación:** detallados en el issue de Jira (documento adjunto).

#### ATOM-31 · IA-PYTHON — Módulo base de voz (STT & TTS)
**Historia:** Como desarrollador de IA, quiero montar un script base en Python que integre módulos de Speech-to-Text (STT) y Text-to-Speech (TTS) usando servicios locales o APIs externas (Nvidia Riva/Whisper), para que el sistema pueda transcribir la voz del usuario y generar respuestas habladas.
**Criterios de aceptación:**
- *STT:* Dado un archivo de audio corto (.wav o .mp3), cuando lo procese el módulo STT, entonces debe retornar un string con la transcripción exacta o altamente precisa.
- *TTS:* Dada una cadena de texto, cuando la procese el módulo TTS, entonces debe generar y guardar un archivo de audio reproducible con la lectura natural de dicho texto.

#### ATOM-33 · SEGURIDAD — Hashing de contraseñas
**Historia:** Como encargado de la seguridad de Atom, quiero implementar un mecanismo de hashing unidireccional (BCrypt o Argon2) con sal de forma aislada, para que las credenciales de los usuarios se procesen de forma segura y no existan contraseñas en texto plano en ninguna capa del sistema.
**Criterios de aceptación:** detallados en el issue de Jira (documento adjunto).

#### ATOM-34 · BACKEND — Manejador global de excepciones
**Historia:** Como desarrollador del proyecto, quiero implementar un manejador de excepciones global no capturadas a nivel de aplicación, para que la app no se cierre inesperadamente (crash) ante errores imprevistos y se proteja la integridad del flujo bancario en dispositivos de terceros.
**Criterios de aceptación:** detallados en el issue de Jira (documento adjunto).

#### ATOM-35 · SEGURIDAD — Validación robusta y verificación de entorno
**Historia:** Como administrador de seguridad de Atom, quiero establecer un sistema estricto de validaciones sintácticas y de control de entorno, para mitigar los riesgos de fraude y vulnerabilidades al no tener control físico sobre el dispositivo del usuario.
**Criterios de aceptación:** detallados en el issue de Jira (documento adjunto).

#### ATOM-47 · SEGURIDAD — Refactor de validación y excepciones (subtarea)
**Historia:** Como desarrollador, quiero centralizar los componentes de validación y el manejo de excepciones en una estructura de paquetes unificada, para que el código sea más mantenible, consistente y fácil de extender.
**Criterios de aceptación:**
- Las clases de validación se consolidan en una estructura de paquetes común.
- Las excepciones personalizadas se centralizan en un único paquete.
- Se usa un manejador global de excepciones unificado en toda la aplicación.
- Se eliminan las clases de validación y excepción duplicadas o redundantes.
- Se actualizan todas las importaciones y referencias afectadas.
- El comportamiento y las respuestas de error existentes permanecen sin cambios.
- La aplicación compila correctamente y todas las pruebas existentes siguen pasando.

## **Sprint 2: Funcionalidad y Conexiones:**

**Objetivo:** Conectar los servicios (Android ↔ Spring ↔ IA Python), habilitar la base de datos vectorial y el flujo de inferencia de acciones.

| ID | Componente | Historia de Usuario (HU) | Story Points (SP) |
| :--- | :--- | :--- | :---: |
| **ATOM-38** | ANDROID | Servicio base de accesibilidad para la inyección de gestos. | 5 |
| **ATOM-39** | API-SPRING | Arquitectura hexagonal: adaptadores para clientes móviles. | 3 |
| **ATOM-40** | API-SPRING | Integración con la base de datos vectorial. | 5 |
| **ATOM-41** | API-SPRING | Orquestador de cliente: conexión Spring Boot ↔ IA Python. | 5 |
| **ATOM-42** | IA-PYTHON | Módulo de embeddings y conectividad vectorial. | 8 |
| **ATOM-43** | JAVA-SPRING | Exposición del módulo STT/TTS mediante API local. | 5 |
| **ATOM-44** | CORE | Flujo de inferencia de acciones (LLM + contexto de pantalla). | 8 |
| **ATOM-45** | INFRAESTRUCTURA | Inicialización y configuración de la base de datos vectorial. | 5 |
| **ATOM-46** | BACKEND | Integración de APIs externas y orquestador de fallback. | 8 |

### Historias de usuario — Sprint 2

#### ATOM-38 · ANDROID — Servicio base de accesibilidad
**Historia:** Como desarrollador Android, quiero registrar y configurar un AccessibilityService en el manifiesto de la app, para obtener permisos del sistema operativo y simular toques, scrolls y lecturas de pantalla desde aplicaciones de terceros (como TikTok).
**Criterios de aceptación:** Dado que el usuario concede el permiso de Accesibilidad en los ajustes del teléfono, cuando la app recibe una orden de prueba, entonces el servicio debe poder hacer scroll o interactuar programáticamente con un elemento activo de la pantalla.

#### ATOM-39 · API-SPRING — Arquitectura hexagonal (adaptadores móviles)
**Historia:** Como desarrollador Spring Boot, quiero crear la estructura de paquetes de arquitectura hexagonal para los controladores REST (Driving Adapters) que recibirán las peticiones de la app móvil (audios, comandos y estado de pantalla).
**Criterios de aceptación:** Dado el proyecto Spring Boot con Maven, cuando se generen las interfaces de los puertos de entrada, entonces la lógica de negocio (Core/Dominio) debe quedar completamente aislada de la capa web de Spring.

#### ATOM-40 · API-SPRING — Integración con la BD vectorial
**Historia:** Como desarrollador Spring Boot, quiero configurar el conector y la infraestructura de la base de datos vectorial, para persistir y consultar las representaciones numéricas (embeddings) del contexto del usuario y las acciones mapeadas.
**Criterios de aceptación:** Dada una instancia activa de la BD vectorial, cuando Spring Boot ejecute la inicialización, entonces debe establecer la conexión correctamente y permitir la creación de colecciones/índices para almacenar vectores.

#### ATOM-41 · API-SPRING — Orquestador de cliente (Spring ↔ IA Python)
**Historia:** Como desarrollador Spring Boot, quiero crear el Driven Adapter para conectarme al módulo de IA en Python mediante peticiones HTTP (FastAPI) o gRPC, delegando el procesamiento de voz (STT/TTS) y la inferencia.
**Criterios de aceptación:** Dado un flujo que requiere IA, cuando Spring Boot envíe el payload al microservicio Python, entonces debe recibir la respuesta estructurada (transcripción o acción a ejecutar) sin bloquear el hilo principal de forma ineficiente.

#### ATOM-42 · IA-PYTHON — Módulo de embeddings
**Historia:** Como desarrollador de IA, quiero desarrollar un script en Python que tome cadenas de texto (comandos del usuario o textos extraídos de la pantalla) y las transforme en vectores de alta dimensión mediante un modelo de lenguaje (Embedding Model), para interactuar con la BD vectorial.
**Criterios de aceptación:** Dado un texto enviado al módulo de IA, cuando el modelo lo procese, entonces debe retornar un arreglo de floats (el vector) listo para indexarse o compararse por similitud de coseno.

#### ATOM-43 · JAVA-SPRING — Exposición de STT/TTS por API local
**Historia:** Como desarrollador, quiero envolver los scripts base de voz (HU-06 STT y TTS) en un framework web ligero (FastAPI) para exponerlos como endpoints locales consumibles limpiamente por Spring Boot.
**Criterios de aceptación:** Dado el servidor FastAPI en ejecución, cuando reciba una petición en /stt con un archivo de audio, entonces debe retornar el JSON con el texto transcrito con la menor latencia posible.

#### ATOM-44 · CORE — Flujo de inferencia de acciones
**Historia:** Como componente del sistema operativo en el dispositivo móvil, se requiere capturar el árbol de nodos de la pantalla activa únicamente en el momento exacto en que se detecta una orden o solicitud de acción, para evitar el consumo excesivo de batería, procesamiento y memoria en segundo plano.
**Criterios de aceptación:**
- Dado que la app tiene los permisos de accesibilidad concedidos, cuando se detecte la activación por un comando o evento de entrada, entonces el servicio debe realizar una única captura del estado actual de la interfaz (árbol de nodos / jerarquía UI).
- Tomada la captura, cuando se serialice el árbol de nodos, entonces el servicio debe detener inmediatamente el escaneo y empaquetar los datos para su envío al backend, evitando que el flujo de captura quede abierto en bucle.

#### ATOM-45 · INFRAESTRUCTURA — Inicialización de la BD vectorial
**Historia:** Como sistema, se requiere construir y configurar una instancia de base de datos vectorial, para proveer un mecanismo de persistencia indexada que permita almacenar y consultar los embeddings generados del contexto de pantalla y los comandos recibidos.
**Criterios de aceptación:**
- Dado el entorno de infraestructura del proyecto, cuando se despliegue el contenedor o servicio de la BD vectorial (Qdrant, Milvus o PGVector), entonces el servicio debe estar activo y accesible en el puerto configurado.
- Dado el servicio vectorial activo, cuando se ejecute el script de inicialización, entonces deben crearse las colecciones/índices iniciales con la dimensión específica requerida por el modelo de embeddings.

#### ATOM-46 · BACKEND — Integración de APIs externas y fallback
**Historia:** Como capa de servicio en el backend, se requiere un componente centralizador que gestione las peticiones a las distintas APIs externas (LLMs, proveedores de voz o servicios cloud), implementando políticas de reintento y respuestas alternativas (fallback) para que el asistente no se cuelgue si una API externa falla o genera latencia.
**Criterios de aceptación:** Dado que el backend hace una petición a una API externa (p. ej. el LLM), cuando dicha API retorne un error (500, Rate Limit o Timeout), entonces el sistema debe reintentar la petición de forma controlada o ejecutar una acción por defecto (fallback), notificando al cliente de forma limpia sin romper la app.

## **Sprint 3: Pulido, Refinamiento y Calidad:**

**Objetivo:** Refinar la experiencia: lectura de pantalla en tiempo real, automatización de gestos, persistencia local privada y optimización de voz y memoria.

| ID | Componente | Historia de Usuario (HU) | Story Points (SP) |
| :--- | :--- | :--- | :---: |
| **ATOM-48** | ANDROID | Lectura de pantalla en tiempo real mediante accesibilidad. | 8 |
| **ATOM-49** | ANDROID | Simulación de toques y automatización de gestos en apps de terceros. | 5 |
| **ATOM-51** | ANDROID | Optimización del historial local y la caché para privacidad absoluta. | 5 |
| **ATOM-52** | IA-BACKEND | Optimización de memoria semántica y búsqueda vectorial en Qdrant. | 8 |
| **ATOM-55** | ANDROID | Migración y configuración del nuevo modelo de voz unificado para TTS. | 2 |
| **ATOM-56** | ANDROID | Detonador de voz en segundo plano mediante la palabra clave «ATOM». | 5 |

### Historias de usuario — Sprint 3

#### ATOM-48 · ANDROID — Lectura de pantalla en tiempo real
**Historia:** Como usuario de Atom, quiero que la aplicación pueda leer y entender lo que hay en mi pantalla sin importar qué app tenga abierta, para que el asistente conozca exactamente mi contexto y pueda ayudarme sin que yo tenga que explicarle visualmente lo que veo.
**Criterios de aceptación:**
- Crear y registrar el ScreenReaderAccessibilityService en el manifiesto con los permisos requeridos.
- Capturar de forma asíncrona el texto y las descripciones de contenido de la vista activa (usando Virtual Threads para no bloquear el hilo de UI).
- Enviar el texto estructurado de la pantalla al puerto de la aplicación para dar contexto al flujo de inferencia.
- Detener automáticamente el escaneo cuando el overlay se minimiza para optimizar la batería.

#### ATOM-49 · ANDROID — Automatización de gestos en apps de terceros
**Historia:** Como usuario de Atom, quiero que el asistente pueda pulsar botones, hacer scroll y navegar dentro de mis apps de terceros por mí, para que ejecute tareas y flujos complejos en mi teléfono de forma totalmente automatizada con solo pedírselo por voz.
**Criterios de aceptación:**
- Implementar la búsqueda de nodos específicos por texto o resource ID dentro de la app en primer plano.
- Usar la API dispatchGesture() de Android para simular clics precisos en la interfaz.
- Mapear las respuestas del caso de uso de inferencia (Order-to-Action) a comandos físicos ejecutables (p. ej. «Pulsa el botón Enviar»).
- Manejar excepciones si la app de terceros cambia de estado inesperadamente durante la automatización.

#### ATOM-51 · ANDROID — Persistencia e historial local privado
**Historia:** Como usuario de Atom, quiero que mi historial de chat se almacene de forma segura exclusivamente en la memoria local de mi dispositivo, para garantizar la máxima privacidad de mis datos personales y que mis conversaciones no se registren en servidores externos.
**Criterios de aceptación:**
- Diseñar la estructura de almacenamiento local con una base de datos SQLite integrada o un mecanismo de caché cifrada nativo de Android.
- Desarrollar el adaptador de infraestructura de persistencia local en la capa Android conectado al puerto de salida de la aplicación.
- Asegurar que el caso de uso consuma este adaptador local para guardar y recuperar mensajes cronológicamente usando el identificador de sesión.
- Validar mediante pruebas unitarias que los datos sensibles del chat se destruyen de la caché local si el usuario limpia la sesión o el overlay se cierra de forma segura.

#### ATOM-52 · IA-BACKEND — Memoria semántica y búsqueda vectorial
**Historia:** Como usuario de Atom, quiero que el asistente asocie instantáneamente mis comandos de voz con las acciones correctas a ejecutar en pantalla, para que sus respuestas y ejecuciones sean ultrarrápidas, precisas y adaptadas a mis necesidades específicas.
**Criterios de aceptación:**
- Configurar la colección en Qdrant usando la métrica de distancia coseno alineada con los vectores del modelo de IA.
- Desarrollar o refinar el adaptador de infraestructura para exponer métodos optimizados de búsqueda semántica y upsert.
- Inyectar los resultados retornados por el adaptador de Qdrant directamente en el caso de uso ActionInferenceFlow para acelerar la toma de decisiones del agente.

#### ATOM-55 · ANDROID — Nuevo modelo de voz unificado (TTS)
**Historia:** Como usuario de Atom, quiero que el asistente me responda con un tono de voz mucho más natural, claro y profesional, acordado por el equipo, para que la interacción por voz sea agradable y no parezca un robot genérico o plano.
**Criterios de aceptación:**
- Configurar los parámetros de inicialización del motor TTS nativo de Android dentro del adaptador de infraestructura de voz.
- Ajustar el tono (pitch) y la velocidad del habla en el caso de uso de control de voz según los estándares definidos por el equipo.
- Implementar un mecanismo de fallback que seleccione una voz nativa de alta calidad en español si el modelo específico no está descargado en el dispositivo.
- Validar la correcta sincronización del flujo de audio de forma concurrente con los virtual threads para evitar interrupciones en respuestas largas.

#### ATOM-56 · ANDROID — Detonador de voz «ATOM» en segundo plano
**Historia:** Como usuario de Atom, quiero que la aplicación se active y abra su interfaz flotante automáticamente cada vez que diga la palabra «ATOM», para usar el asistente totalmente manos libres sin tener que tocar la pantalla ni abrir la app manualmente.
**Criterios de aceptación:**
- Configurar un servicio persistente en segundo plano con reconocimiento nativo para escuchar pasivamente el stream de audio del micrófono.
- Implementar en el adaptador de infraestructura de voz la lógica para procesar el audio localmente y detectar la coincidencia exacta de la palabra clave «ATOM».
- Conectar la detección exitosa al caso de uso de activación, que debe disparar inmediatamente el FloatingBubbleService.
- Optimizar el algoritmo de escucha pasiva usando Virtual Threads para el procesamiento de audio, manteniendo el consumo de batería al mínimo según los estándares de eficiencia del proyecto.

## **Sprint 4: Refinamiento, QA, Pruebas y Despliegue:**

**Objetivo:** Estabilizar el MVP: seguridad e integridad del dispositivo, autenticación, compatibilidad multi-OEM, pruebas de estrés y publicación en Play Store.

| ID | Componente | Historia de Usuario (HU) | Story Points (SP) |
| :--- | :--- | :--- | :---: |
| **ATOM-36** | LANDING | Implementación de secciones y enlaces de descarga. | 2 |
| **ATOM-50** | SEGURIDAD | Sistema de validación de integridad del dispositivo y seguridad anti-fraude. | 8 |
| **ATOM-53** | UI/UX | Optimización estética y minimalismo radical de la interfaz del overlay. | 5 |
| **ATOM-54** | SEGURIDAD | Autenticación segura por correo y persistencia de perfiles en MongoDB. | 5 |
| **ATOM-57** | ANDROID | Adaptación del agente a variantes de Android (HyperOS, Oppo, Vivo, etc.). | 8 |
| **ATOM-58** | OPTIMIZACIÓN | Pruebas de estrés y optimización del flujo de procesos de la IA. | 5 |
| **ATOM-59** | DESPLIEGUE | Configuración de Google Play Console y preparación del lanzamiento beta. | 5 |
| **ATOM-60** | DOCUMENTACIÓN | Documentación técnica de bugs detectados y manual de estabilización del MVP. | 3 |
| **ATOM-61** | BACKEND | Sanitización de errores: evitar fugas de excepciones nativas (gRPC + HTTP) (subtarea). | — |

### Historias de usuario — Sprint 4

#### ATOM-36 · LANDING — Secciones y enlaces de descarga
**Historia:** Como Thomas (usuario final), quiero maquetar la landing page interactiva en TypeScript con la información del asistente y los botones de descarga del APK, para permitir que los usuarios conozcan la app y la instalen.
**Criterios de aceptación:** Dado el diseño previo de la landing, cuando el usuario navegue por la web, entonces debe poder visualizar las características del asistente y pulsar el botón de descarga, iniciando la descarga del APK (almacenado temporalmente en el servidor o storage).

#### ATOM-50 · SEGURIDAD — Validación de integridad del dispositivo
**Historia:** Como usuario de Atom, quiero tener la certeza de que mi dispositivo cuenta con un entorno de ejecución seguro y libre de malware, para que mis datos sensibles en pantalla y mis automatizaciones no sean comprometidos ni interceptados por terceros.
**Criterios de aceptación:**
- Implementar lógica de producción en el DeviceInspectorAdapter para detectar binarios `su` y aplicaciones de superusuario.
- Implementar la verificación de propiedades del sistema/hardware para identificar emuladores comunes (ro.kernel.qemu, ro.product.model).
- Conectar el resultado al caso de uso de inicialización: si el entorno se detecta como inseguro, el FloatingBubbleService debe destruirse de forma inmediata y segura.
- Desarrollar pruebas unitarias con Mockito para simular y validar respuestas en entornos seguros e inseguros.

#### ATOM-53 · UI/UX — Minimalismo radical del overlay
**Historia:** Como usuario de Atom, quiero una interfaz extremadamente limpia, oscura y despejada en la burbuja flotante, para que no obstruya mi vista mientras uso otras aplicaciones y consuma la menor batería posible.
**Criterios de aceptación:**
- Modificar los layouts XML del FloatingBubbleService para aplicar estrictamente el color #0A0A0C (negro puro), optimizando el consumo en pantallas OLED.
- Eliminar componentes visuales redundantes y simplificar las transiciones usando solo animaciones esenciales optimizadas en Lottie.
- Realizar pruebas con el Android Profiler para asegurar que el overlay no genere picos de CPU ni mantenga wakelocks activos en segundo plano.

#### ATOM-54 · SEGURIDAD — Autenticación por correo y perfiles en MongoDB
**Historia:** Como usuario de Atom, quiero poder iniciar sesión de forma segura usando mi correo electrónico dentro de la aplicación, para sentir confianza de que mi cuenta es privada y sincronizar de forma segura mis ajustes personalizados con el servidor.
**Criterios de aceptación:**
- Implementar el caso de uso de autenticación para validar credenciales de correo y contraseñas cifradas.
- Desarrollar el adaptador de infraestructura en el backend para almacenar y consultar perfiles de usuario en una nueva colección dedicada de MongoDB.
- Configurar la transferencia segura de los datos de autenticación por el canal de comunicación gRPC existente.
- Implementar hashing robusto en el backend para almacenar las contraseñas de forma segura sin texto plano.

#### ATOM-57 · ANDROID — Adaptación a variantes de Android (OEM)
**Historia:** Como usuario de un dispositivo Xiaomi, Oppo o Vivo, quiero que Atom ejecute sus servicios en segundo plano y la burbuja flotante sin que el sistema operativo los cierre o bloquee inesperadamente, para poder usar el asistente con normalidad sin importar la capa de personalización de mi teléfono.
**Criterios de aceptación:** detallados en el issue de Jira.

#### ATOM-58 · OPTIMIZACIÓN — Pruebas de estrés y flujo de la IA
**Historia:** Como usuario de Atom, quiero que mis comandos de voz se procesen y mi pantalla se lea sin lag ni sobrecalentamiento del teléfono, para disfrutar de un flujo de automatización fluido que responda rápidamente durante el uso continuo.
**Criterios de aceptación:** detallados en el issue de Jira.

#### ATOM-59 · DESPLIEGUE — Google Play Console y lanzamiento beta
**Historia:** Como usuario final de la comunidad, quiero poder descargar la app de Atom de forma segura directamente desde Google Play Store, para instalar fácilmente las actualizaciones del MVP y confiar en que la app cumple los estándares de la tienda.
**Criterios de aceptación:** detallados en el issue de Jira.

#### ATOM-60 · DOCUMENTACIÓN — Bugs y manual de estabilización del MVP
**Historia:** Como usuario (desarrollador o tester del equipo Atom), quiero un registro claro de los bugs encontrados en el MVP junto con documentación técnica de la arquitectura actual, para corregir problemas rápidamente durante la fase beta y asegurar que la app se mantenga estable en producción.
**Criterios de aceptación:** detallados en el issue de Jira.

#### ATOM-61 · BACKEND — Sanitización de errores (subtarea)
**Historia:** Como desarrollador, quiero sanitizar los errores para evitar la fuga de excepciones nativas hacia el cliente (gRPC + HTTP), para que ningún detalle interno del sistema quede expuesto en las respuestas de error.
**Criterios de aceptación:** pendientes de documentar en el issue de Jira.

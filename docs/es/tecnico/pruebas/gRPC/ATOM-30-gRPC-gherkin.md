# Documentación técnica: Pruebas unitarias gRPC [ATOM].

> **Author:** Emmanuel Suárez García.
> **Contenido:** Pruebas unitarias del adaptador de interacción.
> **Estado:** 🟢 Implementado.

---

Este documento busca describir la estructura y el funcionamiento de las pruebas unitarias diseñadas para la capa de conexión gRPC. Para validar el envío de mensajes de red sin necesidad de iniciar servicios externos o cargar un framework pesado como el contexto de Spring Boot, se construyó una arquitectura de servidor de red aislada en memoria.

## 1. Configuración de la arquitectura de pruebas:

Para validar nuestro adaptador gRPC, establecemos un entorno controlado que imita el servidor de producción utilizando utilidades de prueba ligeras y seguras para hilos (thread-safe):

- **GrpcCleanupRule:** Una herramienta oficial de gRPC que gestiona y destruye automáticamente los canales de red y servidores después de la ejecución de cada prueba para evitar fugas de hilos (threads).
- **FakeAtomAgentService:** Una clase interna estática y concreta que extiende la implementación base generada por gRPC. Actúa como un simulador (mock) del servidor Python, permitiéndonos predefinir respuestas estándar o lanzar errores específicos.
- **Servidor y canal en memoria:** Utiliza `InProcessServerBuilder` y `InProcessChannelBuilder` para construir un bucle de comunicación localizado (loopback) que evita el uso de puertos de red reales.
- **Mecanismo de reflexión:** Inyecta manualmente los objetos del canal simulado y del stub dentro de la instancia del adaptador, desacoplando por completo la suite de pruebas de los frameworks de integración pesados.

### 1.2 Casos de prueba y criterios de validación

La suite de pruebas valida los métodos exactos mapeados en la definición del contrato proto, analizando flujos de comunicación tanto síncronos como de streaming:

**shouldReturnCommandResponseSuccessfully:** Esta prueba verifica el flujo de ejecución de una RPC síncrona (unaria). Activa una petición de estado del sistema, intercepta la carga útil de los parámetros utilizando un `CommandResponse` preconfigurado y ejecuta aserciones para garantizar que el adaptador desempaqueta y formatea correctamente el mensaje del servidor.

**shouldReturnStreamChatTokensCorrectly:** Esta prueba verifica el patrón de comunicación RPC de streaming de servidor utilizado para las interacciones de IA. Configura una lista ordenada de tokens de mensajes individuales (`MessageResponse`) y asegura que el adaptador los procese como una secuencia reactiva continua (`Stream<String>`). Verifica que todos los fragmentos (chunks) se recolecten en el orden exacto en que fueron transmitidos por el servidor simulado.

---
> **Acceso directo:**
> [Referencia de ejecución de pruebas](../../../../../java/src/test/java/com/atom/grpc/InteractionGrpcAdapterTest.java)

## 2. Ciclo de vida de la infraestructura de pruebas:

Para garantizar la independencia y la repetibilidad de las pruebas, una secuencia clara de inicialización y desmontaje gestiona todos los sockets de la memoria:

- **Fase de configuración (Setup):** Genera una dirección de servidor aleatoria, arranca la arquitectura en memoria, establece un ejecutor directo del cliente y ejecuta el mecanismo de reflexión para inyectar los campos internos.
- **Fase de desmontaje (Teardown):** Invoca la destrucción forzada inmediata (`shutdownNow()`) en los canales activos al final de cada prueba para evitar la contaminación cruzada entre pruebas o el consumo de recursos en segundo plano.

### 2.1 Justificación de los objetos clave de prueba

| Componente / Objeto                   | Razón técnica de su implementación                                                                                                                |
|:--------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------|
| **InProcessServerBuilder**            | Genera un servidor embebido aislado dentro de la JVM, permitiendo una ejecución rápida y evitando errores por colisión de puertos.                |
| **StreamObserver**                    | El mecanismo reactivo utilizado para enviar las cargas útiles simuladas o los fragmentos de streaming secuencialmente de vuelta al cliente proxy. |
| **Inyección de campos por reflexión** | Estrategia esencial para sobrescribir propiedades privadas dentro de la clase del adaptador sin tener que exponerlas en el código de producción.  |

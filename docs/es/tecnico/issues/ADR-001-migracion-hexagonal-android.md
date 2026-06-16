# ADR-001 — Adopción de un núcleo hexagonal en la app Android de Atom

- **Estado:** Aceptado
- **Fecha:** 2026-06-14
- **Responsables:** Arquitectura (Arquitecto Principal), equipo Mobile (The-Galactics)
- **Origen del contexto:** Migración del código hexagonal del backend (del proyecto Spring Boot `Atom`) hacia la app Android `Atom_app`.

---

## 1. Contexto

El sistema Atom tiene tres partes:

1. **Cliente Android** (`Atom_app`) — la app que ejecuta el usuario.
2. **Backend Java/Spring Boot** (`Atom`) — servicio hexagonal / puertos y adaptadores.
3. **Agente de IA en Python** (`Atom-agent`) — el entorno de ejecución de la IA, accedido por **gRPC** (`ai.proto`, servicio `AtomAgentService`).

Durante el desarrollo, se pegó una copia en crudo de los paquetes `domain`, `application` e `infrastructure` del backend dentro del módulo Android, bajo `src/main/java/com/atom/`. Ese código depende de **Spring, Lombok, Spring Data MongoDB, AspectJ, jakarta.validation y Spring Security** — nada de lo cual está en el classpath de Android. Como resultado:

- El módulo Android **no compila**.
- `build.gradle.kts` está **malformado** (un fragmento de dependencia duplicado y una llave `}` huérfana colgando tras el bloque `dependencies`).
- El adaptador gRPC referencia stubs generados (`AtomAgentServiceGrpc`) que **nunca se generan** — no hay codegen de protobuf/grpc configurado en Gradle, pese a estar presentes las dependencias de runtime `io.grpc.*` y el archivo `src/main/proto/ai.proto`.

La app ya cuenta con una **capa de presentación MVVM funcional** bajo `com.atom.app` (MainActivity, SettingsActivity, ChatViewModel con LiveData, ChatRepository sobre Retrofit, ApiService `GET api/chat?prompt=`, RetrofitClient usando `BuildConfig.BASE_URL`).

Necesitamos una arquitectura objetivo que (a) produzca un módulo **compilable**, (b) **preserve la UI funcional** y (c) **adopte correctamente el núcleo hexagonal** para que el equipo mobile pueda extenderlo de forma limpia.

---

## 2. Decisión

Adoptar una **arquitectura hexagonal (Clean) adaptada a Android** con cuatro capas, y **mantener el MVVM existente como capa de presentación que envuelve el núcleo hexagonal** (en lugar de reescribir la UI sobre nuevas abstracciones). Este es el camino de menor riesgo.

### 2.1 Mapa de capas

```
com.atom.domain          <- reglas de empresa. POJOs Java puros. CERO dependencias de framework.
com.atom.application     <- reglas de aplicación. Puertos (in/out) + casos de uso + excepciones. CERO dependencias de Android/framework.
com.atom.infrastructure  <- adaptadores. Hablan con el mundo exterior (gRPC, sensores del dispositivo). PUEDEN usar APIs de Android.
com.atom.app             <- presentación (EXISTENTE). Activities + ViewModel + Repository. UI de Android.
```

**La regla de dependencias:** las dependencias apuntan solo hacia adentro. `app -> infrastructure -> application -> domain`. `domain` no depende de nada. `application` depende solo de `domain`. Nada de lo interno importa nada de `app` ni `infrastructure`.

### 2.2 Qué se MIGRA tal cual (libre de framework, reutilizable)

- **Dominio:** `Chat`, `Message`, `User`, `security/DeviceSecurityStatus`, `security/ValidationResult`, `utils/RiskLevel`. Son POJOs/records puros que usan solo `java.*`.
- **Puertos de aplicación:** todo `port/in/*`, `port/out/*`, `port/in/security/*`, `port/out/security/*`.
- **Excepciones de aplicación:** `DeviceSecurityException`, `InputValidationException`.
- **Casos de uso (ya sin anotación de Spring):** `ExternalCommandUseCase`, `ExternalMessageUseCase`, `UserUseCase`.

### 2.3 Qué se ADAPTA (piezas que se conservan, des-Springificadas)

- **`InputValidationUsecase`** y **`DeviceSecurityUseCase`** — se quita la anotación `@Service`; la lógica es portable tal cual.
- **`InteractionGrpcAdapter`** — se quita `@Component`; se reemplaza la inyección por constructor `@Value("${grpc.agent.host/port}")` por **parámetros de constructor planos alimentados desde `BuildConfig`**; se reemplaza el ciclo de vida `javax.annotation.@PostConstruct`/`@PreDestroy` por **métodos explícitos `init()` / `shutdown()`** invocados por la raíz de composición. La lógica del canal gRPC permanece, por lo demás, sin cambios.
- **`DeviceInspectorAdapter`** — se quita `@Component`. **De forma crítica, se reimplementa `isRunningOnEmulator()` contra `android.os.Build`** (FINGERPRINT / MODEL / MANUFACTURER / BRAND / PRODUCT / HARDWARE) en lugar de `System.getProperty("ro.product.*")`, que no funciona en un runtime real de Android. Las verificaciones de binarios de root y de proxy se portan directamente.

### 2.4 Qué se EXCLUYE (permanece en el servidor) y por qué

| Excluido | Razón por la que permanece en el backend |
|---|---|
| `SecurityController` (`@RestController`) | Capa de endpoints HTTP; el cliente *consume* APIs, no las hospeda. |
| `UserEntity`/`ChatEntity`/`MessageEntity` | Persistencia con Spring Data MongoDB; la base de datos vive en el servidor. |
| `Argon2PasswordEncoderAdapter` | Criptografía de Spring Security; **el hashing de contraseñas debe ocurrir en el servidor**, nunca en el cliente. |
| `DeviceSecurityAspect` + `@SecureOperation` | Spring AOP; no disponible en Android. La aplicación de la regla se reimplementa como una llamada de guarda explícita donde se necesite. |
| `Securityexceptionhandler` (`@RestControllerAdvice`) | Mapeo global de errores HTTP (`ProblemDetail`); es una preocupación del servidor. |
| `dto/req/*`, `dto/res/*` | DTOs de petición/respuesta con `jakarta.validation` atados al contrato HTTP. |
| `UserInAdapter`, `UserOutAdapter`, `UserMapper` | Stubs vacíos en el origen; nada que migrar. |

### 2.5 Inyección de dependencias sin Spring

Android no tiene contenedor de Spring. Lo reemplazamos por una **raíz de composición manual** (una factory / service-locator escrita a mano, p. ej. `AppContainer`) que construye el grafo de objetos una sola vez y entrega casos de uso ya construidos a la capa de presentación. Esto conserva la inyección por constructor (testeable, explícita) sin añadir Dagger/Hilt — apropiado para el tamaño actual del grafo. Hilt puede introducirse más adelante si el grafo crece; es una opción futura no disruptiva.

### 2.6 Cómo se conecta la capa de presentación

El **`ChatRepository` existente se convierte en el puente** entre la UI de Android y el núcleo hexagonal. En lugar de (o además de) su llamada Retrofit, delega en los puertos de entrada / casos de uso obtenidos de la raíz de composición. `ChatViewModel` y las Activities no cambian de forma — siguen exponiendo LiveData. Esto preserva la UI funcional mientras enruta el trabajo real a través del núcleo hexagonal.

> **Nota de despliegue de menor riesgo:** la ruta Retrofit `GET api/chat?prompt=` existente se deja intacta y funcional. La ruta hexagonal/gRPC se cablea por debajo para que el equipo pueda cambiar la implementación de respaldo del repositorio sin tocar la UI. Esto evita un corte abrupto de tipo *big-bang* (una adopción al estilo *strangler-fig*).

---

## 3. Decisiones del sistema de build

1. **Arreglar `build.gradle.kts`** — eliminar el fragmento de dependencia duplicado y la llave huérfana para que el script parsee.
2. **Cablear el codegen de gRPC/protobuf** — añadir el plugin de Gradle `com.google.protobuf` con `protoc` + el codegen de gRPC Java + `javalite`, apuntando a **`grpc-protobuf-lite`** (ya es una dependencia — el runtime lite es la elección apropiada para Android). El `java_package = com.atom.infrastructure.adapter.grpc` del proto hace que los stubs generados aterricen exactamente donde `InteractionGrpcAdapter` los espera.
3. **Campos de `BuildConfig` para gRPC** — añadir los build config fields `GRPC_HOST` / `GRPC_PORT` (tomados de `local.properties`, mismo patrón que `BASE_URL`) para que el adaptador gRPC se configure sin inyección de propiedades de Spring.
4. **Alineación AGP/Gradle** — AGP 8.7.3 con Gradle 9.5.1 es una incompatibilidad conocida; el paso de verificación del build debe confirmar la resolución y ajustar el wrapper o AGP si el build se niega a configurarse.

---

## 4. Consecuencias

**Positivas**
- El módulo compila; la UI se preserva; se aplica una regla de dependencias limpia.
- Las capas de dominio + aplicación son **testeables unitariamente en la JVM** sin dependencias de Android/Spring.
- El contrato gRPC permanece compartido y se regenera desde la única fuente `ai.proto`.
- Costura clara y documentada (`ChatRepository`) para intercambiar backends REST ↔ gRPC.

**Negativas / costos**
- La factory de DI manual es boilerplate que el equipo debe mantener (mitigado: grafo pequeño; Hilt es una opción posterior).
- `DeviceInspectorAdapter` necesita una reimplementación real en Android de la detección de emulador (registrado como TODO).
- Cierta duplicación entre los modelos de dominio de aquí y las entidades del backend — aceptable; el cliente posee su propia copia de dominio (rasgo normal de sistemas hexagonales distribuidos).

**Riesgos / seguimientos**
- gRPC en texto plano (`usePlaintext()`) está bien para desarrollo local, pero **debe usar TLS en producción**.
- Las tres superficies de API (Retrofit REST, backend `/api/v1/security`, gRPC) deberían converger con el tiempo; fuera del alcance de este ADR.

---

## 5. Alternativas consideradas

1. **Reescribir la UI sobre nuevas abstracciones** — rechazada: alto riesgo, sin ganancia funcional ahora.
2. **División en múltiples módulos de Gradle (domain/app como módulos separados)** — diferida: más limpia a largo plazo, pero más configuración de la necesaria para una app de un solo equipo hoy; la regla de dependencias a nivel de paquete da casi todo el beneficio ahora.
3. **Mantener Spring vía un BFF del lado del servidor y dejar Android delgado** — viable, pero no resuelve el objetivo inmediato de "hacer que este módulo compile con el núcleo hexagonal"; el cliente sigue necesitando seguridad de dispositivo del lado del cliente y gRPC.

---

## 6. Actualización — decisiones ejecutadas (2026-06-14)

Tras aceptarse este ADR se tomaron e implementaron dos decisiones de seguimiento:

1. **Transporte del chat: corte a gRPC (reemplaza la nota de despliegue strangler-fig de §2.6).** La ruta Retrofit/REST se eliminó por completo — `ApiService`, `RetrofitClient`, los build config fields `BASE_URL` y las dependencias Retrofit/OkHttp/Gson están borrados. `ChatRepository` ahora delega en `ExternalMessageUseCase` (`StreamChatPortIn`) sobre gRPC, recolectando el `Stream<String>` de tokens transmitido por el servidor en una única respuesta para que la UI no cambie. La raíz de composición descrita en §2.5 se implementó como `AppContainer`, hospedada en una subclase `Application` (`AtomApp`) e inyectada en `ChatViewModel` mediante un `ChatViewModelFactory` (`ViewModelProvider.Factory`).

2. **Autenticación / hashing de contraseñas: del lado del servidor, fuera del alcance de la app (diferido).** `UserUseCase` y `PasswordEncoderPortOut` intencionadamente **no** se cablean en la raíz de composición, y **no se construye ningún adaptador Argon2 en Android**. La autenticación de usuarios y el hashing de contraseñas siguen siendo responsabilidad del backend (consistente con la exclusión de `Argon2PasswordEncoderAdapter` en §2.4). Como la app aún no tiene identidad de usuario, `ChatRepository` usa un `userId`/`chatId` aleatorio por sesión para satisfacer el contrato del caso de uso. Cablear una identidad de usuario real es trabajo futuro, condicionado a que se defina la estrategia de autenticación.

> **Nota sobre `usePlaintext()` / TLS:** el seguimiento de TLS en producción de §4 sigue vigente y no se ve afectado por el corte a gRPC.

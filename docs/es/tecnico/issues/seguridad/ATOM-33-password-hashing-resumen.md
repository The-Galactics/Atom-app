# Resumen de implementación: Hashing de contraseñas con Argon2

## Qué se hizo

1. Se agregó un puerto de salida para hashing de contraseñas en la capa de aplicación:
   - `java/src/main/java/com/atom/application/port/out/PasswordEncoderPortOut.java`
   - Expone dos operaciones:
     - `hashPassword(String rawPassword)`
     - `verifyPassword(String rawPassword, String encodedPassword)`

2. Se implementó un adaptador de infraestructura para usar Argon2:
   - `java/src/main/java/com/atom/infrastructure/adapter/out/Argon2PasswordEncoderAdapter.java`
   - Usa `org.springframework.security.crypto.argon2.Argon2PasswordEncoder`
   - Valida entrada vacía y permite:
     - generar hashes seguros
     - verificar contraseñas contra hashes

3. Se desarrolló el caso de uso que usa el puerto de hashing para registrar y autenticar usuarios:
   - `java/src/main/java/com/atom/application/usecase/UserUseCase.java`
   - En el método `registerUser(...)` se genera el hash antes de crear el `User`
   - En `authenticateUser(...)` se compara la contraseña en texto plano con el hash almacenado

4. Se agregaron pruebas unitarias:
   - `java/src/test/java/com/atom/Argon2PasswordEncoderAdapterTest.java`
   - `java/src/test/java/com/atom/UserUseCaseTest.java`
   - Prueban:
     - generación de hash
     - verificación correcta
     - rechazo con contraseña incorrecta
     - registro y autenticación usando el caso de uso

5. Se documentó el comportamiento en Gherkin:
   - `docs/es/tecnico/issues/ATOM-33-password-hashing-gherkin.md`
   - Incluye escenarios de:
     - hash irreversible
     - comparación correcta
     - comparación incorrecta

6. Se ajustó la configuración de Maven para usar `spring-security-crypto` y BouncyCastle.

## Por qué usar Argon2

Argon2 es una función de derivación de claves diseñada para hashing de contraseñas:
- es resistente a ataques de GPU
- usa sal interna, por lo que cada hash es único
- es unidireccional: no se puede recuperar la contraseña
- el resultado se verifica con `matches`, no se descifra

Esta es la mejor estrategia de hasheo actual porque combina:
- resistencia al cómputo intensivo
- configuración de memoria/cpu
- protección contra ataques de fuerza bruta y ataques de diccionario

## Cada capa explicada

### Capa de Dominio
- `java/src/main/java/com/atom/domain/User.java`
- Representa al usuario y contiene la contraseña ya hasheada en su propiedad `password`
- No realiza hashing ni lógica de seguridad; solo mantiene el estado del usuario

### Capa de Aplicación / Caso de Uso
- `java/src/main/java/com/atom/application/usecase/UserUseCase.java`
- Orquesta la lógica de seguridad y persistencia
- Depende de puertos (`UserPortOut`, `PasswordEncoderPortOut`) y no de implementaciones concretas
- Implementa el flujo:
  - registrar usuario con contraseña en texto plano -> hash -> persistir
  - autenticar usuario -> buscar por email -> validar hash

### Capa de Puerto (Interfaces)
- `java/src/main/java/com/atom/application/port/out/PasswordEncoderPortOut.java`
- Actúa como contrato entre core y capa de infraestructura
- Permite reemplazar la implementación (por ejemplo Argon2, BCrypt o una versión mock para pruebas)

### Capa de Infraestructura
- `java/src/main/java/com/atom/infrastructure/adapter/out/Argon2PasswordEncoderAdapter.java`
- Implementa el contrato del puerto usando Argon2
- Está ubicada en `infrastructure` porque es detalle de implementación externo

### Capa de Pruebas
- `java/src/test/java/com/atom/Argon2PasswordEncoderAdapterTest.java`
- `java/src/test/java/com/atom/UserUseCaseTest.java`
- Validan que el servicio de hashing se comporte correctamente y que el caso de uso sea seguro

### Capa de Construcción
- `pom.xml` en la raíz: define el proyecto multi-módulo y el módulo `java`
- `java/pom.xml`: define dependencias, plugins y configuración específica del backend

## Resultado final

- El proyecto Java compila correctamente.
- Los tests específicos de hashing y autenticación se ejecutaron con éxito.
- La implementación está desacoplada: el core no conoce Argon2 directamente.
- Ahora el sistema puede procesar contraseñas de forma segura y almacenar solo hashes.

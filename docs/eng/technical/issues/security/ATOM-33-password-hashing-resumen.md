# Implementation Summary: Password Hashing with Argon2

## What Was Done

1. An outbound port for password hashing was added to the application layer:
    - `java/src/main/java/com/atom/application/port/out/PasswordEncoderPortOut.java`
    - It exposes two operations:
        - `hashPassword(String rawPassword)`
        - `verifyPassword(String rawPassword, String encodedPassword)`

2. An infrastructure adapter was implemented to use Argon2:
    - `java/src/main/java/com/atom/infrastructure/adapter/out/Argon2PasswordEncoderAdapter.java`
    - It uses `org.springframework.security.crypto.argon2.Argon2PasswordEncoder`.
    - It validates empty input and allows:
        - generating secure hashes
        - verifying passwords against hashes

3. The use case that consumes the hashing port to register and authenticate users was developed:
    - `java/src/main/java/com/atom/application/usecase/UserUseCase.java`
    - In the `registerUser(...)` method, the hash is generated before creating the `User` entity.
    - In `authenticateUser(...)`, the plain text password is compared against the stored hash.

4. Unit tests were added:
    - `java/src/test/java/com/atom/Argon2PasswordEncoderAdapterTest.java`
    - `java/src/test/java/com/atom/UserUseCaseTest.java`
    - They test:
        - hash generation
        - correct verification
        - rejection with an incorrect password
        - registration and authentication using the use case

## Architecture and Structure

The project follows a Hexagonal Architecture approach:

### Core Layer (Application & Domain)
- `java/src/main/java/com/atom/application/usecase/UserUseCase.java`
- It contains the core business rules.
- It depends on ports (`UserPortOut`, `PasswordEncoderPortOut`) and not on concrete implementations.
- It implements the workflow:
    - register user with plain text password -> hash -> persist
    - authenticate user -> find by email -> validate hash

### Port Layer (Interfaces)
- `java/src/main/java/com/atom/application/port/out/PasswordEncoderPortOut.java`
- It acts as a contract between the core and the infrastructure layer.
- It allows replacing the implementation (for example, Argon2, BCrypt, or a mock version for testing).

### Infrastructure Layer
- `java/src/main/java/com/atom/infrastructure/adapter/out/Argon2PasswordEncoderAdapter.java`
- It implements the port contract using Argon2.
- It is located in `infrastructure` because it is an external implementation detail.

### Testing Layer
- `java/src/test/java/com/atom/Argon2PasswordEncoderAdapterTest.java`
- `java/src/test/java/com/atom/UserUseCaseTest.java`
- They validate that the hashing service behaves correctly and that the use case is secure.

### Build Layer
- `pom.xml` in the root: defines the multi-module project and the `java` module.
- `java/pom.xml`: defines dependencies, plugins, and specific configuration for the backend.

## Final Result

- The Java project compiles successfully.
- The specific hashing and authentication tests were executed successfully.
- The implementation is decoupled: the core does not know Argon2 directly.
- The system can now process passwords securely and store only hashes.

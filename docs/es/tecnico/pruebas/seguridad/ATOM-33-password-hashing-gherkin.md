# Pruebas Gherkin: Hashing de contraseñas con Argon2

## Característica

Como encargado de la seguridad de Atom,
quiero implementar un mecanismo de hashing unidireccional con sal,
para que las credenciales de los usuarios se procesen de forma segura y no existan contraseñas en texto plano en ninguna capa del sistema.

## Escenarios

### Escenario: Generar hash único e irreversible

  Dado una contraseña en texto plano "AtomSegura123!"
  Cuando el servicio de seguridad la procese
  Entonces debe devolver un hash distinto de la contraseña
  Y el hash debe poder verificarse con la misma contraseña

### Escenario: Verificar contraseña válida

  Dado un hash generado anteriormente para la contraseña "AtomSegura123!"
  Cuando el servicio compare la contraseña en texto plano "AtomSegura123!"
  Entonces el resultado debe ser verdadero

### Escenario: Verificar contraseña inválida

  Dado un hash generado anteriormente para la contraseña "AtomSegura123!"
  Cuando el servicio compare la contraseña en texto plano "OtraClave123!"
  Entonces el resultado debe ser falso

## Proceso de pruebas

1. Definir el puerto de salida `PasswordEncoderPortOut` en la capa de aplicación.
2. Implementar el adaptador `Argon2PasswordEncoderAdapter` en el paquete de infraestructura.
3. Crear pruebas unitarias para:
   - generación de hash
   - verificación de hash correcto
   - rechazo de contraseña incorrecta
4. Probar el caso de uso de registro y autenticación usando `UserUseCase`.

## Resultado esperado

- El servicio usa Argon2 con sal incorporada.
- No se almacena ninguna contraseña en texto plano.
- El algoritmo expone solo dos operaciones: `hashPassword` y `verifyPassword`.
- El Core (caso de uso) depende de un puerto, no de la implementación concreta.

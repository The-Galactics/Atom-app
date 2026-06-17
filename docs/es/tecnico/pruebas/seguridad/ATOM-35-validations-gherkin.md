# Pruebas Gherkin: Validaciones de Seguridad ATOM-35

## Característica

Como administrador de seguridad de Atom,
quiero establecer un sistema estricto de validaciones sintácticas y de control de entorno,
para que se mitiguen los riesgos de fraude y vulnerabilidades al no tener control físico sobre el dispositivo del usuario.

---

## Escenarios — Validación de Formularios

### Escenario: Campo vacío es rechazado

Dado que el usuario intenta enviar un formulario con el campo "email" vacío
Cuando el sistema procese los campos antes de enviarlos
Entonces debe retornar un error indicando que el campo "email" no puede estar vacío
Y el sistema no debe continuar con el procesamiento

### Escenario: Intento de SQL Injection es bloqueado

Dado que el campo "email" contiene el valor "' OR '1'='1"
Cuando el sistema valide la integridad del campo
Entonces debe detectar contenido potencialmente malicioso
Y debe retornar un error indicando que el campo contiene contenido peligroso
Y el sistema no debe procesar ni almacenar el valor

### Escenario: Intento de XSS es bloqueado

Dado que el campo "name" contiene el valor "<script>alert('xss')</script>"
Cuando el sistema valide la integridad del campo
Entonces debe detectar una inyección de código HTML
Y debe retornar un error indicando que el campo contiene contenido malicioso

### Escenario: Intento de Command Injection es bloqueado

Dado que el campo "phone" contiene el valor "123 && rm -rf /"
Cuando el sistema valide la integridad del campo
Entonces debe detectar caracteres de control del sistema operativo
Y debe retornar un error indicando que el campo contiene contenido peligroso

### Escenario: Formato de email inválido es rechazado

Dado que el campo "email" contiene el valor "not-an-email"
Cuando el sistema valide el formato del campo
Entonces debe retornar un error indicando que el email no tiene un formato válido
Y el código de respuesta HTTP debe ser 422

### Escenario: Formato de monto inválido es rechazado

Dado que el campo "amount" contiene el valor "abc"
Cuando el sistema valide el formato del campo en el contexto "transfer"
Entonces debe retornar un error indicando que el monto debe ser un valor numérico válido

### Escenario: Todos los campos válidos pasan la validación

Dado que el formulario contiene los campos:
| Campo  | Valor              |
| email  | user@atom.app      |
| name   | Maria Garcia       |
| amount | 2000.00            |
| phone  | +573001234567      |
Cuando el sistema valide todos los campos
Entonces el resultado debe ser exitoso
Y no debe haber errores en la respuesta

---

## Escenarios — Verificación del Entorno del Dispositivo

### Escenario: Dispositivo limpio permite operar

Dado que el dispositivo no tiene acceso root
Y no está corriendo en un emulador
Y no tiene un proxy activo interceptando tráfico
Cuando el sistema verifique el estado de seguridad del dispositivo
Entonces el nivel de riesgo debe ser LOW
Y la operación debe permitirse

### Escenario: Dispositivo rooteado bloquea operaciones críticas

Dado que el dispositivo tiene acceso root detectado
Cuando el sistema verifique el estado de seguridad
Entonces el nivel de riesgo debe ser HIGH
Y el sistema debe lanzar una excepción de seguridad
Y el código de respuesta HTTP debe ser 403
Y el detalle debe contener "ROOT_ACCESS"

### Escenario: Emulador sin root genera riesgo medio

Dado que el dispositivo está corriendo en un emulador
Y no tiene acceso root
Y no tiene proxy activo
Cuando el sistema verifique el estado de seguridad
Entonces el nivel de riesgo debe ser MEDIUM
Y la operación debe permitirse con advertencia en logs

### Escenario: Combinación de factores genera riesgo alto

Dado que el dispositivo tiene acceso root
Y está corriendo en un emulador
Y tiene un proxy activo en un puerto sospechoso
Cuando el sistema verifique el estado de seguridad
Entonces el nivel de riesgo debe ser HIGH
Y el detalle debe contener "ROOT_ACCESS", "EMULATOR_ENVIRONMENT" y "ACTIVE_PROXY"
Y el código de respuesta HTTP debe ser 403

### Escenario: Método anotado con @SecureOperation es interceptado

Dado que un método crítico está anotado con @SecureOperation
Y el dispositivo tiene acceso root detectado
Cuando se intente ejecutar el método
Entonces el aspecto AOP debe interceptar la ejecución antes de que ocurra
Y debe lanzar una excepción de seguridad sin ejecutar el método

---

## Escenarios — Validación de DTOs (Bean Validation)

### Escenario: Email inválido en UserRequestDto es rechazado por Bean Validation

Dado que el body del request contiene el campo "email" con valor "no-es-email"
Cuando el controller reciba el request con @Valid
Entonces Spring debe lanzar MethodArgumentNotValidException
Y la respuesta debe contener el error "Email must be a valid address"
Y el código HTTP debe ser 422

### Escenario: Password sin complejidad suficiente es rechazado

Dado que el body del request contiene el campo "password" con valor "simple"
Cuando el controller reciba el request con @Valid
Entonces la respuesta debe contener el error sobre complejidad de contraseña
Y el código HTTP debe ser 422

---

## Proceso de pruebas

1. Definir los puertos en `domain/port/in`: `InputValidationPort` y `DeviceSecurityPort`.
2. Definir el puerto de salida en `domain/port/out`: `DeviceInspectorPort`.
3. Implementar los casos de uso en `application/usecase`: `InputValidationUseCase` y `DeviceSecurityUseCase`.
4. Implementar el adaptador de salida `DeviceInspectorAdapter` en `infrastructure/adapter/out/device`.
5. Exponer los endpoints en `SecurityController` con `POST /api/v1/security/validate-input` y `GET /api/v1/security/device-check`.
6. Registrar la anotación `@SecureOperation` y el aspecto `DeviceSecurityAspect`.
7. Centralizar el manejo de errores en `SecurityExceptionHandler` con `ProblemDetail` (RFC 7807).
8. Crear pruebas unitarias para:
    - Detección de campos vacíos
    - Detección de SQL Injection, XSS y Command Injection
    - Validación de formatos por tipo de campo
    - Evaluación del nivel de riesgo del dispositivo
    - Bloqueo de operaciones en dispositivos HIGH risk
    - No bloqueo en dispositivos LOW/MEDIUM risk

## Resultado esperado

- Toda entrada de usuario es validada localmente antes de procesarse.
- Los intentos de inyección (SQL, XSS, Command) son bloqueados con HTTP 422.
- El sistema detecta root, emuladores y proxies sospechosos y asigna un nivel de riesgo.
- Los dispositivos HIGH risk reciben HTTP 403 con detalle del factor de riesgo detectado.
- Cualquier método crítico puede protegerse con `@SecureOperation` sin repetir lógica.
- Todos los errores siguen el estándar RFC 7807 (ProblemDetail).
- El dominio no conoce HTTP, Spring, ni Android; permanece completamente aislado.

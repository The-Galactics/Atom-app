# Gherkin Tests: ATOM-35 Security Validations

## Feature

As the security administrator of Atom,
I want to establish a strict system of syntactic validations and environment control,
in order to mitigate fraud risks and vulnerabilities due to not having physical control over the user's device.

---

## Scenarios — Form Validation

### Scenario: Empty field is rejected

Given that the user attempts to submit a form with the "email" field empty
When the system processes the fields before sending them
Then it must return an error indicating that the "email" field cannot be empty
And the system must not continue with the processing

### Scenario: SQL Injection attempt is blocked

Given that the "email" field contains the value "' OR '1'='1"
When the system validates the integrity of the field
Then it must detect potentially malicious content
And it must return an error indicating that the field contains dangerous content
And the system must not process or store the value

### Scenario: XSS attempt is blocked

Given that the "name" field contains the value "<script>alert('xss')</script>"
When the system validates the integrity of the field
Then it must detect an HTML code injection
And it must return an error indicating that the field contains malicious content

### Scenario: Command Injection attempt is blocked

Given that the "phone" field contains the value "123 && rm -rf /"
When the system validates the integrity of the field
Then it must detect operating system control characters
And it must return an error indicating that the field contains dangerous content

### Scenario: Invalid email format is rejected

Given that the "email" field contains the value "not-an-email"
When the system validates the format of the field
Then it must return an error indicating that the email does not have a valid format
And the HTTP response code must be 422

### Scenario: Invalid amount format is rejected

Given that the "amount" field contains the value "abc"
When the system validates the format of the field in the "transfer" context
Then it must return an error indicating that the amount must be a valid numerical value

### Scenario: All valid fields pass validation

Given that the form contains the fields:
| Field  | Value              |
| email  | user@atom.app      |
| name   | Maria Garcia       |
| amount | 2000.00            |
| phone  | +573001234567      |
When the system validates all fields
Then the result must be successful
And there must be no errors in the response

---

## Scenarios — Device Environment Verification

### Scenario: Clean device allows operation

Given that the device does not have root access
And it is not running on an emulator
And it does not have an active proxy intercepting traffic
When the system verifies the security status of the device
Then the risk level must be LOW
And the operation must be allowed

### Scenario: Rooted device blocks critical operations

Given that the device has root access detected
When the system verifies the security status
Then the risk level must be HIGH
And the system must throw a security exception
And the HTTP response code must be 403
And the detail must contain "ROOT_ACCESS"

### Scenario: Emulator without root generates medium risk

Given that the device is running on an emulator
And it does not have root access
And it does not have an active proxy
When the system verifies the security status
Then the risk level must be MEDIUM
And the operation must be allowed with a warning in logs

### Scenario: Combination of factors generates high risk

Given that the device has root access
And it is running on an emulator
And it has an active proxy on a suspicious port
When the system verifies the security status
Then the risk level must be HIGH
And the detail must contain "ROOT_ACCESS", "EMULATOR_ENVIRONMENT", and "ACTIVE_PROXY"
And the HTTP response code must be 403

### Scenario: Method annotated with @SecureOperation is intercepted

Given that a critical method is annotated with @SecureOperation
And the device has root access detected
When an attempt is made to execute the method
Then the AOP aspect must intercept the execution before it occurs
And it must throw a security exception without executing the method

---

## Scenarios — DTO Validation (Bean Validation)

### Scenario: Invalid email in UserRequestDto is rejected by Bean Validation

Given that the request body contains the "email" field with the value "no-es-email"
When the controller receives the request with @Valid
Then Spring must throw MethodArgumentNotValidException
And the response must contain the error "Email must be a valid address"
And the HTTP code must be 422

### Scenario: Password without sufficient complexity is rejected

Given that the request body contains the "password" field with the value "simple"
When the controller receives the request with @Valid
Then the response must contain the error regarding password complexity
And the HTTP code must be 422

## Testing Process

1. Define the ports in `domain/port/in`: `InputValidationPort` and `DeviceSecurityPort`.
2. Define the output port in `domain/port/out`: `DeviceInspectorPort`.
3. Implement the use cases in `application/usecase`: `InputValidationUseCase` and `DeviceSecurityUseCase`.
4. Implement the output adapter `DeviceInspectorAdapter` in `infrastructure/adapter/out/device`.
5. Expose the endpoints in `SecurityController` with `POST /api/v1/security/validate-input` and `GET /api/v1/security/device-check`.
6. Register the `@SecureOperation` annotation and the `DeviceSecurityAspect` aspect.
7. Centralize error handling in `SecurityExceptionHandler` using `ProblemDetail` (RFC 7807).
8. Create unit tests for:
   - Empty field detection
   - SQL Injection, XSS, and Command Injection detection
   - Format validation by field type
   - Device security risk level evaluation
   - Operation blocking on HIGH risk devices
   - No blocking on LOW/MEDIUM risk devices

## Expected Result

- All user input is validated locally before processing.
- Injection attempts (SQL, XSS, Command) are blocked with HTTP 422.
- The system detects root, emulators, and suspicious proxies, and assigns a risk level.
- HIGH risk devices receive HTTP 403 with details of the detected risk factor.
- Any critical method can be protected with `@SecureOperation` without repeating logic.
- All errors follow the RFC 7807 standard (ProblemDetail).
- The domain does not know HTTP, Spring, or Android; it remains completely isolated.

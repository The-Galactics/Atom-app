# Gherkin Tests: Password Hashing with Argon2

## Feature

As the person in charge of Atom's security,
I want to implement a one-way hashing mechanism with salt,
so that user credentials are processed securely and no plain text passwords exist in any layer of the system.

## Scenarios

### Scenario: Generate a unique and irreversible hash

Given a plain text password "AtomSegura123!"
When the security service processes it
Then it must return a hash different from the password
And the hash must be verifiable with the same password

### Scenario: Verify valid password

Given a hash previously generated for the password "AtomSegura123!"
When the service compares the plain text password "AtomSegura123!"
Then the result must be true

### Scenario: Verify invalid password

Given a hash previously generated for the password "AtomSegura123!"
When the service compares the plain text password "OtraClave123!"
Then the result must be false

## Testing Process

1. Define the output port `PasswordEncoderPortOut` in the application layer.
2. Implement the adapter `Argon2PasswordEncoderAdapter` in the infrastructure package.
3. Create unit tests for:
    - hash generation
    - correct hash verification
    - rejection of incorrect password
4. Test the registration and authentication use case using `UserUseCase`.

## Expected Result

- The service uses Argon2 with built-in salt.
- No plain text passwords are stored.
- The algorithm exposes only two operations: `hashPassword` and `verifyPassword`.
- The Core (use case) depends on a port, not on the concrete implementation.

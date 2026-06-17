package com.atom.hashSecurity;

import com.atom.application.port.out.security.PasswordEncoderPortOut;
import com.atom.application.port.out.UserPortOut;
import com.atom.application.usecase.UserUseCase;
import com.atom.domain.model.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class UserUseCaseTest {

    @Test
    void shouldRegisterUserWithHashedPasswordAndAuthenticate() {
        AtomicReference<User> savedUser = new AtomicReference<>();
        UserPortOut stubUserPort = new UserPortOut() {
            @Override
            public User save(User user) {
                savedUser.set(user);
                return user;
            }

            @Override
            public Optional<User> findById(String userId) {
                return Optional.empty();
            }

            @Override
            public Optional<User> findByEmail(String email) {
                return Optional.ofNullable(savedUser.get());
            }

            @Override
            public void delete(User user) {
                // no-op
            }
        };

        // Minimal mock for PasswordEncoderPortOut since Argon2 is excluded
        PasswordEncoderPortOut encoder = new PasswordEncoderPortOut() {
            @Override
            public String hashPassword(String rawPassword) {
                return "hashed_" + rawPassword;
            }

            @Override
            public boolean verifyPassword(String rawPassword, String encodedPassword) {
                return ("hashed_" + rawPassword).equals(encodedPassword);
            }
        };

        UserUseCase useCase = new UserUseCase(stubUserPort, encoder);

        User registered = useCase.registerUser("test@atom.com", "Atom Test", "P@ssword123");

        assertNotNull(registered);
        assertNotEquals("P@ssword123", registered.getPassword());
        assertTrue(encoder.verifyPassword("P@ssword123", registered.getPassword()));
        assertTrue(useCase.authenticateUser("test@atom.com", "P@ssword123"));
        assertFalse(useCase.authenticateUser("test@atom.com", "PasswordIncorrecto"));
    }
}

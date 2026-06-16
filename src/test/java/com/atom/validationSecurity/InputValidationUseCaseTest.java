package com.atom.validationSecurity;

import com.atom.application.exception.InputValidationException;
import com.atom.application.usecase.security.InputValidationUsecase;
import com.atom.domain.model.security.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios para InputValidationUseCase.
 * No requieren Spring Context — prueba pura del caso de uso.
 */
class InputValidationUseCaseTest {

    private InputValidationUsecase useCase;

    @BeforeEach
    void setUp() {
        useCase = new InputValidationUsecase();
    }

    // ── Campos vacíos ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Empty field detection")
    class EmptyFields {

        @Test
        @DisplayName("Should throw when a field is empty")
        void shouldFailOnEmptyField() {
            var fields = Map.of("email", "");

            assertThatThrownBy(() -> useCase.validateFormFields(fields, "login"))
                    .isInstanceOf(InputValidationException.class)
                    .satisfies(ex -> {
                        var errors = ((InputValidationException) ex).getValidationErrors();
                        assertThat(errors).anyMatch(e -> e.contains("must not be empty"));
                    });
        }

        @Test
        @DisplayName("Should throw when a field is only whitespace")
        void shouldFailOnBlankField() {
            var fields = Map.of("name", "   ");

            assertThatThrownBy(() -> useCase.validateFormFields(fields, "register"))
                    .isInstanceOf(InputValidationException.class);
        }
    }

    // ── Detección de inyecciones ───────────────────────────────────────────────

    @Nested
    @DisplayName("Injection detection")
    class InjectionDetection {

        @Test
        @DisplayName("Should detect SQL Injection attempt")
        void shouldDetectSqlInjection() {
            var fields = Map.of("email", "' OR '1'='1");

            assertThatThrownBy(() -> useCase.validateFormFields(fields, "login"))
                    .isInstanceOf(InputValidationException.class)
                    .satisfies(ex -> {
                        var errors = ((InputValidationException) ex).getValidationErrors();
                        assertThat(errors).anyMatch(e -> e.contains("malicious content"));
                    });
        }

        @Test
        @DisplayName("Should detect XSS attempt")
        void shouldDetectXss() {
            var fields = Map.of("name", "<script>alert('xss')</script>");

            assertThatThrownBy(() -> useCase.validateFormFields(fields, "register"))
                    .isInstanceOf(InputValidationException.class);
        }

        @Test
        @DisplayName("Should detect Command Injection attempt")
        void shouldDetectCommandInjection() {
            var fields = Map.of("phone", "123 && rm -rf /");

            assertThatThrownBy(() -> useCase.validateFormFields(fields, "transfer"))
                    .isInstanceOf(InputValidationException.class);
        }

        @Test
        @DisplayName("Should detect SQL DROP TABLE keyword")
        void shouldDetectDropTable() {
            var fields = Map.of("name", "Robert'); DROP TABLE users;--");

            assertThatThrownBy(() -> useCase.validateFormFields(fields, "register"))
                    .isInstanceOf(InputValidationException.class);
        }
    }

    // ── Validación de formato ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Format validation")
    class FormatValidation {

        @Test
        @DisplayName("Should reject invalid email format")
        void shouldRejectInvalidEmail() {
            var fields = Map.of("email", "not-an-email");

            assertThatThrownBy(() -> useCase.validateFormFields(fields, "login"))
                    .isInstanceOf(InputValidationException.class)
                    .satisfies(ex -> {
                        var errors = ((InputValidationException) ex).getValidationErrors();
                        assertThat(errors).anyMatch(e -> e.contains("valid email"));
                    });
        }

        @Test
        @DisplayName("Should reject invalid amount format")
        void shouldRejectInvalidAmount() {
            var fields = Map.of("amount", "abc");

            assertThatThrownBy(() -> useCase.validateFormFields(fields, "transfer"))
                    .isInstanceOf(InputValidationException.class);
        }

        @Test
        @DisplayName("Should accept valid amount format")
        void shouldAcceptValidAmount() {
            var fields = Map.of("amount", "1500.50");

            ValidationResult result = useCase.validateFormFields(fields, "transfer");

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("Should accept valid email format")
        void shouldAcceptValidEmail() {
            var fields = Map.of("email", "user@atom.app");

            ValidationResult result = useCase.validateFormFields(fields, "login");

            assertThat(result.valid()).isTrue();
        }
    }

    // ── Caso exitoso ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should pass with all valid fields")
    void shouldPassWithAllValidFields() {
        var fields = Map.of(
                "email",  "test@atom.app",
                "name",   "Maria Garcia",
                "amount", "2000.00",
                "phone",  "+573001234567"
        );

        ValidationResult result = useCase.validateFormFields(fields, "transfer");

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }
}

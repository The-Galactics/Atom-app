package com.atom.domain.model.security;

import java.util.Collections;
import java.util.List;

public record ValidationResult(
        boolean valid,
        List<String> errors
) {

    public static ValidationResult success() {
        return new ValidationResult(true, Collections.emptyList());
    }

    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, Collections.unmodifiableList(errors));
    }

    public static ValidationResult failure(String error) {
        return new ValidationResult(false, List.of(error));
    }
}

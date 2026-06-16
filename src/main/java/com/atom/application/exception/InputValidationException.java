package com.atom.application.exception;

import java.util.Collections;
import java.util.List;

public class InputValidationException extends RuntimeException{
    private final List<String> validationErrors;

    public InputValidationException(List<String> errors) {
        super("Input validation failed: " + errors);
        this.validationErrors = List.copyOf(errors);

    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

}

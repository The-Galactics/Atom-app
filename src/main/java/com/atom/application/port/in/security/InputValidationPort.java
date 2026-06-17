package com.atom.application.port.in.security;

import com.atom.domain.model.security.ValidationResult;

import java.util.Map;

public interface InputValidationPort {
    ValidationResult validateFormFields(Map<String, String> fields, String context);

}

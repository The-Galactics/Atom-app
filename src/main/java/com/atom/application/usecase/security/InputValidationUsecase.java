package com.atom.application.usecase.security;

import com.atom.application.exception.InputValidationException;
import com.atom.application.port.in.security.InputValidationPort;
import com.atom.domain.model.security.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class InputValidationUsecase implements InputValidationPort {

    private static final Pattern SQL_INJECTION = Pattern.compile(
            "(?i)(--|;|'|\"|\\b(SELECT|INSERT|UPDATE|DELETE|DROP|UNION|ALTER|EXEC|CAST|CONVERT|CHAR|DECLARE|CURSOR)\\b)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern XSS = Pattern.compile(
            "(?i)(<script|</script|<img|onerror=|onload=|javascript:|data:text/html)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CMD_INJECTION = Pattern.compile(
            "[&|`$;\\\\]|\\.\\./"
    );

    //Patterns for type fields

    private static final Pattern EMAIL_FORMAT    = Pattern.compile("^[\\w.+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern AMOUNT_FORMAT   = Pattern.compile("^\\d{1,12}(\\.\\d{1,2})?$");
    private static final Pattern PHONE_FORMAT    = Pattern.compile("^\\+?[0-9]{7,15}$");
    private static final Pattern SAFE_NAME       = Pattern.compile("^[\\p{L}\\s'\\-]{2,80}$");

    @Override
    public ValidationResult validateFormFields(Map<String, String> fields, String context) {
        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String fieldName  = entry.getKey();
            String fieldValue = entry.getValue();

            if (fieldValue == null || fieldValue.isBlank()) {
                errors.add("Field '%s' must not be empty.".formatted(fieldName));
                continue;
            }


            if (containsInjection(fieldValue)) {
                errors.add("Field '%s' contains potentially malicious content.".formatted(fieldName));
                continue;
            }


            validateFieldFormat(fieldName, fieldValue, context, errors);
        }

        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }

        return ValidationResult.success();
    }
    private boolean containsInjection(String value) {
        return SQL_INJECTION.matcher(value).find()
                || XSS.matcher(value).find()
                || CMD_INJECTION.matcher(value).find();
    }

    private void validateFieldFormat(String fieldName, String value, String context, List<String> errors) {
        String lowerField = fieldName.toLowerCase();

        if (lowerField.contains("email")) {
            if (!EMAIL_FORMAT.matcher(value).matches()) {
                errors.add("Field '%s' is not a valid email address.".formatted(fieldName));
            }
        } else if (lowerField.contains("amount") || lowerField.contains("monto")) {
            if (!AMOUNT_FORMAT.matcher(value).matches()) {
                errors.add("Field '%s' must be a valid numeric amount (e.g. 1500.00).".formatted(fieldName));
            }
        } else if (lowerField.contains("phone") || lowerField.contains("telefono")) {
            if (!PHONE_FORMAT.matcher(value).matches()) {
                errors.add("Field '%s' is not a valid phone number.".formatted(fieldName));
            }
        } else if (lowerField.contains("name") || lowerField.contains("nombre")) {
            if (!SAFE_NAME.matcher(value).matches()) {
                errors.add("Field '%s' contains invalid characters for a name.".formatted(fieldName));
            }
        }
    }

}

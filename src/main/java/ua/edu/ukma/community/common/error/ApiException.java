package ua.edu.ukma.community.common.error;

import java.util.List;


public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldViolation> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<FieldViolation> details) {
        super(message);
        this.code = code;
        this.details = List.copyOf(details);
    }

    public static ApiException validation(String field, String message, Object providedValue) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "Запит містить невалідні поля",
                List.of(new FieldViolation(field, message, providedValue)));
    }

    public static ApiException invalidParameter(String param, String message, Object providedValue) {
        return new ApiException(ErrorCode.INVALID_PARAMETER, "Невалідний параметр запиту",
                List.of(new FieldViolation(param, message, providedValue)));
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldViolation> details() {
        return details;
    }
}

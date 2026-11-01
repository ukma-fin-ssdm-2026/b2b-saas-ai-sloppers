package ua.edu.ukma.community.common.error;

import org.springframework.http.HttpStatus;

import java.util.Locale;


public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    ACCOUNT_BLOCKED(HttpStatus.FORBIDDEN),
    ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    HEAD_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    ACCOUNT_ALREADY_VERIFIED(HttpStatus.CONFLICT),
    ALREADY_HEAD(HttpStatus.CONFLICT),
    LAST_HEAD(HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    MAIL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public String anchor() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}

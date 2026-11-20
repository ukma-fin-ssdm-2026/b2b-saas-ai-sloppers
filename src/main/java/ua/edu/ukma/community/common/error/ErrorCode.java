package ua.edu.ukma.community.common.error;

import org.springframework.http.HttpStatus;

import java.util.Locale;


public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    /** автофільтр знайшов заборонене слово (FR-058) */
    CONTENT_REJECTED(HttpStatus.BAD_REQUEST),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    ACCOUNT_BLOCKED(HttpStatus.FORBIDDEN),
    OWN_ADVERT(HttpStatus.FORBIDDEN),
    ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    ADVERT_NOT_FOUND(HttpStatus.NOT_FOUND),
    REPLY_NOT_FOUND(HttpStatus.NOT_FOUND),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    HEAD_NOT_FOUND(HttpStatus.NOT_FOUND),
    COMPLAINT_NOT_FOUND(HttpStatus.NOT_FOUND),
    BANNED_WORD_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    ACCOUNT_ALREADY_VERIFIED(HttpStatus.CONFLICT),
    ALREADY_HEAD(HttpStatus.CONFLICT),
    LAST_HEAD(HttpStatus.CONFLICT),
    ALREADY_REPLIED(HttpStatus.CONFLICT),
    ADVERT_CLOSED(HttpStatus.CONFLICT),
    ALREADY_COMPLAINED(HttpStatus.CONFLICT),
    ALREADY_BANNED(HttpStatus.CONFLICT),
    NOT_BANNED(HttpStatus.CONFLICT),
    BANNED_WORD_EXISTS(HttpStatus.CONFLICT),
    /** учасник уже оцінив автора за це оголошення (FR-061) */
    ALREADY_RATED(HttpStatus.CONFLICT),
    /** оцінити автора можна лише після закриття оголошення (FR-061) */
    ADVERT_NOT_CLOSED(HttpStatus.CONFLICT),
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

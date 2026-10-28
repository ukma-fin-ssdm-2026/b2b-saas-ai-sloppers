package ua.edu.ukma.community.common.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String DOCS_BASE_URL = "https://ukma-fin-ssdm-2026.github.io/b2b-saas-ai-sloppers/api/errors";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return build(ex.code(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldViolation> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                .collect(Collectors.toCollection(ArrayList::new));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> details.add(new FieldViolation(ge.getObjectName(), ge.getDefaultMessage(), null)));
        return build(ErrorCode.VALIDATION_ERROR, "Запит містить невалідні поля", details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException ex) {
        List<FieldViolation> details = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                errors.getFieldErrors().forEach(fe ->
                        details.add(new FieldViolation(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue())));
            } else {
                String name = parameterName(result.getMethodParameter());
                result.getResolvableErrors().forEach(err ->
                        details.add(new FieldViolation(name, err.getDefaultMessage(), result.getArgument())));
            }
        }
        return build(ErrorCode.VALIDATION_ERROR, "Запит містить невалідні параметри", details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() == null ? "інший тип" : ex.getRequiredType().getSimpleName();
        return build(ErrorCode.INVALID_PARAMETER, "Невалідний параметр запиту",
                List.of(new FieldViolation(ex.getName(), "Очікується значення типу " + expected, ex.getValue())));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(ErrorCode.INVALID_PARAMETER, "Бракує обов'язкового параметра",
                List.of(new FieldViolation(ex.getParameterName(), "Параметр обов'язковий", null)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            String field = mie.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(n -> n != null)
                    .collect(Collectors.joining("."));
            return build(ErrorCode.MALFORMED_REQUEST, "Тіло запиту не відповідає схемі",
                    List.of(new FieldViolation(field, "Неправильний тип або формат значення", null)));
        }
        return build(ErrorCode.MALFORMED_REQUEST, "Тіло запиту відсутнє або не є коректним JSON", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNoRoute(NoResourceFoundException ex) {
        return build(ErrorCode.ROUTE_NOT_FOUND, "Кінцевої точки " + ex.getResourcePath() + " не існує", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, "Метод " + ex.getMethod() + " не підтримується для цього ресурсу", List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.PAYLOAD_TOO_LARGE, "Файл завеликий: не більше 5 МБ", List.of());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return build(ErrorCode.INVALID_PARAMETER, "Бракує файлу в запиті",
                List.of(new FieldViolation(ex.getRequestPartName(), "Частина multipart/form-data обов'язкова", null)));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Тип вмісту " + ex.getContentType() + " не підтримується для цього ресурсу", List.of());
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex) {
        return build(ErrorCode.MALFORMED_REQUEST, "Запит має бути multipart/form-data з файлом у полі file", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, "Внутрішня помилка сервера. Повідомте request_id підтримці", List.of());
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, List<FieldViolation> details) {
        return ResponseEntity.status(code.status()).body(body(code, message, details));
    }

    /**
     * Тіло помилки в єдиному форматі API. Публічне, бо його також пишуть обробники Spring Security (401/403),
     * які спрацьовують ще до контролерів.
     */
    public static ErrorResponse body(ErrorCode code, String message, List<FieldViolation> details) {
        // Якщо в команді є фільтр кореляційного ідентифікатора, він кладе requestId у MDC
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        ApiError error = new ApiError(code, message, details, requestId, DOCS_BASE_URL + "#" + code.anchor());
        return new ErrorResponse(error);
    }

    private static String parameterName(MethodParameter p) {
        RequestParam rp = p.getParameterAnnotation(RequestParam.class);
        if (rp != null && !rp.name().isBlank()) return rp.name();
        PathVariable pv = p.getParameterAnnotation(PathVariable.class);
        if (pv != null && !pv.name().isBlank()) return pv.name();
        return p.getParameterName();
    }
}

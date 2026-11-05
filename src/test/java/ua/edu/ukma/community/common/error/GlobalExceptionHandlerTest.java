package ua.edu.ukma.community.common.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Характеризаційні тести {@link GlobalExceptionHandler} перед рефакторингом Лаб 8.
 *
 * <p>Задача цих тестів — не перевірити, що обробник «правильний», а зафіксувати його поведінку
 * такою, яка вона є зараз, разом із дивними місцями. Вони мають бути зеленими до рефакторингу
 * і після нього; якщо хоч один упаде, рефакторинг змінив зовнішню поведінку API помилок.
 *
 * <p>Клас до Лаб 8 власних тестів не мав: покриття рядків 28.3%
 * (див. docs/refactoring/regression-baseline.md).
 */
@DisplayName("GlobalExceptionHandler: зафіксована поведінка до рефакторингу")
class GlobalExceptionHandlerTest {

    private static final String DOCS = "https://ukma-fin-ssdm-2026.github.io/b2b-saas-ai-sloppers/api/errors";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private static ApiError errorOf(ResponseEntity<ErrorResponse> response) {
        assertThat(response.getBody()).isNotNull();
        return response.getBody().error();
    }

    @Nested
    @DisplayName("тіло помилки (body) — спільний конверт для контролерів і Spring Security")
    class Envelope {

        @Test
        @DisplayName("requestId береться з MDC, якщо фільтр його поклав")
        void requestIdComesFromMdc() {
            MDC.put("requestId", "req_from_filter");

            ApiError error = GlobalExceptionHandler.body(ErrorCode.FORBIDDEN, "нема прав", List.of()).error();

            assertThat(error.requestId()).isEqualTo("req_from_filter");
        }

        @Test
        @DisplayName("без MDC requestId генерується як req_ плюс 16 шістнадцяткових символів")
        void requestIdIsGeneratedWhenMdcEmpty() {
            ApiError error = GlobalExceptionHandler.body(ErrorCode.INTERNAL_ERROR, "ой", List.of()).error();

            // саме цей формат фіксує магічне число 16 у body(): рефакторинг не має його змінити
            assertThat(error.requestId()).matches("^req_[0-9a-f]{16}$");
            assertThat(error.requestId()).hasSize(20);
        }

        @Test
        @DisplayName("кожен виклик без MDC дає новий requestId")
        void generatedRequestIdDiffersBetweenCalls() {
            String first = GlobalExceptionHandler.body(ErrorCode.INTERNAL_ERROR, "ой", List.of()).error().requestId();
            String second = GlobalExceptionHandler.body(ErrorCode.INTERNAL_ERROR, "ой", List.of()).error().requestId();

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("documentation — базовий URL плюс якір коду помилки")
        void documentationPointsAtErrorAnchor() {
            ApiError error = GlobalExceptionHandler
                    .body(ErrorCode.SLUG_ALREADY_EXISTS, "зайнято", List.of()).error();

            assertThat(error.documentation()).isEqualTo(DOCS + "#slug-already-exists");
        }

        @Test
        @DisplayName("код, повідомлення й деталі проходять наскрізь")
        void codeMessageAndDetailsPassThrough() {
            FieldViolation violation = new FieldViolation("slug", "зайнятий", "debate-club");

            ApiError error = GlobalExceptionHandler
                    .body(ErrorCode.SLUG_ALREADY_EXISTS, "зайнято", List.of(violation)).error();

            assertThat(error.code()).isEqualTo(ErrorCode.SLUG_ALREADY_EXISTS);
            assertThat(error.message()).isEqualTo("зайнято");
            assertThat(error.details()).containsExactly(violation);
        }
    }

    @Nested
    @DisplayName("ApiException — власні помилки домену")
    class DomainErrors {

        @Test
        @DisplayName("статус, код, повідомлення й деталі беруться з винятку")
        void apiExceptionIsMappedAsIs() {
            ApiException ex = ApiException.validation("name", "3–200 символів", "ab");

            ResponseEntity<ErrorResponse> response = handler.handleApi(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ApiError error = errorOf(response);
            assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
            assertThat(error.message()).isEqualTo("Запит містить невалідні поля");
            assertThat(error.details()).containsExactly(new FieldViolation("name", "3–200 символів", "ab"));
        }

        @Test
        @DisplayName("статус відповідає коду помилки, а не завжди 400")
        void statusFollowsErrorCode() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleApi(new ApiException(ErrorCode.LAST_HEAD, "останнього голову зняти не можна"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("валідація тіла запиту")
    class BodyValidation {

        @Test
        @DisplayName("помилки полів і помилки рівня обʼєкта потрапляють в details, поля — першими")
        void fieldErrorsComeBeforeGlobalErrors() throws Exception {
            BindingResult binding = new BeanPropertyBindingResult(new Form(), "organization");
            binding.rejectValue("name", "Size", "3–200 символів");
            binding.reject("HeadRequired", "організація без голови не створюється");

            ResponseEntity<ErrorResponse> response = handler.handleBodyValidation(
                    new MethodArgumentNotValidException(methodParameter(0), binding));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ApiError error = errorOf(response);
            assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
            assertThat(error.message()).isEqualTo("Запит містить невалідні поля");
            assertThat(error.details()).hasSize(2);
            // помилка поля: назва поля і відкинуте значення
            assertThat(error.details().getFirst())
                    .isEqualTo(new FieldViolation("name", "3–200 символів", "ab"));
            // помилка рівня обʼєкта підставляє назву обʼєкта у поле field і завжди null як значення
            assertThat(error.details().get(1))
                    .isEqualTo(new FieldViolation("organization", "організація без голови не створюється", null));
        }
    }

    @Nested
    @DisplayName("валідація параметрів і назва параметра")
    class ParamValidation {

        @Test
        @DisplayName("звичайне порушення: назва береться з @RequestParam, значення — з аргумента")
        void requestParamNameIsUsed() throws Exception {
            MethodParameter parameter = methodParameter(0);
            List<MessageSourceResolvable> errors = List.of(resolvable("limit має бути <= 100"));
            ParameterValidationResult result = mock(ParameterValidationResult.class);
            when(result.getMethodParameter()).thenReturn(parameter);
            when(result.getArgument()).thenReturn(101);
            when(result.getResolvableErrors()).thenReturn(errors);

            List<ParameterValidationResult> results = List.of(result);
            HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
            when(ex.getParameterValidationResults()).thenReturn(results);

            ResponseEntity<ErrorResponse> response = handler.handleParamValidation(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ApiError error = errorOf(response);
            assertThat(error.message()).isEqualTo("Запит містить невалідні параметри");
            assertThat(error.details())
                    .containsExactly(new FieldViolation("q", "limit має бути <= 100", 101));
        }

        @Test
        @DisplayName("ParameterErrors: назви беруться з помилок полів, а не з параметра")
        void parameterErrorsUseFieldNames() {
            BindingResult binding = new BeanPropertyBindingResult(new Form(), "filter");
            binding.rejectValue("name", "Size", "не те");

            List<org.springframework.validation.FieldError> fieldErrors = binding.getFieldErrors();
            ParameterErrors errors = mock(ParameterErrors.class);
            when(errors.getFieldErrors()).thenReturn(fieldErrors);

            List<ParameterValidationResult> results = List.of(errors);
            HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
            when(ex.getParameterValidationResults()).thenReturn(results);

            ApiError error = errorOf(handler.handleParamValidation(ex));

            assertThat(error.details())
                    .containsExactly(new FieldViolation("name", "не те", "ab"));
        }

        @Test
        @DisplayName("@PathVariable: назва береться з анотації шляху")
        void pathVariableNameIsUsed() throws Exception {
            MethodParameter parameter = methodParameter(2);
            List<MessageSourceResolvable> errors = List.of(resolvable("очікується UUID"));
            ParameterValidationResult result = mock(ParameterValidationResult.class);
            when(result.getMethodParameter()).thenReturn(parameter);
            when(result.getArgument()).thenReturn("not-a-uuid");
            when(result.getResolvableErrors()).thenReturn(errors);

            List<ParameterValidationResult> results = List.of(result);
            HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
            when(ex.getParameterValidationResults()).thenReturn(results);

            ApiError error = errorOf(handler.handleParamValidation(ex));

            assertThat(error.details().getFirst().field()).isEqualTo("orgId");
        }

        @Test
        @DisplayName("@RequestParam без імені не вважається назвою: обробник іде далі по ланцюжку")
        void blankRequestParamNameFallsThrough() throws Exception {
            // параметр оголошений як @RequestParam String unnamed, тобто rp.name() порожній;
            // саме цю гілку перевірка !isBlank() і відкидає
            assertThat(fallbackFieldName(methodParameter(1), true)).isEqualTo("unnamed");
        }

        @Test
        @DisplayName("без іменованої анотації назва залежить від того, чи ввімкнено пошук імен параметрів")
        void fallbackNameDependsOnParameterNameDiscovery() throws Exception {
            // навмисно зафіксовано як є: без initParameterNameDiscovery Spring не знає імені,
            // і в details потрапляє null. У застосунку пошук імен ініціалізує сам Spring.
            assertThat(fallbackFieldName(methodParameter(3), false)).isNull();
            assertThat(fallbackFieldName(methodParameter(3), true)).isEqualTo("plain");
        }

        private String fallbackFieldName(MethodParameter parameter, boolean withDiscovery) {
            if (withDiscovery) {
                parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
            }
            List<MessageSourceResolvable> errors = List.of(resolvable("щось не так"));
            ParameterValidationResult result = mock(ParameterValidationResult.class);
            when(result.getMethodParameter()).thenReturn(parameter);
            when(result.getResolvableErrors()).thenReturn(errors);

            List<ParameterValidationResult> results = List.of(result);
            HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
            when(ex.getParameterValidationResults()).thenReturn(results);

            return errorOf(handler.handleParamValidation(ex)).details().getFirst().field();
        }
    }

    @Nested
    @DisplayName("невалідні параметри запиту")
    class RequestParameters {

        @Test
        @DisplayName("невідповідність типу: у повідомленні проста назва очікуваного типу")
        void typeMismatchNamesExpectedType() {
            MethodArgumentTypeMismatchException ex =
                    new MethodArgumentTypeMismatchException("abc", Integer.class, "limit", null, null);

            ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ApiError error = errorOf(response);
            assertThat(error.code()).isEqualTo(ErrorCode.INVALID_PARAMETER);
            assertThat(error.details())
                    .containsExactly(new FieldViolation("limit", "Очікується значення типу Integer", "abc"));
        }

        @Test
        @DisplayName("невідомий очікуваний тип дає формулювання «інший тип»")
        void typeMismatchWithoutRequiredType() {
            MethodArgumentTypeMismatchException ex =
                    new MethodArgumentTypeMismatchException("abc", null, "limit", null, null);

            ApiError error = errorOf(handler.handleTypeMismatch(ex));

            assertThat(error.details().getFirst().message()).isEqualTo("Очікується значення типу інший тип");
        }

        @Test
        @DisplayName("бракує обовʼязкового параметра")
        void missingParameter() {
            ApiError error = errorOf(handler.handleMissingParam(
                    new MissingServletRequestParameterException("organizationId", "UUID")));

            assertThat(error.code()).isEqualTo(ErrorCode.INVALID_PARAMETER);
            assertThat(error.message()).isEqualTo("Бракує обов'язкового параметра");
            assertThat(error.details())
                    .containsExactly(new FieldViolation("organizationId", "Параметр обов'язковий", null));
        }
    }

    @Nested
    @DisplayName("нечитабельне тіло запиту")
    class UnreadableBody {

        @Test
        @DisplayName("відомий шлях у JSON: поля шляху зʼєднуються точкою")
        void mismatchedInputJoinsPathWithDots() {
            HttpMessageNotReadableException ex = unreadableCausedBy(mismatchedInput("startsAt"));

            ResponseEntity<ErrorResponse> response = handler.handleUnreadable(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ApiError error = errorOf(response);
            assertThat(error.code()).isEqualTo(ErrorCode.MALFORMED_REQUEST);
            assertThat(error.message()).isEqualTo("Тіло запиту не відповідає схемі");
            assertThat(error.details()).containsExactly(
                    new FieldViolation("startsAt", "Неправильний тип або формат значення", null));
        }

        @Test
        @DisplayName("елемент масиву без назви поля дає порожню назву, а не null")
        void anonymousPathElementYieldsEmptyFieldName() {
            List<JsonMappingException.Reference> path =
                    List.of(new JsonMappingException.Reference(List.of(), 0));
            MismatchedInputException cause = mock(MismatchedInputException.class);
            when(cause.getPath()).thenReturn(path);

            ApiError error = errorOf(handler.handleUnreadable(unreadableCausedBy(cause)));

            // навмисно зафіксовано як є: фільтр відкидає null, і collect(joining) дає ""
            assertThat(error.details().getFirst().field()).isEmpty();
        }

        @Test
        @DisplayName("без MismatchedInputException — загальне повідомлення без деталей")
        void genericMalformedBody() {
            ApiError error = errorOf(handler.handleUnreadable(unreadableCausedBy(new RuntimeException("зламано"))));

            assertThat(error.code()).isEqualTo(ErrorCode.MALFORMED_REQUEST);
            assertThat(error.message()).isEqualTo("Тіло запиту відсутнє або не є коректним JSON");
            assertThat(error.details()).isEmpty();
        }

        private static MismatchedInputException mismatchedInput(String field) {
            List<JsonMappingException.Reference> path =
                    List.of(new JsonMappingException.Reference(List.of(), field));
            MismatchedInputException cause = mock(MismatchedInputException.class);
            when(cause.getPath()).thenReturn(path);
            return cause;
        }

        private static HttpMessageNotReadableException unreadableCausedBy(Throwable cause) {
            return new HttpMessageNotReadableException(
                    "не прочитати", cause, mock(HttpInputMessage.class));
        }
    }

    @Nested
    @DisplayName("маршрут і метод")
    class RoutingErrors {

        @Test
        @DisplayName("невідомий шлях: 404 і шлях у повідомленні")
        void unknownRoute() {
            ApiError error = errorOf(handler.handleNoRoute(
                    new NoResourceFoundException(HttpMethod.GET, "/api/v1/nope")));

            assertThat(error.code()).isEqualTo(ErrorCode.ROUTE_NOT_FOUND);
            assertThat(error.message()).isEqualTo("Кінцевої точки /api/v1/nope не існує");
        }

        @Test
        @DisplayName("непідтримуваний метод: 405 і метод у повідомленні")
        void unsupportedMethod() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleMethod(new HttpRequestMethodNotSupportedException("PUT"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(errorOf(response).message())
                    .isEqualTo("Метод PUT не підтримується для цього ресурсу");
        }
    }

    @Nested
    @DisplayName("завантаження файлів")
    class Uploads {

        @Test
        @DisplayName("завеликий файл: межа 5 МБ зашита в текст повідомлення")
        void payloadTooLarge() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleTooLarge(new MaxUploadSizeExceededException(5L * 1024 * 1024));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
            // навмисно зафіксовано як є: повідомлення дублює межу з application.properties
            // і не читає її з винятку, тому зміна налаштування текст не оновить
            assertThat(errorOf(response).message()).isEqualTo("Файл завеликий: не більше 5 МБ");
        }

        @Test
        @DisplayName("бракує частини multipart")
        void missingPart() {
            ApiError error = errorOf(handler.handleMissingPart(new MissingServletRequestPartException("file")));

            assertThat(error.code()).isEqualTo(ErrorCode.INVALID_PARAMETER);
            assertThat(error.message()).isEqualTo("Бракує файлу в запиті");
            assertThat(error.details()).containsExactly(
                    new FieldViolation("file", "Частина multipart/form-data обов'язкова", null));
        }

        @Test
        @DisplayName("непідтримуваний тип вмісту: 415 і тип у повідомленні")
        void unsupportedMediaType() {
            ResponseEntity<ErrorResponse> response = handler.handleMediaType(
                    new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            assertThat(errorOf(response).message())
                    .isEqualTo("Тип вмісту text/plain не підтримується для цього ресурсу");
        }

        @Test
        @DisplayName("зламаний multipart-запит")
        void brokenMultipart() {
            ApiError error = errorOf(handler.handleMultipart(new MultipartException("щось не те")));

            assertThat(error.code()).isEqualTo(ErrorCode.MALFORMED_REQUEST);
            assertThat(error.message()).isEqualTo("Запит має бути multipart/form-data з файлом у полі file");
        }
    }

    @Nested
    @DisplayName("неочікуваний виняток")
    class Unexpected {

        @Test
        @DisplayName("500 з повідомленням, що не розкриває причину")
        void internalError() {
            ResponseEntity<ErrorResponse> response =
                    handler.handleUnexpected(new IllegalStateException("пароль у стектрейсі"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            ApiError error = errorOf(response);
            assertThat(error.code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
            assertThat(error.message()).isEqualTo("Внутрішня помилка сервера. Повідомте request_id підтримці");
            // текст винятку не має потрапляти клієнтові
            assertThat(error.message()).doesNotContain("пароль");
            assertThat(error.details()).isEmpty();
        }
    }

    // --- допоміжне -------------------------------------------------------------------------------

    private static MethodParameter methodParameter(int index) throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class
                .getDeclaredMethod("sample", String.class, String.class, String.class, String.class);
        return new MethodParameter(method, index);
    }

    private static MessageSourceResolvable resolvable(String message) {
        return new DefaultMessageSourceResolvable(new String[] {"code"}, null, message);
    }

    /** Мінімальний обʼєкт із властивістю, щоб {@code rejectValue} давав справжню помилку поля. */
    static final class Form {

        public String getName() {
            return "ab";
        }
    }

    @SuppressWarnings("unused")
    private void sample(@RequestParam(name = "q") String named,
                        @RequestParam String unnamed,
                        @PathVariable(name = "orgId") String pathNamed,
                        String plain) {
    }
}

package ua.edu.ukma.community.common.error;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Фіксує поведінку {@link GlobalExceptionHandler}, якої торкається рефакторинг #101:
 * формат {@code requestId} і визначення назви параметра. Зелені до зміни і після неї.
 */
@DisplayName("GlobalExceptionHandler: поведінка, якої торкається рефакторинг #101")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("без MDC requestId генерується як req_ плюс 16 шістнадцяткових символів")
    void generatedRequestIdKeepsItsFormat() {
        ApiError error = GlobalExceptionHandler.body(ErrorCode.INTERNAL_ERROR, "ой", List.of()).error();

        assertThat(error.requestId()).matches("^req_[0-9a-f]{16}$");
    }

    @Test
    @DisplayName("requestId береться з MDC, якщо фільтр його поклав")
    void requestIdComesFromMdcWhenPresent() {
        MDC.put("requestId", "req_from_filter");

        ApiError error = GlobalExceptionHandler.body(ErrorCode.FORBIDDEN, "нема прав", List.of()).error();

        assertThat(error.requestId()).isEqualTo("req_from_filter");
    }

    @Test
    @DisplayName("назва параметра береться з @RequestParam")
    void requestParamNameIsUsed() throws Exception {
        assertThat(resolvedFieldName(methodParameter(0))).isEqualTo("q");
    }

    @Test
    @DisplayName("@RequestParam без імені не вважається назвою: обробник іде далі по ланцюжку")
    void blankRequestParamNameFallsThrough() throws Exception {
        // параметр оголошений як @RequestParam String unnamed, тобто rp.name() порожній
        assertThat(resolvedFieldName(methodParameter(1))).isEqualTo("unnamed");
    }

    @Test
    @DisplayName("назва параметра береться з @PathVariable")
    void pathVariableNameIsUsed() throws Exception {
        assertThat(resolvedFieldName(methodParameter(2))).isEqualTo("orgId");
    }

    /** Проганяє параметр через обробник і повертає назву, яка потрапила в details. */
    private String resolvedFieldName(MethodParameter parameter) {
        parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        List<MessageSourceResolvable> errors = List.of(resolvable("щось не так"));

        ParameterValidationResult result = mock(ParameterValidationResult.class);
        when(result.getMethodParameter()).thenReturn(parameter);
        when(result.getResolvableErrors()).thenReturn(errors);

        List<ParameterValidationResult> results = List.of(result);
        HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
        when(ex.getParameterValidationResults()).thenReturn(results);

        ResponseEntity<ErrorResponse> response = handler.handleParamValidation(ex);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().error().details().getFirst().field();
    }

    private static MessageSourceResolvable resolvable(String message) {
        return new DefaultMessageSourceResolvable(new String[] {"code"}, null, message);
    }

    private static MethodParameter methodParameter(int index) throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class
                .getDeclaredMethod("sample", String.class, String.class, String.class);
        return new MethodParameter(method, index);
    }

    @SuppressWarnings("unused")
    private void sample(@RequestParam(name = "q") String named,
                        @RequestParam String unnamed,
                        @PathVariable(name = "orgId") String pathNamed) {
    }
}

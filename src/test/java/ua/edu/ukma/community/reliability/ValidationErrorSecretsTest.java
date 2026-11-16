package ua.edu.ukma.community.reliability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ua.edu.ukma.community.auth.CodeSender;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lab 09, R10 — тіло помилки валідації не розкриває пароль і код підтвердження.
 *
 * <p>Раніше {@code GlobalExceptionHandler} повертав {@code rejectedValue} будь-якого поля
 * в {@code details[].providedValue}, тож 400 на реєстрацію містив пароль відкритим текстом.
 * Тепер для чутливих полів ключа {@code providedValue} у JSON немає взагалі (не {@code null}),
 * а для звичайних полів значення, як і раніше, повертається.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("inmemory")
@DisplayName("Надійність, R10: пароль і код не повертаються в тілі помилки валідації")
class ValidationErrorSecretsTest {

    static final String AUTH = "/api/v1/auth";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CodeSender codeSender;

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(AUTH + path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("закороткий пароль при реєстрації → 400 без providedValue і без самого пароля в тілі")
    void shortPasswordIsNotEchoed() throws Exception {
        String password = "qwerty1";

        postJson("/register", "{\"email\":\"student@ukma.edu.ua\",\"password\":\"" + password + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[?(@.field == 'password')]", hasSize(1)))
                .andExpect(jsonPath("$.error.details[?(@.field == 'password')].message")
                        .value(contains("Пароль: від 8 до 72 символів")))
                .andExpect(jsonPath("$.error.details[0]", not(hasKey("providedValue"))))
                .andExpect(content().string(not(containsString(password))));
    }

    @Test
    @DisplayName("задовгий пароль (73 символи, верхня межа) → значення теж не повертається")
    void tooLongPasswordIsNotEchoed() throws Exception {
        String password = "p".repeat(73);

        postJson("/register", "{\"email\":\"student@ukma.edu.ua\",\"password\":\"" + password + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("password"))
                .andExpect(jsonPath("$.error.details[0]", not(hasKey("providedValue"))))
                .andExpect(content().string(not(containsString(password))));
    }

    @Test
    @DisplayName("невалідний код підтвердження → 400 без providedValue і без коду в тілі")
    void verificationCodeIsNotEchoed() throws Exception {
        String code = "12ab";

        postJson("/verify", "{\"email\":\"student@ukma.edu.ua\",\"code\":\"" + code + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("code"))
                .andExpect(jsonPath("$.error.details[0]", not(hasKey("providedValue"))))
                .andExpect(content().string(not(containsString(code))));
    }

    @Test
    @DisplayName("невалідна пошта: значення пошти повертається, а пароль із того ж запиту — ні")
    void nonSensitiveFieldIsStillEchoed() throws Exception {
        String password = "abc";

        postJson("/register", "{\"email\":\"not-an-email\",\"password\":\"" + password + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field == 'email')].providedValue")
                        .value(contains("not-an-email")))
                .andExpect(jsonPath("$.error.details[?(@.field == 'password')]", hasSize(1)))
                .andExpect(jsonPath("$.error.details[?(@.field == 'password')].providedValue", hasSize(0)))
                .andExpect(content().string(not(containsString("\"" + password + "\""))));
    }
}

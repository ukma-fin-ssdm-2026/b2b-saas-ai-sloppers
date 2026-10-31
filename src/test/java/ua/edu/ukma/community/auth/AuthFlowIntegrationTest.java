package ua.edu.ukma.community.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Наскрізно через HTTP: Spring Security, JWT, ролі на реальних контролерах (профіль inmemory, без бази).
 */
@SpringBootTest(properties = "auth.admin-emails=admin@ukma.edu.ua")
@AutoConfigureMockMvc
@ActiveProfiles("inmemory")
@DisplayName("Auth наскрізно: реєстрація → вхід → ролі на ендпоінтах")
class AuthFlowIntegrationTest {

    static final String API = "/api/v1";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    CodeSender codeSender;

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@ukma.edu.ua";
    }

    private ResultActions postJson(String path, String token, String body) throws Exception {
        return mvc.perform(withToken(post(path), token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String registerVerifyLogin(String email) throws Exception {
        postJson(API + "/auth/register", null, "{\"email\":\"" + email + "\",\"password\":\"password123\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(codeSender, atLeastOnce()).send(eq(email), code.capture());

        postJson(API + "/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"password123\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_VERIFIED"));

        postJson(API + "/auth/verify", null, "{\"email\":\"" + email + "\",\"code\":\"" + code.getValue() + "\"}")
                .andExpect(status().isNoContent());

        JsonNode token = body(postJson(API + "/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"password123\"}")
                .andExpect(status().isOk()));
        return token.get("accessToken").asText();
    }

    @Test
    @DisplayName("гість бачить каталог і події, але не може нічого змінювати (FR-007)")
    void guestAccess() throws Exception {
        mvc.perform(get(API + "/organizations")).andExpect(status().isOk());
        mvc.perform(get(API + "/events")).andExpect(status().isOk());

        postJson(API + "/organizations", null,
                "{\"name\":\"Guest org\",\"slug\":\"guest-org\",\"headEmail\":\"x@ukma.edu.ua\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get(API + "/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("TC-002: реєстрація з чужим доменом → 400")
    void foreignDomainRejected() throws Exception {
        postJson(API + "/auth/register", null, "{\"email\":\"student@gmail.com\",\"password\":\"password123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("email"));
    }

    @Test
    @DisplayName("повний сценарій: адмін створює організацію з головою, голова керує, інші — 403")
    void fullScenario() throws Exception {
        String adminToken = registerVerifyLogin("admin@ukma.edu.ua");
        String headEmail = uniqueEmail("head");
        String headToken = registerVerifyLogin(headEmail);
        String studentEmail = uniqueEmail("student");
        String studentToken = registerVerifyLogin(studentEmail);

        mvc.perform(withToken(get(API + "/auth/me"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalAdmin").value(true));

        String slug = "org-" + UUID.randomUUID().toString().substring(0, 8);
        String createBody = "{\"name\":\"Test organization\",\"slug\":\"" + slug
                + "\",\"headEmail\":\"" + headEmail + "\"}";

        // створювати організації може лише Global Admin
        postJson(API + "/organizations", studentToken, createBody).andExpect(status().isForbidden());
        // голова обов'язковий і має існувати
        postJson(API + "/organizations", adminToken, "{\"name\":\"No head\",\"slug\":\"no-head-org\"}")
                .andExpect(status().isBadRequest());
        postJson(API + "/organizations", adminToken,
                "{\"name\":\"Ghost head\",\"slug\":\"ghost-head\",\"headEmail\":\"ghost@ukma.edu.ua\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));

        String orgId = body(postJson(API + "/organizations", adminToken, createBody)
                .andExpect(status().isCreated())).get("id").asText();

        JsonNode me = body(mvc.perform(withToken(get(API + "/auth/me"), headToken)).andExpect(status().isOk()));
        String headId = me.get("id").asText();
        org.assertj.core.api.Assertions.assertThat(me.get("headOf").toString()).contains(orgId);

        // профіль: голова або адмін; сторонній студент — ні
        String patchBody = "{\"description\":\"Updated by head\"}";
        mvc.perform(withToken(patch(API + "/organizations/" + orgId), headToken)
                        .contentType(MediaType.APPLICATION_JSON).content(patchBody))
                .andExpect(status().isOk());
        mvc.perform(withToken(patch(API + "/organizations/" + orgId), studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(patchBody))
                .andExpect(status().isForbidden());

        // події: лише голова; адмін без призначення себе головою — ні (FR-006)
        String eventBody = "{\"title\":\"Open meeting\",\"startsAt\":\"2030-01-15T18:00:00+02:00\"}";
        postJson(API + "/organizations/" + orgId + "/events", adminToken, eventBody)
                .andExpect(status().isForbidden());
        postJson(API + "/organizations/" + orgId + "/events", headToken, eventBody)
                .andExpect(status().isCreated());

        // останнього голову зняти не можна (FR-011)
        mvc.perform(withToken(delete(API + "/organizations/" + orgId + "/heads/" + headId), headToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LAST_HEAD"));

        // голова призначає студента (FR-009), той отримує права, журнал усе фіксує (FR-013)
        postJson(API + "/organizations/" + orgId + "/heads", headToken, "{\"email\":\"" + studentEmail + "\"}")
                .andExpect(status().isCreated());
        mvc.perform(withToken(patch(API + "/organizations/" + orgId), studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(patchBody))
                .andExpect(status().isOk());
        mvc.perform(withToken(get(API + "/organizations/" + orgId + "/role-log"), headToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // видаляти організацію може лише адмін
        mvc.perform(withToken(delete(API + "/organizations/" + orgId), headToken))
                .andExpect(status().isForbidden());
        mvc.perform(withToken(delete(API + "/organizations/" + orgId), adminToken))
                .andExpect(status().isNoContent());
    }
}

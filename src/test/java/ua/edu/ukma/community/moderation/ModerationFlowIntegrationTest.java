package ua.edu.ukma.community.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ua.edu.ukma.community.auth.CodeSender;
import ua.edu.ukma.community.auth.TokenService;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.auth.UserRepository;
import ua.edu.ukma.community.auth.UserStatus;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Модерація наскрізно через HTTP: Spring Security, JWT, справжні контролери (профіль inmemory, без бази).
 * Контекст Spring спільний з іншими наскрізними тестами, тож кожен тест створює своїх користувачів,
 * а додане слово автофільтра прибирає за собою.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("inmemory")
@DisplayName("Модерація наскрізно: скарга → рішення адміністратора, бан → 403, автофільтр → 400")
class ModerationFlowIntegrationTest {

    static final String ADVERTS = "/api/v1/adverts";
    static final String COMPLAINTS = "/api/v1/complaints";
    static final String MODERATION = "/api/v1/moderation";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    UserRepository users;

    @Autowired
    TokenService tokens;

    @MockitoBean
    CodeSender codeSender;

    private User user(String name, boolean globalAdmin) {
        OffsetDateTime now = OffsetDateTime.now();
        String email = name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@ukma.edu.ua";
        return users.save(new User(UUID.randomUUID(), email, "hash", UserStatus.ACTIVE, globalAdmin, null, now, now));
    }

    private String token(User user) {
        return tokens.issue(user).accessToken();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions postJson(String path, String token, String body) throws Exception {
        return perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions createAdvert(String token, String title) throws Exception {
        return postJson(ADVERTS, token, """
                {"type":"TEAM","title":"%s","description":"Хакатон на вихідних",\
                "slots":2,"deadline":"%s","contact":"@seeker"}""".formatted(title, OffsetDateTime.now().plusDays(2)));
    }

    private String complaintJson(String advertId) {
        return """
                {"targetType":"ADVERT","targetId":"%s","category":"SPAM","comment":"Реклама"}""".formatted(advertId);
    }

    @Test
    @DisplayName("гість не скаржиться — 401; звичайний користувач не бачить черги — 403")
    void accessRules() throws Exception {
        String alice = token(user("alice", false));

        mvc.perform(post(COMPLAINTS).contentType(MediaType.APPLICATION_JSON).content(complaintJson(
                UUID.randomUUID().toString()))).andExpect(status().isUnauthorized());
        perform(get(MODERATION + "/complaints"), alice)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("TC-037: скарга → адміністратор підтверджує → оголошення зникає")
    void complaintResolvedRemovesAdvert() throws Exception {
        String author = token(user("author", false));
        String alice = token(user("alice", false));
        String admin = token(user("admin", true));
        String advertId = body(createAdvert(author, "Продам курси").andExpect(status().isCreated()))
                .get("id").asText();

        String complaintId = body(postJson(COMPLAINTS, alice, complaintJson(advertId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.targetType").value("ADVERT")))
                .get("id").asText();
        postJson(COMPLAINTS, alice, complaintJson(advertId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_COMPLAINED"));

        perform(get(MODERATION + "/complaints").param("status", "OPEN").param("limit", "100"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '%s')]".formatted(complaintId)).exists());
        postJson(MODERATION + "/complaints/" + complaintId + "/resolve", admin, "{\"resolution\":\"Реклама\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        perform(get(ADVERTS + "/" + advertId), alice).andExpect(status().isNotFound());
        postJson(MODERATION + "/complaints/" + complaintId + "/dismiss", admin, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("TC-038: бан закриває API для порушника, розблокування відкриває знову")
    void banLocksTheAccountOut() throws Exception {
        User bob = user("bob", false);
        String bobToken = token(bob);
        String admin = token(user("admin", true));

        postJson(MODERATION + "/users/" + bob.id() + "/ban", admin,
                "{\"reason\":\"Спам\",\"expiresAt\":\"%s\"}".formatted(OffsetDateTime.now().plusDays(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.automatic").value(false));

        createAdvert(bobToken, "Шукаю команду")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_BLOCKED"));
        postJson(COMPLAINTS, bobToken, complaintJson(UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_BLOCKED"));

        perform(delete(MODERATION + "/users/" + bob.id() + "/ban"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        perform(get(MODERATION + "/users/" + bob.id() + "/bans"), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        createAdvert(bobToken, "Шукаю команду").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("бан без причини — 400 з полем reason")
    void banNeedsReason() throws Exception {
        User bob = user("bob", false);
        String admin = token(user("admin", true));

        postJson(MODERATION + "/users/" + bob.id() + "/ban", admin, "{\"reason\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("reason"));
    }

    @Test
    @DisplayName("FR-058: заборонене слово відбиває оголошення з кодом CONTENT_REJECTED")
    void bannedWordRejectsAdvert() throws Exception {
        String admin = token(user("admin", true));
        String carol = token(user("carol", false));
        String word = "шахрайськекасино";
        String wordId = body(postJson(MODERATION + "/banned-words", admin, "{\"word\":\"" + word + "\"}")
                .andExpect(status().isCreated()))
                .get("id").asText();
        try {
            createAdvert(carol, "Найкраще " + word.toUpperCase(Locale.ROOT) + " міста")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("CONTENT_REJECTED"));
        } finally {
            perform(delete(MODERATION + "/banned-words/" + wordId), admin).andExpect(status().isNoContent());
        }
        createAdvert(carol, "Найкраще " + word + " міста").andExpect(status().isCreated());
    }
}

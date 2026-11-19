package ua.edu.ukma.community.board;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Дошка наскрізно через HTTP: Spring Security, JWT, справжні контролери (профіль inmemory, без бази).
 * Головне тут — NFR-009: контакт автора не потрапляє в жодну відповідь тим, кому його бачити не можна.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("inmemory")
@DisplayName("Дошка оголошень наскрізно: створення → відгук → підтвердження → контакт")
class BoardFlowIntegrationTest {

    static final String ADVERTS = "/api/v1/adverts";

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

    /** Відповіді, отримані тими, хто контакту бачити не має; наприкінці жодна не має його містити. */
    private final List<String> strangerResponses = new ArrayList<>();

    private String tokenFor(String name) {
        return tokenFor(name, null, null);
    }

    private String tokenFor(String name, String firstName, String lastName) {
        OffsetDateTime now = OffsetDateTime.now();
        String email = name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@ukma.edu.ua";
        User user = users.save(new User(UUID.randomUUID(), email, "hash", UserStatus.ACTIVE, false, null, now, now,
                firstName, lastName));
        return tokens.issue(user).accessToken();
    }

    private static String uniqueContact() {
        return "@seek_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private static String advertJson(String contact, int slots, OffsetDateTime deadline) {
        return """
                {"type":"TEAM","title":"Шукаю бекендера","description":"Хакатон на вихідних",\
                "slots":%d,"deadline":"%s","contact":"%s"}""".formatted(slots, deadline, contact);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions postJson(String path, String token, String body) throws Exception {
        return perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    /** Запит від того, хто контакту не бачить: тіло відповіді збираємо для перевірки наприкінці. */
    private ResultActions asStranger(MockHttpServletRequestBuilder request, String token) throws Exception {
        ResultActions result = perform(request, token);
        strangerResponses.add(result.andReturn().getResponse().getContentAsString());
        return result;
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String createAdvert(String token, String contact, int slots) throws Exception {
        return body(postJson(ADVERTS, token, advertJson(contact, slots, OffsetDateTime.now().plusDays(2)))
                .andExpect(status().isCreated()))
                .get("id").asText();
    }

    @Test
    @DisplayName("TC-030 (гість): дошка недоступна без токена — 401")
    void guestHasNoAccess() throws Exception {
        mvc.perform(get(ADVERTS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get(ADVERTS + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post(ADVERTS).contentType(MediaType.APPLICATION_JSON)
                        .content(advertJson("@someone", 1, OffsetDateTime.now().plusDays(1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TC-029: без контакту, з кривим контактом, нулем місць чи дедлайном у минулому — 400")
    void createValidation() throws Exception {
        String token = tokenFor("author");
        OffsetDateTime tomorrow = OffsetDateTime.now().plusDays(1);

        postJson(ADVERTS, token, """
                {"type":"TEAM","title":"Без контакту","description":"d","slots":1,"deadline":"%s"}"""
                .formatted(tomorrow))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("contact"));
        postJson(ADVERTS, token, advertJson("напишіть мені в лс", 1, tomorrow))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("contact"))
                // NFR-009: контакт не повертається навіть у тілі помилки
                .andExpect(jsonPath("$.error.details[0].providedValue").doesNotExist())
                .andExpect(content().string(not(containsString("напишіть мені"))));
        postJson(ADVERTS, token, advertJson("@someone", 0, tomorrow))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("slots"));
        postJson(ADVERTS, token, advertJson("@someone", 1, OffsetDateTime.now().minusHours(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("deadline"));
    }

    @Test
    @DisplayName("TC-028 – TC-036 / NFR-009: повний сценарій; контакт бачать лише автор і підтверджений")
    void fullFlowKeepsContactPrivate() throws Exception {
        String contact = uniqueContact();
        String author = tokenFor("author");
        String alice = tokenFor("alice");
        String bob = tokenFor("bob");

        // TC-028: створення, автор бачить свій контакт
        ResultActions created = postJson(ADVERTS, author, advertJson(contact, 1, OffsetDateTime.now().plusDays(2)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(ADVERTS + "/")))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.own").value(true))
                .andExpect(jsonPath("$.contact").value(contact));
        String advertId = body(created).get("id").asText();
        String advert = ADVERTS + "/" + advertId;

        // TC-030: інші бачать оголошення, але без контакту
        asStranger(get(ADVERTS), alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + advertId + "')]").isNotEmpty());
        asStranger(get(advert), bob)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").value(nullValue()));

        // TC-031: відгук; на своє — 403, повторний — 409
        postJson(advert + "/replies", author, "{\"message\":\"сам собі\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OWN_ADVERT"));
        String aliceReply = body(postJson(advert + "/replies", alice, "{\"message\":\"Пишу на Spring\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")))
                .get("id").asText();
        asStranger(post(advert + "/replies").contentType(MediaType.APPLICATION_JSON).content("{}"), alice)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_REPLIED"));
        String bobReply = body(asStranger(post(advert + "/replies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"І я\"}"), bob)
                .andExpect(status().isCreated()))
                .get("id").asText();

        // до підтвердження контакт закритий і для відгукувача
        asStranger(get(advert), alice)
                .andExpect(jsonPath("$.myReplyStatus").value("PENDING"))
                .andExpect(jsonPath("$.contact").value(nullValue()));
        asStranger(get(ADVERTS + "/replied"), alice).andExpect(status().isOk());

        // TC-032: відгуки бачить лише автор
        asStranger(get(advert + "/replies"), alice)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        perform(get(advert + "/replies"), author)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        asStranger(post(advert + "/replies/" + aliceReply + "/confirm"), bob)
                .andExpect(status().isForbidden());

        // TC-033 + TC-034: підтвердження відкриває контакт Алісі й закриває оголошення (1 місце)
        perform(post(advert + "/replies/" + aliceReply + "/confirm"), author)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        perform(get(advert), alice)
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.contact").value(contact));

        // Боб — відгук не підтверджено — контакту не бачить ні до, ні після закриття
        asStranger(get(advert), bob)
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.myReplyStatus").value("PENDING"))
                .andExpect(jsonPath("$.contact").value(nullValue()));
        asStranger(get(ADVERTS + "/replied"), bob).andExpect(status().isOk());

        // TC-036: закрите не серед активних, відгуки на нього не приймаються
        asStranger(get(ADVERTS), bob)
                .andExpect(content().string(not(containsString(advertId))));
        asStranger(post(advert + "/replies").contentType(MediaType.APPLICATION_JSON).content("{}"),
                tokenFor("carol"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADVERT_CLOSED"));
        perform(post(advert + "/replies/" + bobReply + "/confirm"), author)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADVERT_CLOSED"));

        // підтверджена Аліса зберігає контакт і в «моїх відгуках»
        perform(get(ADVERTS + "/replied"), alice)
                .andExpect(jsonPath("$.data[0].id").value(advertId))
                .andExpect(jsonPath("$.data[0].contact").value(contact));

        assertThat(strangerResponses).isNotEmpty().noneMatch(responseBody -> responseBody.contains(contact));
    }

    @Test
    @DisplayName("FR-051, FR-053: відкликання відгуку, ручне закриття й видалення")
    void withdrawCloseDelete() throws Exception {
        String author = tokenFor("author");
        String alice = tokenFor("alice");
        String advertId = createAdvert(author, uniqueContact(), 3);
        String advert = ADVERTS + "/" + advertId;

        String reply = body(postJson(advert + "/replies", alice, "{}").andExpect(status().isCreated()))
                .get("id").asText();
        perform(post(advert + "/replies/" + reply + "/withdraw"), alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        perform(get(advert + "/replies"), author).andExpect(jsonPath("$.length()").value(0));

        perform(post(advert + "/close"), alice).andExpect(status().isForbidden());
        perform(post(advert + "/close"), author)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        perform(get(ADVERTS + "/mine"), author)
                .andExpect(jsonPath("$.data[0].id").value(advertId));

        perform(delete(advert), alice).andExpect(status().isForbidden());
        perform(delete(advert), author).andExpect(status().isNoContent());
        perform(get(advert), author)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADVERT_NOT_FOUND"));
        perform(get(ADVERTS + "/mine"), author).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("FR-069: в оголошенні видно ім'я автора, у відгуках — ім'я відгукувача; без імені — null")
    void namesInsteadOfIds() throws Exception {
        String author = tokenFor("author", "Теодора", "Гречко");
        String alice = tokenFor("alice", "Олена", "Коваль");
        String anonymous = tokenFor("anon");
        String advertId = createAdvert(author, uniqueContact(), 3);
        String advert = ADVERTS + "/" + advertId;

        perform(get(advert), alice)
                .andExpect(jsonPath("$.authorName").value("Теодора Гречко"));
        perform(get(ADVERTS), alice)
                .andExpect(jsonPath("$.data[?(@.id == '" + advertId + "')].authorName").value(
                        org.hamcrest.Matchers.contains("Теодора Гречко")));

        postJson(advert + "/replies", alice, "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userName").value("Олена Коваль"));
        postJson(advert + "/replies", anonymous, "{}").andExpect(status().isCreated());

        perform(get(advert + "/replies"), author)
                .andExpect(jsonPath("$[*].userName").value(
                        org.hamcrest.Matchers.containsInAnyOrder("Олена Коваль", (String) null)));
    }
}

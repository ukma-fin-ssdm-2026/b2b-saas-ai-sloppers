package ua.edu.ukma.community.rating;

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
import ua.edu.ukma.community.organization.Organization;
import ua.edu.ukma.community.organization.OrganizationRepository;
import ua.edu.ukma.community.support.TestFixtures;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Рейтинги наскрізно через HTTP (профіль inmemory): оцінка автора після закриття оголошення й рейтинг
 * організації в публічному каталозі. Контекст спільний з іншими наскрізними тестами, тож кожен тест
 * створює власних користувачів і власну організацію.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("inmemory")
@DisplayName("Рейтинги наскрізно: учасник оцінює автора, користувачі — організацію")
class RatingFlowIntegrationTest {

    static final String ADVERTS = "/api/v1/adverts";
    static final String ORGANIZATIONS = "/api/v1/organizations";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    UserRepository users;

    @Autowired
    OrganizationRepository organizations;

    @Autowired
    TokenService tokens;

    @MockitoBean
    CodeSender codeSender;

    private String token(String name) {
        OffsetDateTime now = OffsetDateTime.now();
        String email = name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@ukma.edu.ua";
        User user = users.save(new User(UUID.randomUUID(), email, "hash", UserStatus.ACTIVE, false, null, now, now));
        return tokens.issue(user).accessToken();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions sendJson(MockHttpServletRequestBuilder request, String token, String body)
            throws Exception {
        return perform(request.contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    private String id(ResultActions result) throws Exception {
        JsonNode body = json.readTree(result.andReturn().getResponse().getContentAsString());
        return body.get("id").asText();
    }

    @Test
    @DisplayName("FR-061, FR-062: учасник оцінює автора після закриття; рейтинг видно в оголошенні")
    void participantRatesAuthor() throws Exception {
        String author = token("author");
        String alice = token("alice");
        String advert = ADVERTS + "/" + id(sendJson(post(ADVERTS), author, """
                {"type":"TEAM","title":"Шукаю бекендера","description":"Хакатон на вихідних",\
                "slots":1,"deadline":"%s","contact":"@seeker_team"}""".formatted(OffsetDateTime.now().plusDays(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorRating.count").value(0))
                .andExpect(jsonPath("$.authorRating.average").value(nullValue())));

        String reply = id(sendJson(post(advert + "/replies"), alice, "{}").andExpect(status().isCreated()));
        sendJson(post(advert + "/rating"), alice, "{\"score\":5}")
                .andExpect(status().isForbidden());
        perform(post(advert + "/replies/" + reply + "/confirm"), author).andExpect(status().isOk());

        sendJson(post(advert + "/rating"), alice, "{\"score\":5}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(5))
                .andExpect(jsonPath("$.authorRating.average").value(5.0))
                .andExpect(jsonPath("$.authorRating.count").value(1));
        sendJson(post(advert + "/rating"), alice, "{\"score\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_RATED"));

        perform(get(advert), alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.myRating").value(5))
                .andExpect(jsonPath("$.authorRating.count").value(1));
        perform(get(advert), author)
                .andExpect(jsonPath("$.myRating").value(nullValue()))
                .andExpect(jsonPath("$.authorRating.average").value(5.0));
    }

    @Test
    @DisplayName("оцінка поза шкалою — 400 з полем score")
    void scoreOutOfRange() throws Exception {
        sendJson(post(ADVERTS + "/" + UUID.randomUUID() + "/rating"), token("alice"), "{\"score\":6}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("score"));
    }

    @Test
    @DisplayName("FR-063, FR-064: оцінка організації змінюється, а гість бачить середнє в каталозі й профілі")
    void organizationRating() throws Exception {
        Organization club = organizations.save(TestFixtures.organization(UUID.randomUUID(), "Клуб рейтингів",
                "rating-club-" + UUID.randomUUID().toString().substring(0, 8)));
        String path = ORGANIZATIONS + "/" + club.id();
        String alice = token("alice");
        String bob = token("bob");

        mvc.perform(get(path + "/rating")).andExpect(status().isUnauthorized());
        sendJson(put(path + "/rating"), alice, "{\"score\":2}").andExpect(status().isOk());
        sendJson(put(path + "/rating"), alice, "{\"score\":5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myScore").value(5))
                .andExpect(jsonPath("$.rating.count").value(1));
        sendJson(put(path + "/rating"), bob, "{\"score\":4}").andExpect(status().isOk());

        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Клуб рейтингів"))
                .andExpect(jsonPath("$.rating.average").value(4.5))
                .andExpect(jsonPath("$.rating.count").value(2));
        mvc.perform(get(ORGANIZATIONS).param("q", club.slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(club.id().toString()))
                .andExpect(jsonPath("$.data[0].rating.average").value(4.5));
        perform(get(path + "/rating"), token("carol"))
                .andExpect(jsonPath("$.myScore").value(nullValue()))
                .andExpect(jsonPath("$.rating.count").value(2));
    }
}

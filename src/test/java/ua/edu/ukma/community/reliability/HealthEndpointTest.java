package ua.edu.ukma.community.reliability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lab 09, бонус 2 — {@code /actuator/health} наскрізно, коли Supabase Storage недоступний.
 *
 * <p>Загальний статус чесно каже {@code DOWN} (503) і показує, яка залежність зламалась,
 * але readiness і liveness лишаються {@code UP}: без картинок сайт працює, тож Kubernetes
 * не виводить под з балансування і не перезапускає його (graceful degradation).
 */
@SpringBootTest(properties = "management.endpoint.health.cache.time-to-live=0")
@AutoConfigureMockMvc
@ActiveProfiles("inmemory")
@DisplayName("Надійність, бонус: /actuator/health при недоступному сховищі")
class HealthEndpointTest {

    @Autowired
    MockMvc mvc;

    /**
     * Підміняє справжній індикатор Supabase (бін з {@code StorageConfig}).
     * {@code CALLS_REAL_METHODS}: actuator викликає default-метод {@code getHealth(boolean)}, а не {@code health()};
     * без цього мок повернув би {@code null}, і компонент {@code storage} просто зник би з відповіді.
     */
    @MockitoBean(name = "storageHealthIndicator", answers = Answers.CALLS_REAL_METHODS)
    HealthIndicator storageHealth;

    @Test
    @DisplayName("сховище DOWN → /actuator/health 503 з components.storage = DOWN, без деталей")
    void storageDownMakesOverallHealthDown() throws Exception {
        when(storageHealth.health()).thenReturn(Health.down().withDetail("bucket", "images").build());

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.storage.status").value("DOWN"))
                .andExpect(jsonPath("$.components.storage", not(hasKey("details"))));
    }

    @Test
    @DisplayName("сховище DOWN → readiness і liveness UP: под лишається в роботі")
    void storageDownKeepsPodInService() throws Exception {
        when(storageHealth.health()).thenReturn(Health.down().build());

        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("сховище UP → загальний статус UP, 200")
    void storageUpMakesOverallHealthUp() throws Exception {
        when(storageHealth.health()).thenReturn(Health.up().build());

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.storage.status").value("UP"));
    }
}
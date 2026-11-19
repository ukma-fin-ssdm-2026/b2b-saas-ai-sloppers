package ua.edu.ukma.community.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import ua.edu.ukma.community.support.MutableClock;
import ua.edu.ukma.community.support.TestDatabase;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** SQL голів і журналу ролей на справжньому PostgreSQL; зокрема імена з JOIN (FR-069). */
@DisplayName("JdbcHeadRepository на PostgreSQL")
class JdbcHeadRepositoryTest {

    private MutableClock clock;
    private JdbcClient jdbc;
    private JdbcUserRepository users;
    private JdbcHeadRepository heads;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        TestDatabase.truncateAll();
        DataSource dataSource = TestDatabase.dataSource();
        clock = MutableClock.startingAtFixtureNow();
        jdbc = JdbcClient.create(dataSource);
        users = new JdbcUserRepository(jdbc, clock);
        heads = new JdbcHeadRepository(jdbc, new DataSourceTransactionManager(dataSource), clock);
        organizationId = UUID.randomUUID();
        jdbc.sql("INSERT INTO organization (id, name, slug) VALUES (?, 'Org', 'org')").param(organizationId).update();
    }

    private User user(String email, String firstName, String lastName) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return users.save(new User(UUID.randomUUID(), email, "hash", UserStatus.ACTIVE, false, null, now, now,
                firstName, lastName));
    }

    @Test
    @DisplayName("FR-069: у списку голів і журналі ролей — ім'я та прізвище; без імені — null")
    void namesInHeadsAndLog() {
        User admin = user("admin@ukma.edu.ua", null, null);
        User head = user("head@ukma.edu.ua", "Теодора", "Гречко");
        OffsetDateTime now = OffsetDateTime.now(clock);

        assertThat(heads.appoint(organizationId, head.id(), admin.id(), now)).isTrue();
        assertThat(heads.appoint(organizationId, head.id(), admin.id(), now)).isFalse();

        assertThat(heads.findHeads(organizationId)).singleElement().satisfies(h -> {
            assertThat(h.userId()).isEqualTo(head.id());
            assertThat(h.email()).isEqualTo("head@ukma.edu.ua");
            assertThat(h.name()).isEqualTo("Теодора Гречко");
            assertThat(h.appointedBy()).isEqualTo(admin.id());
        });
        assertThat(heads.findLog(organizationId)).singleElement().satisfies(r -> {
            assertThat(r.targetName()).isEqualTo("Теодора Гречко");
            assertThat(r.actorEmail()).isEqualTo("admin@ukma.edu.ua");
            assertThat(r.actorName()).isNull();
            assertThat(r.action()).isEqualTo(RoleChange.RoleAction.APPOINTED);
        });
        assertThat(heads.isHead(organizationId, head.id())).isTrue();
        assertThat(heads.findOrganizationsHeadedBy(head.id())).containsExactly(organizationId);
    }

    @Test
    @DisplayName("FR-011: останнього голову не зняти; другого — можна, і це потрапляє в журнал")
    void lastHeadProtection() {
        User admin = user("admin@ukma.edu.ua", null, null);
        User first = user("first@ukma.edu.ua", "Олена", "Коваль");
        User second = user("second@ukma.edu.ua", null, null);
        OffsetDateTime now = OffsetDateTime.now(clock);
        heads.appoint(organizationId, first.id(), admin.id(), now);

        assertThat(heads.remove(organizationId, first.id(), admin.id(), now))
                .isEqualTo(HeadRepository.RemoveResult.LAST_HEAD);
        assertThat(heads.remove(organizationId, second.id(), admin.id(), now))
                .isEqualTo(HeadRepository.RemoveResult.NOT_HEAD);

        heads.appoint(organizationId, second.id(), first.id(), now);
        assertThat(heads.remove(organizationId, second.id(), first.id(), now))
                .isEqualTo(HeadRepository.RemoveResult.REMOVED);
        assertThat(heads.findLog(organizationId)).extracting(RoleChange::actorName)
                .contains("Олена Коваль");
    }
}

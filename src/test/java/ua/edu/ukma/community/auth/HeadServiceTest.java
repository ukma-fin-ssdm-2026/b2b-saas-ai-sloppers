package ua.edu.ukma.community.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ua.edu.ukma.community.auth.AuthTestSupport.activeUser;
import static ua.edu.ukma.community.support.TestFixtures.FIXED_CLOCK;
import static ua.edu.ukma.community.support.TestFixtures.ORG_ID;
import static ua.edu.ukma.community.support.TestFixtures.OTHER_ORG_ID;

@DisplayName("HeadService і OrgRolePolicy: ролі голів (FR-005–FR-013)")
class HeadServiceTest {

    final OffsetDateTime now = OffsetDateTime.now(FIXED_CLOCK);

    InMemoryUserRepository users;
    InMemoryHeadRepository headRepository;
    OrgRolePolicy policy;
    HeadService service;

    User admin;
    User headA;
    User headB;
    User student;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        headRepository = new InMemoryHeadRepository(users);
        policy = new OrgRolePolicy(headRepository);
        service = new HeadService(headRepository, users, policy, FIXED_CLOCK);

        admin = users.save(activeUser("admin@ukma.edu.ua", true, now));
        headA = users.save(activeUser("head-a@ukma.edu.ua", false, now));
        headB = users.save(activeUser("head-b@ukma.edu.ua", false, now));
        student = users.save(activeUser("student@ukma.edu.ua", false, now));

        service.appointFirstHead(admin, ORG_ID, headA);
        service.appointFirstHead(admin, OTHER_ORG_ID, headB);
    }

    private static ErrorCode codeOf(Throwable e) {
        return ((ApiException) e).code();
    }

    @Test
    @DisplayName("TC-015: голова призначає й знімає іншого голову своєї організації; усе пишеться в журнал")
    void headAppointsAndRemovesHead() {
        OrganizationHead appointed = service.appoint(headA, ORG_ID, "STUDENT@ukma.edu.ua", null);
        assertThat(appointed.userId()).isEqualTo(student.id());
        assertThat(appointed.appointedBy()).isEqualTo(headA.id());

        service.remove(headA, ORG_ID, student.id());

        assertThat(service.list(headA, ORG_ID)).extracting(OrganizationHead::userId).containsExactly(headA.id());
        List<RoleChange> log = service.log(headA, ORG_ID);
        assertThat(log).extracting(RoleChange::action).contains(
                RoleChange.RoleAction.APPOINTED, RoleChange.RoleAction.REMOVED);
        assertThat(log).filteredOn(r -> r.targetUserId().equals(student.id()))
                .allSatisfy(r -> {
                    assertThat(r.actorUserId()).isEqualTo(headA.id());
                    assertThat(r.targetEmail()).isEqualTo("student@ukma.edu.ua");
                });
    }

    @Test
    @DisplayName("TC-017: голова організації A не керує головами організації B → 403, нічого не змінено")
    void headCannotManageOtherOrganization() {
        assertThatThrownBy(() -> service.appoint(headA, OTHER_ORG_ID, "student@ukma.edu.ua", null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.remove(headA, OTHER_ORG_ID, headB.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.log(headA, OTHER_ORG_ID))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(service.list(headB, OTHER_ORG_ID)).extracting(OrganizationHead::userId)
                .containsExactly(headB.id());
    }

    @Test
    @DisplayName("FR-006: Global Admin керує головами будь-якої організації, але сам не має прав голови")
    void adminManagesHeadsButIsNotHead() {
        service.appoint(admin, OTHER_ORG_ID, "student@ukma.edu.ua", null);
        assertThat(service.list(admin, OTHER_ORG_ID)).hasSize(2);

        assertThatThrownBy(() -> policy.requireHead(admin, OTHER_ORG_ID))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FORBIDDEN));
        policy.requireHeadOrAdmin(admin, OTHER_ORG_ID);

        // щоб модерувати події, адмін призначає себе головою
        service.appoint(admin, OTHER_ORG_ID, "admin@ukma.edu.ua", null);
        policy.requireHead(admin, OTHER_ORG_ID);
    }

    @Test
    @DisplayName("звичайний користувач не бачить голів і не призначає їх")
    void studentHasNoRights() {
        assertThatThrownBy(() -> service.list(student, ORG_ID))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.appoint(student, ORG_ID, "student@ukma.edu.ua", null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> policy.requireGlobalAdmin(student))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("FR-011: останнього голову зняти не можна, навіть самому собі")
    void lastHeadCannotBeRemoved() {
        assertThatThrownBy(() -> service.remove(headA, ORG_ID, headA.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.LAST_HEAD));
        assertThatThrownBy(() -> service.remove(admin, ORG_ID, headA.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.LAST_HEAD));
        assertThat(policy.isHead(headA, ORG_ID)).isTrue();
    }

    @Test
    @DisplayName("TC-016 / NFR-006: 100 паралельних зняттів — лишається рівно один голова")
    void concurrentRemovalsKeepOneHead() throws InterruptedException {
        List<User> extra = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            User u = users.save(activeUser("h" + i + "@ukma.edu.ua", false, now));
            service.appoint(admin, ORG_ID, u.email(), null);
            extra.add(u);
        }
        extra.add(headA);
        AtomicInteger removed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        for (int i = 0; i < 100; i++) {
            User target = extra.get(i % extra.size());
            pool.submit(() -> {
                try {
                    service.remove(admin, ORG_ID, target.id());
                    removed.incrementAndGet();
                } catch (ApiException expected) {
                    // LAST_HEAD або HEAD_NOT_FOUND
                }
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(service.list(admin, ORG_ID)).hasSize(1);
        assertThat(removed.get()).isEqualTo(9);
    }

    @Test
    @DisplayName("FR-012: Global Admin замінює останнього голову одним запитом; голові це заборонено")
    void adminReplacesLastHead() {
        assertThatThrownBy(() -> service.appoint(headA, ORG_ID, "student@ukma.edu.ua", headA.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.FORBIDDEN));

        service.appoint(admin, ORG_ID, "student@ukma.edu.ua", headA.id());

        assertThat(service.list(admin, ORG_ID)).extracting(OrganizationHead::userId).containsExactly(student.id());
    }

    @Test
    @DisplayName("головою можна зробити лише зареєстрованого з підтвердженою поштою; двічі — 409")
    void appointValidation() {
        users.save(new User(UUID.randomUUID(), "pending@ukma.edu.ua", "hash", UserStatus.PENDING, false, null,
                now, now));

        assertThatThrownBy(() -> service.appoint(headA, ORG_ID, "nobody@ukma.edu.ua", null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.USER_NOT_FOUND));
        assertThatThrownBy(() -> service.appoint(headA, ORG_ID, "pending@ukma.edu.ua", null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.USER_NOT_FOUND));
        assertThatThrownBy(() -> service.appoint(headA, ORG_ID, "head-a@ukma.edu.ua", null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ALREADY_HEAD));
        assertThatThrownBy(() -> service.remove(headA, ORG_ID, student.id()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.HEAD_NOT_FOUND));
    }

    @Test
    @DisplayName("/auth/me: список організацій, де користувач голова")
    void organizationsHeadedBy() {
        service.appoint(admin, OTHER_ORG_ID, "head-a@ukma.edu.ua", null);

        assertThat(service.organizationsHeadedBy(headA)).containsExactlyInAnyOrder(ORG_ID, OTHER_ORG_ID);
        assertThat(service.organizationsHeadedBy(student)).isEmpty();
    }
}

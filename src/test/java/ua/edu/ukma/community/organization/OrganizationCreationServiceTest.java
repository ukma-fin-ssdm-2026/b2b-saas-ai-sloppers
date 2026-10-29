package ua.edu.ukma.community.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import ua.edu.ukma.community.auth.HeadService;
import ua.edu.ukma.community.auth.OrgRolePolicy;
import ua.edu.ukma.community.auth.User;
import ua.edu.ukma.community.auth.UserStatus;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ua.edu.ukma.community.support.TestFixtures.FIXED_CLOCK;
import static ua.edu.ukma.community.support.TestFixtures.ORG_ID;
import static ua.edu.ukma.community.support.TestFixtures.organization;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationCreationService: організація й перший голова в одній транзакції (FR-011, TC-012)")
class OrganizationCreationServiceTest {

    /** Фіксує, чи виконується код усередині транзакції і чи її відкочено. */
    static final class RecordingTransactions implements TransactionOperations {
        boolean active;
        boolean opened;
        boolean rolledBack;

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            opened = true;
            active = true;
            TransactionStatus status = new SimpleTransactionStatus();
            try {
                return action.doInTransaction(status);
            } catch (RuntimeException e) {
                rolledBack = true;
                throw e;
            } finally {
                active = false;
            }
        }
    }

    final OffsetDateTime now = OffsetDateTime.now(FIXED_CLOCK);
    final User admin = new User(UUID.randomUUID(), "admin@ukma.edu.ua", "hash", UserStatus.ACTIVE, true, null,
            now, now);
    final User head = new User(UUID.randomUUID(), "head@ukma.edu.ua", "hash", UserStatus.ACTIVE, false, null,
            now, now);
    final OrganizationCreateRequest request = new OrganizationCreateRequest("FIDo", "fido", null, null, null,
            "head@ukma.edu.ua");

    @Mock
    OrganizationService organizations;
    @Mock
    HeadService heads;
    @Mock
    OrgRolePolicy policy;

    RecordingTransactions transactions;
    OrganizationCreationService service;

    @BeforeEach
    void setUp() {
        transactions = new RecordingTransactions();
        service = new OrganizationCreationService(organizations, heads, policy, transactions);
    }

    @Test
    @DisplayName("створення організації й призначення голови відбуваються в одній транзакції")
    void createsOrganizationAndHeadInOneTransaction() {
        Organization created = organization(ORG_ID, "FIDo", "fido");
        when(heads.requireActiveUser("head@ukma.edu.ua")).thenReturn(head);
        when(organizations.create(request)).thenAnswer(inv -> {
            assertThat(transactions.active).isTrue();
            return created;
        });
        doAnswer(inv -> {
            assertThat(transactions.active).isTrue();
            return null;
        }).when(heads).appointFirstHead(admin, ORG_ID, head);

        assertThat(service.create(admin, request)).isEqualTo(created);
        verify(heads).appointFirstHead(admin, ORG_ID, head);
        assertThat(transactions.rolledBack).isFalse();
    }

    @Test
    @DisplayName("помилка призначення голови відкочує транзакцію разом зі створенням організації")
    void headFailureRollsBackOrganization() {
        when(heads.requireActiveUser("head@ukma.edu.ua")).thenReturn(head);
        when(organizations.create(request)).thenReturn(organization(ORG_ID, "FIDo", "fido"));
        doThrow(new IllegalStateException("db error")).when(heads).appointFirstHead(any(), eq(ORG_ID), any());

        assertThatThrownBy(() -> service.create(admin, request)).isInstanceOf(IllegalStateException.class);
        assertThat(transactions.rolledBack).isTrue();
    }

    @Test
    @DisplayName("TC-012: голови не знайдено → 404, транзакцію не відкрито, організацію не створено")
    void unknownHeadCreatesNothing() {
        when(heads.requireActiveUser("head@ukma.edu.ua"))
                .thenThrow(new ApiException(ErrorCode.USER_NOT_FOUND, "not found"));

        assertThatThrownBy(() -> service.create(admin, request))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        assertThat(transactions.opened).isFalse();
        verify(organizations, never()).create(any());
    }

    @Test
    @DisplayName("TC-014: не Global Admin → 403, нічого не перевіряється й не створюється")
    void nonAdminIsRejected() {
        doThrow(new ApiException(ErrorCode.FORBIDDEN, "admin only")).when(policy).requireGlobalAdmin(head);

        assertThatThrownBy(() -> service.create(head, request))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(heads, never()).requireActiveUser(any());
        verify(organizations, never()).create(any());
    }
}

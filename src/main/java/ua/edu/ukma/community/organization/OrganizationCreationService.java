package ua.edu.ukma.community.organization;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import ua.edu.ukma.community.auth.HeadService;
import ua.edu.ukma.community.auth.OrgRolePolicy;
import ua.edu.ukma.community.auth.User;

/**
 * Створення організації разом з першим головою (FR-011, FR-014, TC-012).
 * Обидва записи йдуть в одній транзакції: якщо голову призначити не вдалося, організація теж не з'являється.
 */
@Service
public class OrganizationCreationService {

    private final OrganizationService organizations;
    private final HeadService heads;
    private final OrgRolePolicy policy;
    private final TransactionOperations transactions;

    public OrganizationCreationService(OrganizationService organizations, HeadService heads, OrgRolePolicy policy,
                                       TransactionOperations transactions) {
        this.organizations = organizations;
        this.heads = heads;
        this.policy = policy;
        this.transactions = transactions;
    }

    public Organization create(User actor, OrganizationCreateRequest request) {
        policy.requireGlobalAdmin(actor);
        // перевіряємо голову до транзакції: типова помилка (немає такого користувача) не відкриває її взагалі
        User head = heads.requireActiveUser(request.headEmail());
        return transactions.execute(status -> {
            Organization created = organizations.create(request);
            heads.appointFirstHead(actor, created.id(), head);
            return created;
        });
    }
}

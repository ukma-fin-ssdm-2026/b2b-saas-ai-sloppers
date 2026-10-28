package ua.edu.ukma.community.auth;

import org.springframework.stereotype.Service;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


@Service
public class HeadService {

    private final HeadRepository heads;
    private final UserRepository users;
    private final OrgRolePolicy policy;
    private final Clock clock;

    public HeadService(HeadRepository heads, UserRepository users, OrgRolePolicy policy, Clock clock) {
        this.heads = heads;
        this.users = users;
        this.policy = policy;
        this.clock = clock;
    }

    public List<OrganizationHead> list(User actor, UUID organizationId) {
        policy.requireHeadOrAdmin(actor, organizationId);
        return heads.findHeads(organizationId);
    }


    public OrganizationHead appoint(User actor, UUID organizationId, String email, UUID replacesUserId) {
        policy.requireHeadOrAdmin(actor, organizationId);
        User target = requireActiveUser(email);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (replacesUserId == null) {
            if (!heads.appoint(organizationId, target.id(), actor.id(), now)) {
                throw new ApiException(ErrorCode.ALREADY_HEAD, target.email() + " вже голова цієї організації");
            }
        } else {
            policy.requireGlobalAdmin(actor);
            if (replacesUserId.equals(target.id())) {
                throw ApiException.validation("replacesUserId", "Не можна замінити голову на нього самого",
                        replacesUserId);
            }
            if (heads.replace(organizationId, target.id(), replacesUserId, actor.id(), now)
                    == HeadRepository.RemoveResult.NOT_HEAD) {
                throw headNotFound(replacesUserId);
            }
        }
        return heads.findHeads(organizationId).stream()
                .filter(h -> h.userId().equals(target.id()))
                .findFirst()
                .orElseThrow(() -> headNotFound(target.id()));
    }

    public void remove(User actor, UUID organizationId, UUID userId) {
        policy.requireHeadOrAdmin(actor, organizationId);
        switch (heads.remove(organizationId, userId, actor.id(), OffsetDateTime.now(clock))) {
            case REMOVED -> {
            }
            case NOT_HEAD -> throw headNotFound(userId);
            case LAST_HEAD -> throw new ApiException(ErrorCode.LAST_HEAD,
                    "Це останній голова: спершу призначте іншого або замініть його одним запитом");
            default -> throw new IllegalStateException();
        }
    }

    public List<RoleChange> log(User actor, UUID organizationId) {
        policy.requireHeadOrAdmin(actor, organizationId);
        return heads.findLog(organizationId);
    }

    public void appointFirstHead(User actor, UUID organizationId, User head) {
        heads.appoint(organizationId, head.id(), actor.id(), OffsetDateTime.now(clock));
    }

    public User requireActiveUser(String rawEmail) {
        String email = Emails.normalize(rawEmail);
        return users.findByEmail(email)
                .filter(User::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND,
                        "Користувача " + email + " не знайдено або він ще не підтвердив пошту"));
    }

    public List<UUID> organizationsHeadedBy(User user) {
        return heads.findOrganizationsHeadedBy(user.id());
    }

    private static ApiException headNotFound(UUID userId) {
        return new ApiException(ErrorCode.HEAD_NOT_FOUND, "Користувач " + userId + " не є головою цієї організації");
    }
}

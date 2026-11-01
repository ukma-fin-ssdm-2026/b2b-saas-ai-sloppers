package ua.edu.ukma.community.auth;

import org.springframework.stereotype.Component;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.util.UUID;


@Component
public class OrgRolePolicy {

    private final HeadRepository heads;

    public OrgRolePolicy(HeadRepository heads) {
        this.heads = heads;
    }

    public boolean isHead(User user, UUID organizationId) {
        return heads.isHead(organizationId, user.id());
    }

    public void requireGlobalAdmin(User user) {
        if (!user.globalAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Дія доступна лише Global Admin");
        }
    }

    public void requireHead(User user, UUID organizationId) {
        if (!isHead(user, organizationId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Дія доступна лише голові цієї організації");
        }
    }

    public void requireHeadOrAdmin(User user, UUID organizationId) {
        if (!user.globalAdmin() && !isHead(user, organizationId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Дія доступна голові цієї організації або Global Admin");
        }
    }
}

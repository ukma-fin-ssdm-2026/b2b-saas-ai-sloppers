package ua.edu.ukma.community.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;


@Component
public class CurrentUser {

    private final UserRepository users;
    private final AuthProperties properties;
    private final Clock clock;

    public CurrentUser(UserRepository users, AuthProperties properties, Clock clock) {
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    public User require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Потрібен вхід");
        }
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Недійсний токен");
        }
        User user = users.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Користувача з цього токена не існує"));
        if (user.status() == UserStatus.BLOCKED) {
            throw new ApiException(ErrorCode.ACCOUNT_BLOCKED, "Акаунт заблоковано");
        }
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED, "Підтвердіть пошту кодом з листа");
        }
        return syncGlobalAdmin(user);
    }

    private User syncGlobalAdmin(User user) {
        boolean shouldBeAdmin = properties.isAdminEmail(user.email());
        if (user.globalAdmin() == shouldBeAdmin) {
            return user;
        }
        return users.save(user.withGlobalAdmin(shouldBeAdmin, OffsetDateTime.now(clock)));
    }
}

package ua.edu.ukma.community.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

import java.util.UUID;


@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
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
        return user;
    }
}

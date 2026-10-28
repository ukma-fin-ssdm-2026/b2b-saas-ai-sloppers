package ua.edu.ukma.community.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ua.edu.ukma.community.common.error.ApiException;
import ua.edu.ukma.community.common.error.ErrorCode;

/**
 * Вхід за поштою й паролем (FR-004).
 */
@Service
public class LoginService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    /** Хеш, з яким порівнюємо пароль, коли пошти немає: так відповідь займає стільки ж часу. */
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode("not-a-real-password");
    }

    public TokenResponse login(String rawEmail, String password) {
        User user = users.findByEmail(Emails.normalize(rawEmail)).orElse(null);
        if (user == null) {
            passwords.matches(password, dummyHash);
            throw invalidCredentials();
        }
        if (!passwords.matches(password, user.passwordHash())) {
            throw invalidCredentials();
        }
        if (user.status() == UserStatus.PENDING) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_VERIFIED,
                    "Пошту не підтверджено: введіть код з листа або запросіть новий");
        }
        if (user.status() == UserStatus.BLOCKED) {
            throw new ApiException(ErrorCode.ACCOUNT_BLOCKED, "Акаунт заблоковано");
        }
        return tokens.issue(user);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Неправильна пошта або пароль");
    }
}

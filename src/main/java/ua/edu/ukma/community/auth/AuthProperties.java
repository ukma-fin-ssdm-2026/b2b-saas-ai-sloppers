package ua.edu.ukma.community.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Налаштування Auth (префікс {@code auth.} у {@code application.properties}).
 *
 * @param jwtSecret      секрет для підпису JWT, щонайменше 32 байти; порожній — згенерується при старті
 * @param tokenTtl       строк дії токена (NFR-010: не більше 60 хв)
 * @param adminEmails    пошти, які при реєстрації отримують роль Global Admin
 * @param mailFrom       адреса відправника листів; порожня — береться {@code spring.mail.username}
 * @param allowedDomain  дозволений домен пошти (FR-001)
 * @param codeTtl        строк дії коду підтвердження (US-001: 15 хв)
 * @param codeAttempts   кількість спроб введення коду (US-001: 5)
 * @param resendCooldown мінімальна пауза між надсиланням кодів
 */
@ConfigurationProperties("auth")
public record AuthProperties(
        @DefaultValue("") String jwtSecret,
        @DefaultValue("60m") Duration tokenTtl,
        @DefaultValue List<String> adminEmails,
        @DefaultValue("") String mailFrom,
        @DefaultValue("ukma.edu.ua") String allowedDomain,
        @DefaultValue("15m") Duration codeTtl,
        @DefaultValue("5") int codeAttempts,
        @DefaultValue("60s") Duration resendCooldown
) {

    public boolean isAdminEmail(String normalizedEmail) {
        return adminEmails.stream()
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalizedEmail::equals);
    }
}

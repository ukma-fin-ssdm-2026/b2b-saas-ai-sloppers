package ua.edu.ukma.community.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;
import java.util.Locale;


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

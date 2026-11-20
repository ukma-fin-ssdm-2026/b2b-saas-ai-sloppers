package ua.edu.ukma.community.moderation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Налаштування модерації. Усі числа з FR-059 і FR-060 вимога прямо називає конфігурованими, тож вони
 * тут, а не в коді.
 */
@ConfigurationProperties("moderation")
public record ModerationProperties(
        @DefaultValue Limits limits,
        @DefaultValue AutoBan autoBan
) {

    /** Ліміти частоти дій (FR-059). Нуль або менше вимикає відповідний ліміт. */
    public record Limits(
            @DefaultValue("1") int adverts,
            @DefaultValue("24h") Duration advertWindow,
            @DefaultValue("3") int replies,
            @DefaultValue("1h") Duration replyWindow
    ) {
    }

    /** Автоматичне блокування (FR-060). Нуль або менше вимикає відповідний тригер. */
    public record AutoBan(
            @DefaultValue("3") int complaints,
            @DefaultValue("3") int filterRejections,
            @DefaultValue("24h") Duration rejectionWindow,
            @DefaultValue("72h") Duration duration
    ) {
    }
}

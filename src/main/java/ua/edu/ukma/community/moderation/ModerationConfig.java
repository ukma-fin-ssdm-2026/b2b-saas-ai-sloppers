package ua.edu.ukma.community.moderation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Вмикає {@link ModerationProperties}: числа з FR-059 і FR-060 задаються конфігурацією. */
@Configuration
@EnableConfigurationProperties(ModerationProperties.class)
public class ModerationConfig {
}

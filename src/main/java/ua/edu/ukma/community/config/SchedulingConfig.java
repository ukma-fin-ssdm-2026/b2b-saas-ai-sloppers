package ua.edu.ukma.community.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Періодичні задачі ({@code @Scheduled}): закриття прострочених оголошень (FR-050) і зняття банів,
 * строк яких вийшов (FR-054).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

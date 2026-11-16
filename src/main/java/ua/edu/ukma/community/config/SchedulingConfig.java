package ua.edu.ukma.community.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Періодичні задачі ({@code @Scheduled}): поки що лише закриття прострочених оголошень (FR-050). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

package ua.edu.ukma.community.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;


@Configuration
public class ClockConfig {

    public static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");

    @Bean
    Clock clock() {
        return Clock.system(KYIV);
    }
}

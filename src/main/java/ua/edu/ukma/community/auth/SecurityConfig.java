package ua.edu.ukma.community.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import ua.edu.ukma.community.common.error.ErrorCode;
import ua.edu.ukma.community.common.error.GlobalExceptionHandler;

import java.io.IOException;
import java.util.List;

import static ua.edu.ukma.community.common.ApiPaths.V1;


@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, ex) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            write(response, objectMapper, ErrorCode.UNAUTHORIZED,
                    "Потрібен вхід: передайте токен у заголовку Authorization: Bearer <token>");
        };
        AccessDeniedHandler forbidden = (request, response, ex) ->
                write(response, objectMapper, ErrorCode.FORBIDDEN, "Недостатньо прав для цієї дії");

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // реєстрація й вхід
                        .requestMatchers(HttpMethod.POST, V1 + "/auth/register", V1 + "/auth/verify",
                                V1 + "/auth/resend-code", V1 + "/auth/login").permitAll()
                        // гості: каталог, події, календар (FR-007)
                        .requestMatchers(HttpMethod.GET, V1 + "/organizations", V1 + "/organizations/{organizationId}",
                                V1 + "/events", V1 + "/events/{eventId}", V1 + "/events/{eventId}/calendar.ics",
                                V1 + "/events/{eventId}/calendar/google").permitAll()
                        // документація й службові
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/v3/api-docs.yaml", "/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden));
        return http.build();
    }

    private static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode code,
                              String message) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), GlobalExceptionHandler.body(code, message, List.of()));
    }
}

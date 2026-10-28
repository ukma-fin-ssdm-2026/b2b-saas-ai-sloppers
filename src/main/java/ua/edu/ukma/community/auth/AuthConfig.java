package ua.edu.ukma.community.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;


@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {

    static final String ISSUER = "student-community";

    private static final Logger LOG = LoggerFactory.getLogger(AuthConfig.class);
    private static final int MIN_SECRET_BYTES = 32;
    private static final String HMAC = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtKey(AuthProperties properties) {
        if (properties.jwtSecret().isBlank()) {
            LOG.warn("AUTH_JWT_SECRET is not set: using a random key, tokens will be invalid after restart");
            byte[] random = new byte[MIN_SECRET_BYTES];
            RANDOM.nextBytes(random);
            return new SecretKeySpec(random, HMAC);
        }
        byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("AUTH_JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        return new SecretKeySpec(secret, HMAC);
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }


    @Bean
    CodeSender codeSender(ObjectProvider<JavaMailSender> mailSender, AuthProperties properties,
                          @Value("${spring.mail.host:}") String mailHost,
                          @Value("${spring.mail.username:}") String mailUsername) {
        JavaMailSender mail = mailSender.getIfAvailable();

        if (mail == null || mailHost.isBlank()) {
            LOG.warn("SMTP is not configured (spring.mail.host): verification codes will be written to the log");
            return new LogCodeSender();
        }
        String from = properties.mailFrom().isBlank() ? mailUsername : properties.mailFrom();
        return new MailCodeSender(mail, from, properties.codeTtl());
    }
}

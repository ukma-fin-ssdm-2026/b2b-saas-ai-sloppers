package ua.edu.ukma.community.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Для розробки й демо: замість листа пише код у лог бекенду. Вмикається, коли SMTP не налаштовано.
 */
public class LogCodeSender implements CodeSender {

    private static final Logger LOG = LoggerFactory.getLogger(LogCodeSender.class);

    @Override
    public void send(String email, String code) {
        LOG.warn("SMTP is not configured. Verification code for {}: {}", email, code);
    }
}

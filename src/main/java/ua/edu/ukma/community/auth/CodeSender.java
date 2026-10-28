package ua.edu.ukma.community.auth;

/**
 * Надсилає код підтвердження на пошту.
 */
public interface CodeSender {

    void send(String email, String code);
}

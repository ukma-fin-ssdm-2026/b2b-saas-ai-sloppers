package ua.edu.ukma.community.auth;


public interface CodeSender {

    /** Код підтвердження пошти після реєстрації (FR-002). */
    void send(String email, String code);

    /**
     * Код для скидання пароля (FR-065). Типово — той самий лист, що й {@link #send}; реалізації з поштою
     * перевизначають текст, щоб лист не казав «ви реєструвалися».
     */
    default void sendPasswordReset(String email, String code) {
        send(email, code);
    }
}

package ua.edu.ukma.community.board;

/** Обмеження полів дошки; ті самі числа стоять у міграції V6. */
final class BoardRules {
    static final int TITLE_MIN = 3;
    static final int TITLE_MAX = 200;
    static final int DESCRIPTION_MAX = 2000;
    static final int SLOTS_MIN = 1;
    static final int SLOTS_MAX = 50;
    static final int CONTACT_MAX = 200;
    static final int MESSAGE_MAX = 500;

    /**
     * Контакт (FR-044): Telegram ({@code @нік} або {@code t.me/нік}), телефон або email.
     * Перевірка формату, а не існування: мета — не дати зберегти «напишіть мені» без способу зв'язку.
     */
    static final String CONTACT_REGEX = "^(@[A-Za-z0-9_]{5,32}"
            + "|(https://)?t\\.me/[A-Za-z0-9_]{5,32}"
            + "|\\+?[0-9][0-9 ()-]{5,18}[0-9]"
            + "|[^@\\s]+@[^@\\s]+\\.[^@\\s]+)$";
    static final String CONTACT_MESSAGE = "Контакт: Telegram (@нік або t.me/нік), телефон або email";

    static final String NOT_BLANK_REGEX = "(?s).*\\S.*";
    static final String NOT_BLANK_MESSAGE = "Поле не може складатися лише з пробілів";

    private BoardRules() {
    }
}

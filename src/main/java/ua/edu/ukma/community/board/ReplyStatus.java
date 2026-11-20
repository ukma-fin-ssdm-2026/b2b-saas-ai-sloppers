package ua.edu.ukma.community.board;

/** Статус відгуку: {@code PENDING} → {@code CONFIRMED} | {@code REJECTED} | {@code WITHDRAWN} | {@code REMOVED}. */
public enum ReplyStatus {
    PENDING,
    /** автор підтвердив, відгукувач бачить контакт (FR-049) */
    CONFIRMED,
    REJECTED,
    /** відгукувач відкликав відгук до рішення автора (FR-053) */
    WITHDRAWN,
    /** прибрав модератор за підтвердженою скаргою (FR-057); на відміну від REJECTED — не рішення автора */
    REMOVED
}

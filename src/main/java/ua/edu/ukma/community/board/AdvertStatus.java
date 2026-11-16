package ua.edu.ukma.community.board;

/** Статус оголошення: {@code ACTIVE} → {@code CLOSED} | {@code DELETED}. */
public enum AdvertStatus {
    ACTIVE,
    /** закрите вручну, за дедлайном або після заповнення місць (FR-050, FR-051) */
    CLOSED,
    /** видалене автором; нікому не показується (FR-051) */
    DELETED
}

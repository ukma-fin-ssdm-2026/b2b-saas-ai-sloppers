package ua.edu.ukma.community.moderation;

/** Стан скарги: {@code OPEN} → {@code RESOLVED} (контент прибрано) | {@code DISMISSED} (FR-057). */
public enum ComplaintStatus {
    OPEN,
    /** скаргу підтверджено, контент прибрано */
    RESOLVED,
    /** скаргу відхилено, контент лишається */
    DISMISSED
}

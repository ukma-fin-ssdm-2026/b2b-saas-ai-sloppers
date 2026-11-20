/**
 * Модерація користувачів і контенту (FR-054–FR-060): скарги, блокування, автофільтр заборонених слів
 * і ліміти частоти дій.
 *
 * <p>До появи цього пакета модерація платформи була розподілена між Auth Module і Bulletin Board —
 * прогалина, зафіксована в {@code docs/architecture/traceability-matrix.md}. Тепер це окремий модуль
 * із двома вузькими межами: {@link ua.edu.ukma.community.moderation.ModerationGate} — те, що запитує
 * в модерації дошка, {@link ua.edu.ukma.community.moderation.ModeratedContent} — те, що модерація
 * запитує в дошки.
 */
package ua.edu.ukma.community.moderation;

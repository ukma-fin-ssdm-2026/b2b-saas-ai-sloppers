/**
 * C-06 Bulletin Board: дошка оголошень (FR-043–FR-053, US-011–US-013).
 *
 * <p>Оголошення ({@link ua.edu.ukma.community.board.Advert}, таблиця {@code advert}) — пошук команди або
 * компанії для дозвілля. Відгук ({@link ua.edu.ukma.community.board.Reply}, таблиця {@code advert_response})
 * — заявка іншого користувача на місце в оголошенні.
 *
 * <p>Два інваріанти, заради яких модуль влаштований саме так:
 * <ul>
 *     <li>контакт автора бачать лише сам автор і підтверджені відгукувачі (FR-049, NFR-009):
 *     рішення приймає {@link ua.edu.ukma.community.board.AdvertView}, а не фронтенд;</li>
 *     <li>підтверджених відгуків не більше, ніж місць (FR-050, NFR-006): усі зміни відгуків ідуть
 *     у транзакції з блокуванням рядка оголошення ({@code SELECT ... FOR UPDATE}).</li>
 * </ul>
 *
 * <p>Блокування користувачів (FR-054, FR-055) — окремий модуль модерації; поки що заблокований акаунт
 * відсікає {@link ua.edu.ukma.community.auth.CurrentUser}.
 */
package ua.edu.ukma.community.board;

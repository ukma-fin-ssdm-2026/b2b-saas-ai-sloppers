# Changelog

Усі значущі зміни в цьому проєкті будуть задокументовані в цьому файлі.

Формат ґрунтується на [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
і цей проєкт дотримується [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Тести JDBC-репозиторіїв на вбудованому PostgreSQL 17 (`embedded-postgres`, без Docker, працює в CI): дошка, користувачі, голови; паралельні підтвердження з кількох екземплярів сервісу (TC-034, `FOR UPDATE`)
- Модуль «Дошка оголошень» (C-06, FR-043–FR-053): оголошення `TEAM`/`LEISURE`, відгуки з підтвердженням і відхиленням, відкликання відгуку, ручне закриття й видалення
- Контакт автора повертається лише автору й підтвердженим відгукувачам; не потрапляє в логи й тіла помилок валідації (NFR-009)
- Автозакриття оголошення, коли заповнено всі місця (в одній транзакції з `SELECT ... FOR UPDATE`, NFR-006) і після дедлайну (`BoardScheduler`, раз на 5 хв)
- Міграція `V6__create_board.sql`: таблиці `advert`, `advert_response`
- Коди помилок `ADVERT_NOT_FOUND`, `REPLY_NOT_FOUND`, `OWN_ADVERT`, `ALREADY_REPLIED`, `ADVERT_CLOSED`
- Ім'я та прізвище користувача (FR-069): необов'язкові поля при реєстрації, `PATCH /auth/me` для зміни; показуються замість ідентифікатора в оголошеннях (`authorName`), відгуках (`userName`), списку голів (`name`) і журналі ролей (`targetName`, `actorName`). Міграція `V7__add_user_names.sql`
- Скидання пароля (FR-065): `POST /auth/password-reset` надсилає код на пошту, `POST /auth/password-reset/confirm` задає новий пароль. Код з тими самими обмеженнями, що й при реєстрації (15 хв, 5 спроб, раз на хвилину); невідома пошта не розкривається
- `newPassword` не повертається в тілі помилки валідації (як `password` і `code`)

## [1.0.1] - 2026-11-08

### Refactoring

- Замініть магічне число на константу в `CalendarExportService.utf8Length` (UTF-8 code point boundaries)
- Витяг надано у спільний доступ `@ImageUploadErrorResponses` анотація для відповідей про помилки OpenAPI при завантаженні зображень
- Замініть магічне число символьною константою та виправте дужки/довжину рядка в `GlobalExceptionHandler`
- Розкласти умовний вираз у `Event.approve` у пойменовані предикати (`fromPending`, `fromUnpublished`, `republishUnverified`)

## [1.0.0] - 2026-11-02

### Added

- Початкова базова версія проміжного випуску
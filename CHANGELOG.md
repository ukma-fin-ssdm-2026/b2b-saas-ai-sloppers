# Changelog

Усі значущі зміни в цьому проєкті будуть задокументовані в цьому файлі.

Формат ґрунтується на [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
і цей проєкт дотримується [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-11-08

### Refactoring

- Замініть магічне число на константу в `CalendarExportService.utf8Length` (UTF-8 code point boundaries)
- Витяг надано у спільний доступ `@ImageUploadErrorResponses` анотація для відповідей про помилки OpenAPI при завантаженні зображень
- Замініть магічне число символьною константою та виправте дужки/довжину рядка в `GlobalExceptionHandler`
- Розкласти умовний вираз у `Event.approve` у пойменовані предикати (`fromPending`, `fromUnpublished`, `republishUnverified`)

## [1.0.0] - 2026-11-02

### Added

- Початкова базова версія проміжного випуску
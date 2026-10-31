# Progress

## Frontend (`web-src`)

- Створено клієнт у [`web-src/`](../../web-src/): React + TypeScript + Vite.
- Робочий компонент: каталог організацій (`OrganizationsPage`) через `GET /api/v1/organizations`
- Vite `server.proxy`: `/api` -> `http://localhost:8080`
- Стани UI: loading, error, empty, success; типи з OpenAPI у `src/types/organization.ts`.
- Інтро при відкритті сторінки (друк тексту -> на каталог)
- Інструкції запуску: [`web-src/README.md`](../../web-src/README.md).

## Backend (`src`)

Коди компонентів відповідають [traceability-matrix.md](../architecture/traceability-matrix.md).

| Код  | Компонент            | Пакет                     | Стан |
| ---- | -------------------- | ------------------------- | ---- |
| C-02 | API Controllers      | `*Controller` у модулях, `common/` | ✅ |
| C-04 | Organizations Module | `organization/`           | ✅ CRUD, пошук, пагінація, автопублікація |
| C-05 | Events & Moderation  | `event/`                  | ✅ дайджест, ручне створення, модерація, прийом від скрейпера |
| C-08 | Calendar Export      | `calendar/`               | ✅ `.ics` (RFC 5545) і посилання Google Calendar |
| C-07 | Scraper Worker       | `scraper/`                | 🟡 контракт `ScrapedEvent` та `EventService.ingest` готові; сам збір пише команда |
| C-09 | PostgreSQL           | `infra/migrations/`       | 🟡 схема `organization` і `event` готова, backend ще працює зі сховищем у пам'яті |
| C-03 | Auth Module          | `auth/`                   | ⏳ межа модуля описана в `package-info.java` |
| C-06 | Bulletin Board       | `board/`                  | ⏳ межа модуля описана в `package-info.java` |
| C-01 | React Web Client     | —                         | ⏳ |

✅ працює · 🟡 частково · ⏳ не розпочато

## 2. Структура коду

Кожен модуль Lab 3 є окремим пакетом (package-by-feature) з однаковою будовою:

| Шар | Що містить | Залежить від Spring |
| --- | ---------- | ------------------- |
| `*Controller` | маршрути, валідація входу, коди відповідей, OpenAPI | так |
| `*Service` | доменна логіка й правила | лише `@Service` |
| доменні `record` (`Organization`, `Event`) | дані й переходи станів | ні |
| `*Repository` | інтерфейс сховища | ні |
| `InMemory*Repository` | тимчасова реалізація сховища | лише `@Repository` |

```
src/main/java/ua/edu/ukma/community/
├── config/          ClockConfig (годинник для тестованості), OpenApiConfig
├── common/          ApiPaths, error/ (єдиний формат помилок), pagination/
├── organization/    C-04: контролер, сервіс, репозиторій, DTO, доменна подія OrganizationDeleted
├── event/           C-05: Event, EventStatus, EventService, 2 контролери, DTO, ScrapedEvent, репозиторій
├── calendar/        C-08: CalendarExportService, CalendarController
├── scraper/         C-07: контракт для команди (package-info.java)
├── auth/            C-03: межа модуля (package-info.java)
└── board/           C-06: межа модуля (package-info.java)
```

Модулі не мають циклічних залежностей. Модуль `event` залежить від `organization`, а не навпаки. Коли організацію видаляють, `OrganizationService` публікує доменну подію `OrganizationDeleted`, а `EventService` видаляє події цієї організації.

## 3. Кінцеві точки

Права доступу описані в контракті (`bearerAuth`, `401`, `403`) і почнуть перевірятися разом з Auth Module.

| Метод | Шлях | Що робить | Вимоги |
| ----- | ---- | --------- | ------ |
| `GET` | `/api/v1/organizations` | каталог: пошук, пагінація | FR-017, FR-018 |
| `GET` | `/api/v1/organizations/{id}` | профіль організації | FR-020 |
| `POST` | `/api/v1/organizations` | створення | FR-014 |
| `PATCH` | `/api/v1/organizations/{id}` | редагування, вмикання автопублікації | FR-016, FR-029 |
| `DELETE` | `/api/v1/organizations/{id}` | видалення разом з подіями | — |
| `GET` | `/api/v1/events` | дайджест опублікованих майбутніх подій, фільтри `organizationId`, `from`, `to` | FR-038, FR-039 |
| `GET` | `/api/v1/events/{id}` | деталі; неопублікована подія → `404` | FR-040 |
| `PATCH` | `/api/v1/events/{id}` | редагування до і після публікації | FR-034 |
| `POST` | `/api/v1/events/{id}/approve` | схвалити або позначити автоопубліковану як переглянуту | FR-032, FR-035 |
| `POST` | `/api/v1/events/{id}/reject` | відхилити з черги модерації | FR-035 |
| `POST` | `/api/v1/events/{id}/unpublish` | зняти з публікації | FR-036 |
| `GET` | `/api/v1/organizations/{id}/events` | список голови: `status=PENDING` — черга, `verified=false` — неперевірені | FR-032, FR-033 |
| `POST` | `/api/v1/organizations/{id}/events` | ручне створення, публікується одразу | FR-037 |
| `GET` | `/api/v1/events/{id}/calendar.ics` | файл `.ics` | FR-042 |
| `GET` | `/api/v1/events/{id}/calendar/google` | посилання «Додати в Google Calendar» | FR-041 |
| `GET` | `/actuator/health` | перевірка стану для probes у Minikube | ADR-004 |

Нові коди помилок: `EVENT_NOT_FOUND` (`404`) і `INVALID_STATE_TRANSITION` (`409`), наприклад для повторного зняття з публікації.

### Життєвий цикл події

```
PENDING ──approve──▶ PUBLISHED ──unpublish──▶ UNPUBLISHED
   │                     ▲                         │
   └─reject─▶ REJECTED   └─────────approve─────────┘
```

Переходи перевіряє доменна модель `Event`, а не контролер. Тому правила однакові для API і для скрейпера. `REJECTED` — кінцевий стан. Подія, яку голова створив вручну, одразу `PUBLISHED` і перевірена.

### Контракт для Scraper Worker

Скрейпер збирає з поста **назву події, дату й час початку та посилання на пост** і передає їх так:

```java
eventService.ingest(new ScrapedEvent(organizationId, sourceId, externalPostId, title, startsAt, postUrl));
```

Модуль подій уже гарантує:

- той самий пост із того самого джерела вдруге нічого не створює (FR-025);
- якщо автопублікацію вимкнено, подія потрапляє в чергу модерації (FR-030), а якщо ввімкнено, публікується одразу як неперевірена (FR-031, FR-032);
- назва (3–200 символів), ідентифікатор поста й посилання (лише `http(s)`, без пробілів і переносів рядка) перевіряються в `ScrapedEvent`, до потрапляння в домен.

На боці скрейпера лишаються розклад (FR-023), таймаути й повторні спроби (NFR-007), статус останнього запуску (FR-028) і дублікати з різних джерел (FR-026). Деталі описано в `scraper/package-info.java`.

## 4. Відповідність стандартам якості

**Стандарт кодування:** Google Java Style з двома відхиленнями. Відступ 4 пробіли й рядок до 120 символів, щоб не переформатовувати весь наявний код. Правила лежать у [`config/quality/checkstyle.xml`](../../config/quality/checkstyle.xml).

| Перевірка | Де запускається | Результат |
| --------- | --------------- | --------- |
| Checkstyle 10.26 (стиль, іменування, імпорти, Javadoc публічних типів) | фаза `validate`, падає на першому порушенні | на коді Lab 4 було **35** порушень (немає Javadoc, `import java.util.*`, `if` без дужок, `log` замість константи, довгі рядки); зараз **0** |
| SpotBugs 4.9 + find-sec-bugs 1.14, `effort=Max`, поріг Medium | фаза `verify` | **0** знахідок після двох задокументованих винятків у [`config/quality/spotbugs-exclude.xml`](../../config/quality/spotbugs-exclude.xml) |
| JUnit 5 + MockMvc | фаза `test` | 37 модульних тестів доменної логіки і 16 API-тестів |

Обидва аналізатори підключено в `pom.xml`, тому workflow **CI** (`./mvnw verify`) запускає їх на кожному PR без змін у `ci.yml`. Повний розбір знахідок, зокрема низькопріоритетних, має бути в [`static-analysis.md`](static-analysis.md).


## Використання ШІ

Цей звіт та редакція коду підготовлено за допомогою Claude за вимогами та архітектурою команди.
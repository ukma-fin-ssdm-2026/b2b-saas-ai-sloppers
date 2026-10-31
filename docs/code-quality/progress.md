# Progress

## Frontend (`web-src`)

- Створено клієнт у [`web-src/`](../../web-src/): React + TypeScript + Vite.
- Робочий компонент: каталог організацій (`OrganizationsPage`) через `GET /api/v1/organizations`
- Vite `server.proxy`: `/api` -> `http://localhost:8080`
- Стани UI: loading, error, empty, success; типи з OpenAPI у `src/types/organization.ts`.
- Інтро при відкритті сторінки (друк тексту -> на каталог)
- Модульні тести: Vitest + Testing Library, 14 тестів (`src/api/organizations.test.ts`, `src/pages/OrganizationsPage.test.tsx`); запускаються в CI кроком `npm run test`.
- Інструкції запуску: [`web-src/README.md`](../../web-src/README.md).

Клієнт поки лише читає публічний каталог: форми входу, кабінету голови й черги модерації ще немає, хоча API для них готове. Це найбільший розрив між бекендом і фронтендом на цьому етапі.

## Backend (`src`)

Коди компонентів відповідають [traceability-matrix.md](../architecture/traceability-matrix.md).

| Код  | Компонент            | Пакет                     | Стан |
| ---- | -------------------- | ------------------------- | ---- |
| C-02 | API Controllers      | `*Controller` у модулях, `common/` | ✅ |
| C-03 | Auth Module          | `auth/`                   | ✅ реєстрація з підтвердженням пошти, вхід, JWT (HS256), ролі голів і адміна |
| C-04 | Organizations Module | `organization/`           | ✅ CRUD, пошук, пагінація, автопублікація, логотипи |
| C-05 | Events & Moderation  | `event/`                  | ✅ дайджест, ручне створення, модерація, прийом від скрейпера, картинки |
| C-08 | Calendar Export      | `calendar/`               | ✅ `.ics` (RFC 5545) і посилання Google Calendar |
| C-09 | PostgreSQL           | `infra/migrations/`       | ✅ міграції V1–V4, JDBC-реалізації репозиторіїв; профіль `inmemory` лишається для тестів |
| C-01 | React Web Client     | `web-src/`                | 🟡 каталог організацій працює; форми входу й кабінету голови ще немає |
| C-07 | Scraper Worker       | `scraper/`                | 🟡 контракт `ScrapedEvent` та `EventService.ingest` готові; сам збір пише команда |
| C-06 | Bulletin Board       | `board/`                  | ⏳ межа модуля описана в `package-info.java` |

✅ працює · 🟡 частково · ⏳ не розпочато

Поза початковим переліком Lab 3 з'явився ще один пакет — `storage/`: збереження картинок у Supabase Storage (логотипи організацій, зображення подій) з валідацією формату й розміру. У матриці простежуваності окремого коду компонента він не має, і це розбіжність, яку варто закрити: або додати C-10, або віднести до C-09 як частину шару зберігання.

## 2. Структура коду

Кожен модуль Lab 3 є окремим пакетом (package-by-feature) з однаковою будовою:

| Шар | Що містить | Залежить від Spring |
| --- | ---------- | ------------------- |
| `*Controller` | маршрути, валідація входу, коди відповідей, OpenAPI | так |
| `*Service` | доменна логіка й правила | лише `@Service` |
| доменні `record` (`Organization`, `Event`, `User`) | дані й переходи станів | ні |
| `*Rules`, `OrgRolePolicy` | правила валідації й доступу окремо від сервісів | ні |
| `*Repository` | інтерфейс сховища | ні |
| `Jdbc*Repository` | реалізація поверх PostgreSQL (профіль за замовчуванням) | `@Repository` + `JdbcClient` |
| `InMemory*Repository` | реалізація для тестів і профілю `inmemory` | лише `@Repository` |

```
src/main/java/ua/edu/ukma/community/
├── config/          ClockConfig (годинник для тестованості), OpenApiConfig, TransactionConfig
├── common/          ApiPaths, LogSafe, error/ (єдиний формат помилок), pagination/
├── auth/            C-03: реєстрація, підтвердження пошти, вхід, JWT, ролі голів, SecurityConfig
├── organization/    C-04: контролери (профіль, голови, логотип), сервіси, репозиторії, доменна подія OrganizationDeleted
├── event/           C-05: Event, EventStatus, EventService, 4 контролери, DTO, ScrapedEvent, репозиторії
├── calendar/        C-08: CalendarExportService, CalendarController
├── storage/         збереження картинок у Supabase Storage, валідація формату й розміру
├── scraper/         C-07: контракт для команди (package-info.java)
└── board/           C-06: межа модуля (package-info.java)
```

Кожен репозиторій має дві реалізації: `Jdbc*` для PostgreSQL і `InMemory*` для профілю `inmemory`. Завдяки цьому тести й генерація OpenAPI-специфікації працюють без бази — але SQL у `Jdbc*` через це не покривається тестами (див. [testing-strategy.md](../testing/testing-strategy.md)).

Модулі не мають циклічних залежностей. Модуль `event` залежить від `organization`, а не навпаки. Коли організацію видаляють, `OrganizationService` публікує доменну подію `OrganizationDeleted`, а `EventService` видаляє події цієї організації.

## 3. Кінцеві точки

Права доступу **перевіряються**: типове правило в `auth/SecurityConfig` — `anyRequest().authenticated()`, відкриті лише реєстрація, вхід, читання каталогу (FR-007), Swagger UI і `/actuator/health`. Ролі всередині організації перевіряє `OrgRolePolicy`. Доведено `AuthFlowIntegrationTest`. У попередній редакції цього розділу було написано, що `401`/`403` лише описані в контракті — це вже не так.

| Метод | Шлях | Що робить | Вимоги |
| ----- | ---- | --------- | ------ |
| `POST` | `/api/v1/auth/register` | реєстрація, лист із кодом підтвердження | FR-001, FR-002 |
| `POST` | `/api/v1/auth/verify` | підтвердження пошти кодом | FR-003 |
| `POST` | `/api/v1/auth/resend-code` | повторне надсилання коду | FR-002 |
| `POST` | `/api/v1/auth/login` | вхід, видача JWT | FR-004, NFR-010 |
| `GET` | `/api/v1/auth/me` | свій профіль, ознака адміна, перелік організацій, де користувач голова | FR-005 |
| `GET` | `/api/v1/organizations` | каталог: пошук, пагінація | FR-017, FR-018 |
| `GET` | `/api/v1/organizations/{id}` | профіль організації | FR-020 |
| `POST` | `/api/v1/organizations` | створення | FR-014 |
| `PATCH` | `/api/v1/organizations/{id}` | редагування, вмикання автопублікації | FR-016, FR-029 |
| `DELETE` | `/api/v1/organizations/{id}` | видалення разом з подіями (лише Global Admin) | — |
| `PUT` | `/api/v1/organizations/{id}/logo` | завантаження логотипа | FR-014, FR-016 |
| `DELETE` | `/api/v1/organizations/{id}/logo` | видалення логотипа | FR-016 |
| `GET` | `/api/v1/organizations/{id}/heads` | перелік голів організації | FR-005 |
| `POST` | `/api/v1/organizations/{id}/heads` | голова призначає іншого голову | FR-009 |
| `DELETE` | `/api/v1/organizations/{id}/heads/{userId}` | зняття голови; останнього зняти не можна → `409 LAST_HEAD` | FR-010, FR-011 |
| `GET` | `/api/v1/organizations/{id}/role-log` | журнал змін ролей | FR-013 |
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
| `PUT` | `/api/v1/events/{id}/image` | завантаження зображення події | FR-034 |
| `DELETE` | `/api/v1/events/{id}/image` | видалення зображення | FR-034 |
| `GET` | `/actuator/health` | перевірка стану для probes у Minikube | ADR-004 |

Разом 28 кінцевих точок API плюс `/actuator/health`.

Коди помилок, що з'явилися разом із модулями: `EVENT_NOT_FOUND` (`404`), `INVALID_STATE_TRANSITION` (`409`, наприклад для повторного зняття з публікації), `UNAUTHORIZED` (`401`), `FORBIDDEN` (`403`), `ACCOUNT_NOT_VERIFIED` (`403`), `USER_NOT_FOUND` (`404`), `LAST_HEAD` (`409`).

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

**Стандарт кодування:** власний набір правил Checkstyle; базові величини як у конвенціях Spring Framework (відступ 4 пробіли, рядок до 120 символів). Правила з поясненнями лежать у [`config/quality/checkstyle.xml`](../../config/quality/checkstyle.xml).

Цифри нижче — з прогону на `main` 31.10.2026.

| Перевірка | Де запускається | Результат |
| --------- | --------------- | --------- |
| Checkstyle 10.20.1 (стиль, іменування, імпорти, Javadoc публічних типів) | фаза `validate`, звітний режим | **129** зауважень, усі стильові; 94 з них — наслідок двох налаштувань, а не дефектів у коді |
| SpotBugs 4.8.6 + find-sec-bugs 1.13.0, `effort=Max`, поріг Low | фаза `verify`, звітний режим | **71** знахідка, **2** після чотирьох задокументованих груп винятків у [`config/quality/spotbugs-exclude.xml`](../../config/quality/spotbugs-exclude.xml) |
| JUnit 5 + Mockito + MockMvc | фаза `test` | **115** тестів бекенду (112 модульних, 3 інтеграційних), покриття рядків 62.1% |
| Vitest + Testing Library | `npm run test` у `web-src/` | **14** тестів фронтенду |

Аналізатори підключено в `pom.xml`, але на PR їх запускає окремий workflow **Lint** (`./mvnw verify -DskipTests`); у **CI** вони вимкнені через `-Dcheckstyle.skip -Dspotbugs.skip`, щоб не виконуватись двічі. Повний розбір знахідок, рішення щодо кожної та перелік відкритих дій — у [`static-analysis.md`](static-analysis.md).


## Використання ШІ

Цей звіт та редакція коду підготовлено за допомогою Claude за вимогами та архітектурою команди. Цифри й статуси компонентів оновлено 31.10.2026 за прогоном аналізаторів і тестів.
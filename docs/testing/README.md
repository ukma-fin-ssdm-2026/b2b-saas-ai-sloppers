# Модульні тести та покриття

Стратегія рівнів, воріт і подальших тестів: [testing-strategy.md](testing-strategy.md).

Інструменти бекенду: **JUnit 5**, **Mockito**, **AssertJ** і **JaCoCo**.  
Тести: [`src/test/java/`](../../src/test/java/ua/edu/ukma/community/).

Інструменти фронту: **Vitest**, **Testing Library**, coverage **v8** (поріг рядків **5%**, як у `jacoco-check`).  
Тести поруч із кодом у [`web-src/src/**/*.test.ts(x)`](../../web-src/src/); фікстури — `web-src/src/test/support/testFixtures.ts`.

## Запуск

```bash
./mvnw test                           # швидкий набір (без @Tag("integration")) + звіт JaCoCo, Docker не потрібен
./mvnw verify                         # + повільний набір (Failsafe): Spring-контекст і PostgreSQL у контейнері, потрібен Docker
open target/site/jacoco/index.html    # переглянути звіт локально

cd web-src && npm test                # модульні тести фронту
cd web-src && npm run test:coverage   # + HTML coverage
```

`./mvnw test` падає, якщо покриття рядків нижче порогу `jacoco-check` у [`pom.xml`](../../pom.xml). Зараз це
**50%** при фактичних **62.1%** (піднято з 5% 01.11.2026) — див. перший пункт у
[«Відкритих діях»](testing-strategy.md#відкриті-дії).

Фронтенд тестується окремо:

```bash
cd web-src
npm run test            # 14 тестів (Vitest)
npm run test:coverage   # з покриттям
```

### У CI

Job **Build and Test Backend** у [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) на кожен push і PR
у `main` виконує окремі кроки `Compile` → `Run backend tests` → `Package`, тож у логах одразу видно, що
впало: компіляція чи тести. Далі:

- **Summary запуску** — кількість тестів (усього, failures, errors, skipped), загальне покриття рядків і
  таблиця покриття по класах (рядки й гілки, найменш покриті зверху);
- **артефакт `test-reports`** (завжди, 7 днів) — звіти Surefire і HTML-звіт JaCoCo з підсвіткою рядків.

### Швидкий і повільний набори (Lab 10, бонус 3)

| Набір | Що входить | Чим запускається | Де в CI |
|---|---|---|---|
| Швидкий | усі тести без тегу `integration` | Surefire, `./mvnw test` | job **Build and Test Backend** на кожному PR і push у `main` |
| Повільний | `@Tag("integration")`: `*FlowIntegrationTest` (повний Spring-контекст + MockMvc) і тести на PostgreSQL (`Jdbc*RepositoryTest`, `BoardConcurrencyPostgresTest`) | Failsafe, `./mvnw verify` | job **Integration Tests** лише на push у `main` |

PostgreSQL для повільного набору піднімає Testcontainers ([`support/TestDatabase`](../../src/test/java/ua/edu/ukma/community/support/TestDatabase.java)):
контейнер `postgres:17` через Docker раннера, один на прогін, схема — міграції з `infra/migrations`. Failsafe
запускає JVM з `-Duser.timezone=UTC`, бо драйвер передає в базу часову зону JVM, а застарілу назву
`Europe/Kiev` новий tzdata в образі не знає.

Новий тест, якому потрібна база чи повний Spring-контекст, позначайте `@Tag("integration")`.


### Оновити закомічений звіт

`target/` у `.gitignore`, тому знімок звіту зберігаємо в [`docs/testing/coverage/`](coverage/jacoco/index.html):

```bash
./mvnw test -Dcheckstyle.skip -Dspotbugs.skip
rm -rf docs/testing/coverage/jacoco && cp -r target/site/jacoco docs/testing/coverage/jacoco
git add docs/testing/coverage
```

Шлях важливий: звіт має лежати в `docs/testing/coverage/jacoco/`, бо саме на `coverage/jacoco/index.html`
посилаються обидва документи. Попередня редакція цієї інструкції копіювала в `docs/testing/coverage/` і
ламала посилання.

## Що покрито

16 тестових класів, 115 тестів. Шлях до кожного — `src/test/java/ua/edu/ukma/community/<пакет>/`.

| Модуль | Тестовий клас | Що перевіряється |
| ------ | ------------- | ---------------- |
| `auth` | [`RegistrationServiceTest`](../../src/test/java/ua/edu/ukma/community/auth/RegistrationServiceTest.java) | реєстрація, домен пошти, код підтвердження, прострочення й ліміт спроб (FR-001–FR-003) |
| `auth` | [`LoginServiceTest`](../../src/test/java/ua/edu/ukma/community/auth/LoginServiceTest.java) | вхід, непідтверджений акаунт, видача JWT (FR-004, NFR-010) |
| `auth` | [`HeadServiceTest`](../../src/test/java/ua/edu/ukma/community/auth/HeadServiceTest.java) | призначення й зняття голів, заборона знімати останнього, журнал ролей (FR-005–FR-013) |
| `auth` | [`AuthFlowIntegrationTest`](../../src/test/java/ua/edu/ukma/community/auth/AuthFlowIntegrationTest.java) | **інтеграційний:** наскрізно через HTTP — гість, студент, голова, адмін; `401`, `403`, `409` |
| `organization` | [`OrganizationServiceTest`](../../src/test/java/ua/edu/ukma/community/organization/OrganizationServiceTest.java) | пошук і сортування, отримання за slug, створення, часткове оновлення, унікальність slug, видалення з подією-каскадом |
| `organization` | [`OrganizationCreationServiceTest`](../../src/test/java/ua/edu/ukma/community/organization/OrganizationCreationServiceTest.java) | створення організації разом із призначенням голови (FR-014) |
| `organization` | [`OrganizationLogoServiceTest`](../../src/test/java/ua/edu/ukma/community/organization/OrganizationLogoServiceTest.java) | завантаження й видалення логотипа (FR-016) |
| `organization` | [`InMemoryOrganizationRepositoryTest`](../../src/test/java/ua/edu/ukma/community/organization/InMemoryOrganizationRepositoryTest.java) | `existsBySlug` з виключенням, повторне видалення, пагінація поверх демо-даних |
| `organization` | [`InstagramHandleTest`](../../src/test/java/ua/edu/ukma/community/organization/InstagramHandleTest.java) | розбір і нормалізація посилання на Instagram |
| `event` | [`EventServiceTest`](../../src/test/java/ua/edu/ukma/community/event/EventServiceTest.java) | дайджест (FR-038/039), ручне створення (FR-037), прийом від скрейпера й дедуплікація, модерація, каскадне видалення |
| `event` | [`EventTest`](../../src/test/java/ua/edu/ukma/community/event/EventTest.java) | машина станів модерації (FR-032, FR-035, FR-036), часткове редагування |
| `event` | [`DedupKeyTest`](../../src/test/java/ua/edu/ukma/community/event/DedupKeyTest.java) | ключ дедуплікації: нормалізація назви й 15-хвилинні кошики (FR-025) |
| `storage` | [`ImageServiceTest`](../../src/test/java/ua/edu/ukma/community/storage/ImageServiceTest.java) | перевірка формату й розміру картинки перед збереженням |
| `storage` | [`SupabaseImageStorageTest`](../../src/test/java/ua/edu/ukma/community/storage/SupabaseImageStorageTest.java) | побудова запитів до Supabase Storage поверх `MockRestServiceServer` |
| `common` | [`LogSafeTest`](../../src/test/java/ua/edu/ukma/community/common/LogSafeTest.java) | значення ззовні не можуть підробити рядок у лозі (CWE-117) |
| `common.error` | [`ErrorCodeAnchorTest`](../../src/test/java/ua/edu/ukma/community/common/error/ErrorCodeAnchorTest.java) | якір коду помилки не залежить від локалі JVM — регресія до `DM_CONVERT_CASE` |

## Техніки

**Фікстури.** [`TestFixtures`](../../src/test/java/ua/edu/ukma/community/support/TestFixtures.java) дає
зафіксований `Clock` (21.10.2026 13:00, Київ) і фабрики `organization(...)`, `event(...)`. Завдяки
фіксованому часу перевірки `createdAt`/`updatedAt` і меж дайджесту детерміновані. Спільний стан класу
готується в `@BeforeEach`.

**Моки (Mockito).** `OrganizationServiceTest` і `EventServiceTest` ізолюють сервіс від сховища,
`ApplicationEventPublisher` і `OrganizationService` через `@Mock`. Перевіряємо не лише результат, а й
взаємодії: `verify(..., never()).save(...)` після помилки, `verifyNoInteractions(...)` коли запит має
відсіктися раніше, `ArgumentCaptor` для опублікованої події.

**Заглушки (fakes/stubs).** `InMemoryOrganizationRepositoryTest` підставляє в сервіс справжнє сховище в
пам'яті замість БД, а `ApplicationEventPublisher` — лямбду, що складає події в список.

**Граничні випадки.** Подія рівно «зараз» входить у дайджест, рівно о `to` — ні; `from == to`;
`from` у минулому; назва рівно 3/200 і 201 символ; порожня назва чи ідентифікатор поста; посилання
не `http(s)` або з пробілом; `offset` за межами списку; повторне видалення; локаль `tr_TR`.
Повторювані випадки оформлені як `@ParameterizedTest` (`@CsvSource`, `@EnumSource`, `@ValueSource`).

## Поза обсягом

- **`calendar`** (експорт `.ics` і посилання Google Calendar) окремих тестів не має — покриття 12.5%,
  і це найслабше місце звіту, бо код там чистий і тестувався б найлегше.
- **JDBC-репозиторії** (`JdbcHeadRepository`, `JdbcEventRepository`, `JdbcOrganizationRepository`) —
  192 рядки з нульовим покриттям. SQL виконується лише проти справжнього PostgreSQL, тож закрити їх
  можна лише Testcontainers, а не правками в тестах.
- **`MailCodeSender`** — надсилання листа через SMTP; у тестах підставляється мок `CodeSender`.
- **`common/pagination`** покривається опосередковано через `list`/`digest` сервісів.

Текст та більшість коду тестів оформлено за допомогою Claude
# Модульні тести та покриття

Стратегія рівнів, воріт і подальших тестів: [testing-strategy.md](testing-strategy.md).

Інструменти бекенду: **JUnit 5**, **Mockito**, **AssertJ** і **JaCoCo**.  
Тести: [`src/test/java/`](../../src/test/java/ua/edu/ukma/community/).

Інструменти фронту: **Vitest**, **Testing Library**, coverage **v8** (поріг рядків **5%**, як у `jacoco-check`).  
Тести поруч із кодом у [`web-src/src/**/*.test.ts(x)`](../../web-src/src/); фікстури — `web-src/src/test/support/testFixtures.ts`.

## Запуск

```bash
./mvnw test                           # усі тести + звіт JaCoCo
open target/site/jacoco/index.html    # переглянути звіт локально

cd web-src && npm test                # модульні тести фронту
cd web-src && npm run test:coverage   # + HTML coverage
```

`./mvnw test` падає, якщо покриття рядків нижче порогу `jacoco-check` у [`pom.xml`](../../pom.xml). Зараз це
**5%** — тимчасовий поріг на тестовий період, піднімаємо разом із кількістю тестів.

### У CI

Job **Build and Test Backend** у [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) на кожен push і PR
у `main` виконує окремі кроки `Compile` → `Run backend tests` → `Package`, тож у логах одразу видно, що
впало: компіляція чи тести. Далі:

- **Summary запуску** — кількість тестів (усього, failures, errors, skipped), загальне покриття рядків і
  таблиця покриття по класах (рядки й гілки, найменш покриті зверху);
- **артефакт `test-reports`** (завжди, 7 днів) — звіти Surefire і HTML-звіт JaCoCo з підсвіткою рядків.


### Оновити закомічений звіт

`target/` у `.gitignore`, тому знімок звіту зберігаємо в [`docs/testing/coverage/`](coverage/index.html):

```bash
./mvnw test -Dcheckstyle.skip -Dspotbugs.skip
rm -rf docs/testing/coverage && cp -r target/site/jacoco docs/testing/coverage
git add docs/testing/coverage
```

## Що покрито

| Модуль | Тестовий клас | Що перевіряється |
| ------ | ------------- | ---------------- |
| `organization` | [`OrganizationServiceTest`](../../src/test/java/ua/edu/ukma/community/organization/OrganizationServiceTest.java) | пошук і сортування, отримання за slug, створення, часткове оновлення, унікальність slug, видалення з подією-каскадом |
| `organization` | [`InMemoryOrganizationRepositoryTest`](../../src/test/java/ua/edu/ukma/community/organization/InMemoryOrganizationRepositoryTest.java) | `existsBySlug` з виключенням, повторне видалення, пагінація поверх демо-даних |
| `event` | [`EventServiceTest`](../../src/test/java/ua/edu/ukma/community/event/EventServiceTest.java) | дайджест (FR-038/039), ручне створення (FR-037), прийом від скрейпера й дедуплікація, модерація, каскадне видалення |
| `event` | [`EventTest`](../../src/test/java/ua/edu/ukma/community/event/EventTest.java) | машина станів модерації (FR-032, FR-035, FR-036), часткове редагування |

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

Модульні тести зосереджені на модулях `organization` і `event`, де живе основна доменна логіка.
`calendar` (експорт `.ics` і Google Calendar) окремих тестів поки не має, а `common/pagination`
покривається опосередковано через `list`/`digest` сервісів.

Текст та більшість коду тестів оформлено за допомогою Claude
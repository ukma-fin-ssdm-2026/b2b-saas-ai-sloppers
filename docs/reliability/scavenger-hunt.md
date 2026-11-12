# Reliability Scavenger Hunt

Пошук потенційних проблем із надійністю в бекенді «Студентське ком'юніті та Івенти».

- **Стан коду:** коміт [`18cd339`](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/commit/18cd33988cb3f1bd67f63b8903489911d2024205) від 2026-11-06; усі посилання на рядки ведуть саме на цей коміт
- **Обсяг:** `src/main/java/ua/edu/ukma/community/**`, `src/main/resources/application.properties`, `infra/kube/Services/Backend/deployment.yaml`
- **Метод:** читання коду; застосунок не запускався, сценарії збоїв виведені з коду

## Категорії

| Код | Категорія |
|---|---|
| **A** | Обробка помилок |
| **B** | Зовнішні залежності |
| **C** | Валідація вхідних даних |
| **D** | Потенційні мовчазні збої |
| **E** | Продуктивність і блокування |

## Підсумок

| № | Проблема | Категорія | Критичність |
|---|---|---|---|
| R1 | Розсилка листів підписникам синхронно в запиті та під `synchronized` | E, B | Висока |
| R2 | Можливий нескінченний цикл у `collectKeys` під час видалення організації | E | Висока |
| R3 | `findAll()` і фільтрація в пам'яті на кожен публічний запит | E | Висока |
| R4 | `request_id` у відповіді не потрапляє в логи | A, D | Середня |
| R5 | Мовчазний перехід на заглушки (коди в лог, випадковий JWT-ключ) | D | Висока |
| R6 | Анонси подій губляться без повторних спроб | D, A | Середня |
| R7 | Файли-сироти в Supabase Storage | B, D | Середня |
| R8 | Немає тайм-ауту на SQL-запити | B, E | Середня |
| R9 | Лист із кодом надсилається до збереження в БД | D, B | Середня |
| R10 | Пароль повертається в тілі помилки валідації | A, C | Середня |
| R11 | Помилки клієнта й конфлікти БД віддаються як 500 | A | Низька |

Класифікацію кожної знахідки за ланцюгом «несправність → помилка → відмова» наведено в розділі [Етап 2](#етап-2-класифікація-несправність--помилка--відмова).

---

## R1. Розсилка листів підписникам синхронно в запиті та під `synchronized`

**Категорія:** E (довга операція в обробнику, блокування), B (зовнішній SMTP у критичному шляху)

**Де:** [EventService.java:78-86](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/event/EventService.java#L78-L86), [EventService.java:97-104](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/event/EventService.java#L97-L104), [EventService.java:115-129](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/event/EventService.java#L115-L129), [SubscriptionService.java:61-68](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/subscription/SubscriptionService.java#L61-L68)

```java
// EventService.java
public synchronized Event create(UUID organizationId, EventCreateRequest request) {
    organizations.get(organizationId);
    OffsetDateTime now = OffsetDateTime.now(clock);
    Event created = events.save(new Event(/* ... */));
    subscriptions.notifyNewEvent(created);   // SMTP-розсилка всередині synchronized
    return created;
}

// SubscriptionService.java
for (String email : emails) {
    try {
        mailer.sendNewEvent(email, organization, event);   // послідовно, по одному листу
    } catch (RuntimeException e) { /* ... */ }
}
```

```properties
# application.properties — тайм-аути на КОЖЕН лист
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=10000
spring.mail.properties.mail.smtp.writetimeout=10000
```

**Чому небезпечно:** `synchronized` блокує монітор єдиного біна `EventService` — тобто **всі** операції зміни подій у системі (`create`, `update`, `approve`, `reject`, `unpublish`, `changeImage`, `ingest`). Поки триває розсилка, жоден інший запит на зміну подій не виконається. Час розсилки лінійно залежить від кількості підписників і від стану зовнішнього SMTP-сервера, на який ми не впливаємо.

**Потенційний вплив:**
- SMTP недоступний, 200 підписників → лише на тайм-аутах з'єднання ~200 × 5 с ≈ 17 хв; якщо сервер приймає з'єднання, але «висить» — ще довше. Увесь цей час модерація та створення подій зупинені для всіх користувачів.
- HTTP-клієнт (фронтенд, ingress) відвалюється по тайм-ауту, хоча подія **вже збережена**. Користувач тисне «Створити» ще раз → дубль події.
- Потоки Tomcat накопичуються в очікуванні монітора → вичерпання пулу потоків, сервіс перестає відповідати навіть на читання.

**Як виправити:** розсилати асинхронно після коміту (`@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`), а надійніше — через таблицю-outbox, яку обробляє фоновий воркер.

---

## R2. Можливий нескінченний цикл у `collectKeys` під час видалення організації

**Категорія:** E (можливість нескінченного циклу, довга операція в обробнику)

**Де:** [SupabaseImageStorage.java:115-143](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/storage/SupabaseImageStorage.java#L115-L143), [OrganizationService.java:86-91](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/organization/OrganizationService.java#L86-L91), [OrganizationLogoService.java:47-50](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/organization/OrganizationLogoService.java#L47-L50)

```java
// SupabaseImageStorage.java
private void collectKeys(String folder, int depth, List<String> keys) {
    if (depth > MAX_DEPTH) {          // обмежує лише глибину рекурсії
        return;
    }
    int offset = 0;
    while (true) {                     // а пагінацію — ніщо
        List<Map<String, Object>> page = client.post()
                .uri("/object/list/" + bucket)
                .body(Map.of("prefix", folder, "limit", PAGE_SIZE, "offset", offset))
                .retrieve()
                .body(LIST_TYPE);
        if (page == null || page.isEmpty()) {
            return;
        }
        // ... keys.add(...)
        if (page.size() < PAGE_SIZE) {
            return;
        }
        offset += PAGE_SIZE;
    }
}

// OrganizationService.java
public synchronized void delete(UUID id) {
    if (!organizations.deleteById(id)) { throw notFound(id.toString()); }
    events.publishEvent(new OrganizationDeleted(id));   // слухачі виконуються синхронно
}

// OrganizationLogoService.java
@EventListener
public void onOrganizationDeleted(OrganizationDeleted deleted) {
    images.deleteFolderQuietly(folder(deleted.organizationId()));   // мережеві виклики в потоці запиту
}
```

**Чому небезпечно:** вихід із циклу залежить виключно від того, що поверне сторонній API. Якщо Supabase проігнорує `offset` (зміна API, баг, проксі з кешем) і повертатиме ту саму повну сторінку, цикл ніколи не завершиться, а `keys` ростиме до `OutOfMemoryError`. `MAX_DEPTH` від цього не захищає. До того ж усе це виконується синхронно в HTTP-запиті `DELETE /organizations/{id}` і під `synchronized` монітором `OrganizationService`. Кожна сторінка списку може чекати до 30 с (read timeout).

**Потенційний вплив:** зависання запиту на видалення організації, блокування всіх змін організацій (`create`, `update`, `changeLogo`, `delete`) до рестарту поду, у гіршому разі OOM і падіння поду.

**Як виправити:** обмежити загальну кількість сторінок/ключів (наприклад, 10 000) і логувати перевищення; видаляти папку асинхронно після коміту, а не в обробнику запиту.

---

## R3. `findAll()` і фільтрація в пам'яті на кожен публічний запит

**Категорія:** E (довга операція в обробнику)

**Де:** [EventService.java:42-57](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/event/EventService.java#L42-L57), [OrganizationService.java:34-41](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/organization/OrganizationService.java#L34-L41), [JdbcEventRepository.java:56-61](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/event/JdbcEventRepository.java#L56-L61)

```java
// EventService.digest
List<Event> upcoming = events.findAll().stream()         // SELECT ... FROM event — уся таблиця
        .filter(Event::isPublished)
        .filter(e -> !e.startsAt().isBefore(lowerBound))
        .filter(e -> to == null || e.startsAt().isBefore(to))
        .filter(e -> organizationId == null || organizationId.equals(e.organizationId()))
        .sorted(BY_START)
        .toList();
return OffsetPage.of(upcoming, limit, offset);            // пагінація вже після завантаження всього
```

```sql
-- V2__create_event.sql: індекс існує, але запит його не використовує
CREATE INDEX event_digest_idx ON event (status, starts_at);
```

**Чому небезпечно:** дайджест — публічний ендпоінт для гостей, тобто найнавантаженіший. Кожен запит тягне з БД усі події за всю історію (включно з минулими, відхиленими й неопублікованими), створює для кожної об'єкт `Event` і сортує їх у JVM. Вартість запиту росте лінійно з кількістю подій, а скрапер додає їх автоматично. Аналогічно для списку організацій.

**Потенційний вплив:** зростання латентності та споживання пам'яті з часом; при кількох паралельних запитах — тиск на GC, можливий OOM (ліміт пам'яті поду), довше утримання з'єднань із пулу Hikari, що голодує інші запити.

**Як виправити:** перенести фільтри, сортування й `LIMIT/OFFSET` у SQL (`WHERE status = 'PUBLISHED' AND starts_at >= ? ... ORDER BY starts_at, id LIMIT ? OFFSET ?`), окремим запитом рахувати `total`.

---

## R4. `request_id` у відповіді не потрапляє в логи

**Категорія:** A (обробка помилок), D (збій, який неможливо діагностувати)

**Де:** [GlobalExceptionHandler.java:130-134](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/common/error/GlobalExceptionHandler.java#L130-L134), [GlobalExceptionHandler.java:144-152](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/common/error/GlobalExceptionHandler.java#L144-L152)

```java
@ExceptionHandler(Exception.class)
ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);    // у лозі немає request_id
    return build(ErrorCode.INTERNAL_ERROR,
            "Внутрішня помилка сервера. Повідомте request_id підтримці", List.of());
}

public static ErrorResponse body(ErrorCode code, String message, List<FieldViolation> details) {
    // Якщо в команді є фільтр кореляційного ідентифікатора, він кладе requestId у MDC
    String requestId = MDC.get("requestId");
    if (requestId == null) {                 // фільтра в проєкті немає → завжди null
        requestId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    // ...
}
```

**Чому небезпечно:** у кодовій базі немає жодного фільтра, який кладе `requestId` у MDC (перевірено пошуком `MDC`/`requestId`). Тому id щоразу генерується випадково **після** того, як помилку вже залоговано, і ніде не записується. Повідомлення користувачу обіцяє, що за `request_id` підтримка знайде проблему, але зв'язку між відповіддю і рядком логу немає.

**Потенційний вплив:** будь-яку 500-ку від користувача неможливо зіставити з конкретним стектрейсом; при кількох одночасних помилках розслідування перетворюється на вгадування за часом. Інцидент «щось не працює» не можна відтворити.

**Як виправити:** `OncePerRequestFilter`, що бере `X-Request-Id` із запиту або генерує новий, кладе його в MDC, додає в заголовок відповіді й чистить у `finally`; додати `%X{requestId}` у патерн логування.

---

## R5. Мовчазний перехід на заглушки (коди в лог, випадковий JWT-ключ)

**Категорія:** D (непередбачена поведінка там, де потрібна чітка помилка)

**Де:** [AuthConfig.java:45-58](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/auth/AuthConfig.java#L45-L58), [AuthConfig.java:73-85](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/auth/AuthConfig.java#L73-L85), [LogCodeSender.java:12-15](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/auth/LogCodeSender.java#L12-L15)

```java
// AuthConfig.java
@Bean
SecretKey jwtKey(AuthProperties properties) {
    if (properties.jwtSecret().isBlank()) {
        LOG.warn("AUTH_JWT_SECRET is not set: using a random key, tokens will be invalid after restart");
        byte[] random = new byte[MIN_SECRET_BYTES];
        RANDOM.nextBytes(random);
        return new SecretKeySpec(random, HMAC);
    }
    // ...
}

@Bean
CodeSender codeSender(/* ... */) {
    JavaMailSender mail = mailSender.getIfAvailable();
    if (mail == null || mailHost.isBlank()) {
        LOG.warn("SMTP is not configured (spring.mail.host): verification codes will be written to the log");
        return new LogCodeSender();
    }
    // ...
}

// LogCodeSender.java
LOG.warn("SMTP is not configured. Verification code for {}: {}", LogSafe.of(email), code);
```

**Чому небезпечно:** зручний для локальної розробки fallback вмикається **у будь-якому середовищі**, зокрема в Kubernetes. Достатньо помилки в Secret `backend-secrets` (опечатка в ключі, забули додати змінну) — і застосунок стартує «здоровим», проходить усі health-проби, а проблема видна лише як один WARN серед логів старту. Причому коди підтвердження пишуться в лог відкритим текстом.

**Потенційний вплив:**
- Реєстрація повертає успіх, але жоден користувач не отримує код → ніхто не може активувати акаунт, а помилки в API немає.
- Будь-хто з доступом до логів (`kubectl logs`, система збору логів) може підтвердити чужу пошту.
- Без `AUTH_JWT_SECRET` кожен рестарт/передеплой поду (а в нас `strategy: Recreate`) розлогінює всіх користувачів.

**Як виправити:** дозволяти заглушки лише в профілях `dev`/`test`/`inmemory`; в інших — падати на старті (`IllegalStateException`) з чітким повідомленням, яку змінну не задано.

**Статус:** виправлено в `AuthConfig` (fail fast поза stub-профілями). Тест: `AuthStubGuardTest`. Деталі — у [reliability-report.md](reliability-report.md) § R5.

---

## R6. Анонси подій губляться без повторних спроб

**Категорія:** D (помилки ховаються), A (подвійне перехоплення)

**Де:** [SmtpEventAnnouncementMailer.java:47-51](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/subscription/SmtpEventAnnouncementMailer.java#L47-L51), [SubscriptionService.java:45-69](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/subscription/SubscriptionService.java#L45-L69)

```java
// SmtpEventAnnouncementMailer.java
try {
    mail.send(message);
} catch (MailException e) {
    LOG.error("Failed to send new-event email to {}: {}", LogSafe.of(email), LogSafe.of(e.getMessage()));
    // виняток не прокидається далі
}

// SubscriptionService.java
/** Notify active subscribers that a newly published event exists. Failures are logged, never thrown. */
public void notifyNewEvent(Event event) {
    Organization organization;
    try {
        organization = organizations.get(event.organizationId());
    } catch (RuntimeException e) {          // занадто широке перехоплення
        LOG.warn("Skip subscriber notify ...");
        return;
    }
    // ...
}
```

**Чому небезпечно:** «не валити створення події через пошту» — правильна мета, але реалізовано її як «забути про лист». Немає ні збереження невдалих відправок, ні повторних спроб, ні метрики. Перехоплення `RuntimeException` під час пошуку організації ховає й неочікувані помилки (наприклад, збій БД), видаючи їх за «organization missing» з рівнем WARN.

**Потенційний вплив:** короткочасний збій SMTP (ліміт Gmail на кількість листів, мережевий збій на 5 хв) означає, що частина або всі підписники ніколи не дізнаються про подію. Ні користувачі, ні адміністратори про це не знають; ліміт Gmail при великій розсилці гарантовано «з'їсть» хвіст списку.

**Як виправити:** outbox-таблиця `notification(status, attempts, next_attempt_at, last_error)` із фоновим воркером і експоненційною затримкою; лічильник невдалих відправок у метриках; у `notifyNewEvent` перехоплювати лише `ApiException` з кодом `ORGANIZATION_NOT_FOUND`.

---

## R7. Файли-сироти в Supabase Storage

**Категорія:** B (файлові операції без резервного шляху), D (помилки видалення ховаються)

**Де:** [EventImageService.java:22-35](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/event/EventImageService.java#L22-L35), [OrganizationLogoService.java:23-36](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/organization/OrganizationLogoService.java#L23-L36), [SupabaseImageStorage.java:73-88](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/storage/SupabaseImageStorage.java#L73-L88)

```java
// EventImageService.java
public Event upload(UUID eventId, MultipartFile file) {
    Event current = events.get(eventId);
    current.requireNotRejected("змінити картинку");
    String url = images.store(folder(current), "image", file);   // файл уже в Supabase
    Event updated;
    try {
        updated = events.changeImage(eventId, url);
    } catch (ApiException e) {          // DataAccessException сюди не потрапить
        images.deleteQuietly(url);
        throw e;
    }
    images.deleteQuietly(current.imageUrl());   // при збої — лише WARN
    return updated;
}

// SupabaseImageStorage.java
} catch (RestClientException e) {
    LOG.warn("Supabase Storage delete failed for {}/{}: {}", /* ... */);
}
```

**Чому небезпечно:** операція охоплює дві системи (Supabase і PostgreSQL) без компенсації для всіх випадків збою:
- якщо `changeImage` впаде з помилкою БД (`DataAccessException`, тайм-аут з'єднання, failover Postgres), щойно завантажений файл не видаляється;
- якщо `deleteQuietly` старої картинки не вдасться, файл назавжди лишається в бакеті;
- `events.get` і `changeImage` — два окремі кроки, тож два паралельні завантаження прочитають однаковий `current`, і одне з нових зображень залишиться без посилання.

**Потенційний вплив:** бакет поступово заповнюється файлами, на які немає посилань (квота Supabase, гроші). Бакет публічний, тож «видалений» постер або логотип і далі доступний за прямим посиланням — небажано, якщо картинку прибрали через невідповідний вміст.

**Як виправити:** перехоплювати `RuntimeException` для компенсаційного видалення; невдалі видалення записувати в таблицю `pending_file_deletion` і прибирати фоновою задачею; періодична звірка бакета з БД.

---

## R8. Немає тайм-ауту на SQL-запити

**Категорія:** B (виклики БД без тайм-аутів), E (блокування)

**Де:** [application.properties](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/resources/application.properties), [deployment.yaml:55-63](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/infra/kube/Services/Backend/deployment.yaml#L55-L63)

```properties
# application.properties — про БД лише коментар, жодних тайм-аутів
# PostgreSQL: SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD from the environment (.env, docker-compose)
# немає spring.jdbc.template.query-timeout
# немає statement_timeout у SPRING_DATASOURCE_URL
```

```yaml
# deployment.yaml — тайм-аути Hikari є лише тут, і це тайм-аути з'єднання, а не запиту
- name: SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT
  value: "5000"
- name: SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT
  value: "2000"
```

**Чому небезпечно:** `connection-timeout` обмежує лише очікування вільного з'єднання з пулу. Сам SQL-запит, який завис (блокування рядка, важкий `SELECT` з R3, проблеми з мережею під час failover), виконуватиметься скільки завгодно. Сервісні методи (`EventService`, `OrganizationService`, `RegistrationService`) — `synchronized`, тож один завислий запит блокує всі записи відповідного домену. Поза Kubernetes (docker-compose, локально) діють стандартні значення Hikari, бо налаштування продубльовані лише в маніфесті.

**Потенційний вплив:** каскадне зависання: завислий запит тримає з'єднання та монітор → інші потоки чекають монітора → пул потоків Tomcat вичерпується → под не відповідає, хоча liveness-проба (без БД) каже, що все добре, і рестарту не буде.

**Як виправити:** `spring.jdbc.template.query-timeout=5s` та/або `?options=-c%20statement_timeout%3D5000` у JDBC URL; перенести налаштування Hikari в `application.properties`, щоб вони діяли всюди.

---

## R9. Лист із кодом надсилається до збереження в БД

**Категорія:** D (непередбачена поведінка), B (зовнішній виклик поза узгодженим порядком)

**Де:** [RegistrationService.java:38-55](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/auth/RegistrationService.java#L38-L55), [RegistrationService.java:81-98](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/auth/RegistrationService.java#L81-L98)

```java
public synchronized User register(String rawEmail, String password) {
    // ...
    User user = new User(/* ... */, verification(code, now), now, now);
    codeSender.send(email, code);   // 1. лист пішов
    return users.save(user);        // 2. а збереження може впасти
}

public synchronized void resendCode(String rawEmail) {
    // ...
    String code = newCode();
    codeSender.send(email, code);                                       // новий код у пошті
    users.save(user.withVerification(verification(code, now), now));   // хеш у БД — ще старий, якщо save впаде
}
```

**Чому небезпечно:** надсилання листа — незворотна дія, а запис у БД може не відбутися (збій з'єднання, тайм-аут, порушення унікальності). Крім того, SMTP-виклик (до 25 с на тайм-аутах) виконується всередині `synchronized`, тому всі реєстрації, підтвердження і повторні запити коду в системі вишиковуються в чергу за одним повільним листом.

**Потенційний вплив:**
- `register`: користувач отримує код, але акаунта немає → «Неправильний код» при підтвердженні, при повторній реєстрації — новий лист; плутанина й звернення в підтримку.
- `resendCode`: у пошті код, який ніколи не підійде, а cooldown не дає швидко запросити інший.
- Під час проблем із SMTP реєстрація фактично стає однопотоковою.

**Як виправити:** спершу `save`, потім `send` (і при помилці відправки — дозволити `resend` без cooldown); виконувати відправку поза `synchronized`.

---

## R10. Пароль повертається в тілі помилки валідації

**Категорія:** A (небезпечні повідомлення користувачу), C (валідація вхідних даних)

**Де:** [GlobalExceptionHandler.java:46-54](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/common/error/GlobalExceptionHandler.java#L46-L54), [AuthRequests.java:24-26](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/auth/AuthRequests.java#L24-L26)

```java
// GlobalExceptionHandler.java
@ExceptionHandler(MethodArgumentNotValidException.class)
ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
    List<FieldViolation> details = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage(),
                    fe.getRejectedValue()))     // значення поля як є, без фільтра
            .collect(Collectors.toCollection(ArrayList::new));
    // ...
}

// AuthRequests.Register
@NotBlank(message = "Пароль обов'язковий")
@Size(min = 8, max = 72, message = "Пароль: від 8 до 72 символів")
String password
```

Відповідь на `POST /api/v1/auth/register` з паролем `qwerty1`:

```json
{ "error": { "code": "VALIDATION_ERROR",
  "details": [ { "field": "password", "message": "Пароль: від 8 до 72 символів", "value": "qwerty1" } ] } }
```

**Чому небезпечно:** обробник однаково повертає `rejectedValue` для будь-якого поля, включно з паролем і кодом підтвердження. Тіла помилок часто зберігаються там, де пароль бути не повинен: логи API-шлюзу/ingress, фронтенд-трекери помилок (Sentry), HAR-файли, скриншоти в баг-репортах. Користувачі нерідко повторно використовують паролі.

**Потенційний вплив:** витік паролів (у т. ч. від інших сервісів) у сторонні системи логування; порушення NFR щодо зберігання секретів.

**Як виправити:** не повертати `value` для чутливих полів (список `password`, `code`, або анотація `@Sensitive` на полі); простіше — не повертати `rejectedValue` взагалі для тіла запиту.

---

## R11. Помилки клієнта й конфлікти БД віддаються як 500

**Категорія:** A (обробка помилок)

**Де:** [ImageService.java:48-62](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/storage/ImageService.java#L48-L62), [OrganizationService.java:56-61](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/organization/OrganizationService.java#L56-L61), [RegistrationService.java:45-54](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/auth/RegistrationService.java#L45-L54), [GlobalExceptionHandler.java:130-134](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/common/error/GlobalExceptionHandler.java#L130-L134)

```java
// ImageService.java — обірване завантаження від клієнта
try {
    return file.getBytes();
} catch (IOException e) {
    throw new UncheckedIOException(e);   // → handleUnexpected → 500 + ERROR зі стектрейсом
}

// OrganizationService.java — check-then-act, захищений лише synchronized у межах однієї JVM
public synchronized Organization create(OrganizationCreateRequest request) {
    ensureSlugFree(request.slug(), null);              // SELECT
    // ...
    return organizations.save(/* ... */);              // INSERT; UNIQUE (slug) у БД
}
```

**Чому небезпечно:** у `GlobalExceptionHandler` немає окремих обробників для `UncheckedIOException` і `DataIntegrityViolationException`, тож вони потрапляють у загальний `handleUnexpected`. Перевірки «slug вільний» і «пошта вільна» коректні лише доки працює рівно один под (`replicas: 1`); при другому інстансі, ручному записі в БД чи конкурентному запиті з іншого шляху спрацює `UNIQUE` у PostgreSQL, і клієнт отримає 500 замість зрозумілого 409.

**Потенційний вплив:** клієнт не може відрізнити свою помилку від збою сервера й повторює запит без шансу на успіх; логи засмічуються ERROR-ами зі стектрейсами від звичайних обривів з'єднання, що ускладнює пошук справжніх збоїв і спрацьовування алертів.

**Як виправити:** обробник `DataIntegrityViolationException` → 409 `CONFLICT` (з розбором імені constraint: `organization_slug_key` → `SLUG_ALREADY_EXISTS`, `app_user_email_key` → `EMAIL_ALREADY_REGISTERED`); `IOException` при читанні файлу → 400 `MALFORMED_REQUEST` з рівнем логування WARN.

---

## Етап 2. Класифікація: несправність → помилка → відмова

- **Несправність (fault)** — джерело дефекту в коді.
- **Помилка (error)** — некоректний внутрішній стан, який виникає, коли несправність активується.
- **Відмова (failure)** — неправильна поведінка системи назовні: що бачить користувач або інша система.

Більшість знахідок — несправності, чия помилка ще не дійшла до відмови: вони спрацьовують лише за певних умов (збій SMTP, багато підписників, неповний Secret, зростання даних). Тому рядок «Відмова» описує, що побачить користувач, коли ланцюг замкнеться.

### R1. Розсилка листів синхронно під `synchronized`

| Поле | Значення |
|---|---|
| **Несправність** | `EventService.create/approve/ingest` (`synchronized`) напряму викликають `notifyNewEvent`, який у циклі по одному надсилає листи через SMTP |
| **Помилка** | Потік запиту хвилинами тримає монітор `EventService`, чекаючи тайм-аутів SMTP; подія вже записана в БД, а відповідь ще не сформована; інші потоки стоять у черзі на монітор |
| **Відмова** | Створення та модерація подій «висять» і завершуються тайм-аутом шлюзу (504), хоча подія вже створена; повторне натискання створює дубль; поки триває розсилка, будь-які зміни подій у системі недоступні |
| **Критичність** | Висока |

### R2. Нескінченний цикл у `collectKeys`

| Поле | Значення |
|---|---|
| **Несправність** | `while (true)` у `SupabaseImageStorage.collectKeys` без ліміту сторінок; виклик іде синхронно з `OrganizationService.delete` під `synchronized` |
| **Помилка** | Supabase повертає повну сторінку незалежно від `offset` → `offset` росте, список `keys` без кінця наповнюється дублікатами; монітор `OrganizationService` не звільняється |
| **Відмова** | `DELETE /organizations/{id}` ніколи не відповідає; створення, редагування організацій і зміна логотипів зависають для всіх; згодом `OutOfMemoryError` і рестарт поду — короткий простій усього бекенду |
| **Критичність** | Висока |

### R3. `findAll()` і фільтрація в пам'яті

| Поле | Значення |
|---|---|
| **Несправність** | `EventService.digest` і `OrganizationService.list` викликають `findAll()` і фільтрують, сортують та пагінують у Java замість SQL |
| **Помилка** | На кожен запит у пам'яті створюється об'єкт для кожного рядка таблиці (включно з минулими й відхиленими подіями); з'єднання з пулу Hikari зайняте весь час читання |
| **Відмова** | Публічний дайджест і каталог повільнішають у міру накопичення подій; під навантаженням — 500 через вичерпання пулу з'єднань (`SQLTransientConnectionException`) або OOM поду |
| **Критичність** | Висока |

### R4. `request_id` не потрапляє в логи

| Поле | Значення |
|---|---|
| **Несправність** | Немає фільтра, який кладе `requestId` у MDC; `handleUnexpected` логує виняток без ідентифікатора |
| **Помилка** | `requestId` у тілі відповіді — випадкове значення, не пов'язане з жодним записом у лозі |
| **Відмова** | Користувач передає підтримці `request_id` з помилки 500, а розробник не може знайти відповідний стектрейс: збій неможливо діагностувати, тож він лишається невиправленим |
| **Критичність** | Середня |

### R5. Мовчазний перехід на заглушки

| Поле | Значення |
|---|---|
| **Несправність** | `AuthConfig` вмикає `LogCodeSender` і випадковий JWT-ключ у будь-якому середовищі, якщо не задано `SPRING_MAIL_HOST` / `AUTH_JWT_SECRET` |
| **Помилка** | У продакшені працює заглушка: коди підтвердження пишуться в лог замість пошти; ключ підпису JWT існує лише до рестарту поду |
| **Відмова** | Реєстрація повертає успіх, але лист не приходить — ніхто не може підтвердити акаунт; після кожного деплою всі користувачі отримують 401 і мусять входити знову; коди видно всім, хто має доступ до `kubectl logs` |
| **Критичність** | Висока |

### R6. Анонси подій губляться

| Поле | Значення |
|---|---|
| **Несправність** | `MailException` перехоплюється й лише логується; немає збереження невдалих відправок і повторних спроб; надто широке `catch (RuntimeException)` у `notifyNewEvent` |
| **Помилка** | Невдалий лист ніде не зафіксований як «не надіслано»; система не знає, кого з підписників сповіщено, а кого ні |
| **Відмова** | Підписники не отримують анонс події (частково або всі, якщо спрацював ліміт SMTP); ні користувачі, ні адміністратор про це не дізнаються |
| **Критичність** | Середня |

### R7. Файли-сироти в Supabase Storage

| Поле | Значення |
|---|---|
| **Несправність** | Компенсаційне видалення лише при `ApiException`; `deleteQuietly` ковтає помилки; `events.get` і `changeImage` — неатомарна пара кроків |
| **Помилка** | У бакеті лежить файл, на який немає посилання в БД, або картинка, яку в застосунку вже прибрали |
| **Відмова** | Прибраний постер чи логотип і далі відкривається за прямим посиланням; з часом бакет перевищує квоту Supabase, і завантаження нових картинок повертає 503 `STORAGE_UNAVAILABLE` |
| **Критичність** | Середня |

### R8. Немає тайм-ауту на SQL-запити

| Поле | Значення |
|---|---|
| **Несправність** | Не задано `spring.jdbc.template.query-timeout` / `statement_timeout`; налаштування Hikari є лише в `deployment.yaml` |
| **Помилка** | SQL-запит, що завис на блокуванні рядка або під час failover PostgreSQL, необмежено тримає з'єднання і `synchronized`-монітор сервісу |
| **Відмова** | Запити до відповідного домену (події, організації, реєстрація) зависають; liveness-проба лишається зеленою, тож Kubernetes под не перезапускає — простій триває до ручного втручання |
| **Критичність** | Середня |

### R9. Лист із кодом до збереження в БД

| Поле | Значення |
|---|---|
| **Несправність** | У `register` і `resendCode` виклик `codeSender.send` стоїть перед `users.save`, і все це всередині `synchronized` |
| **Помилка** | У пошті користувача код, якого немає в БД: акаунт не збережено або в БД лишився хеш попереднього коду |
| **Відмова** | При підтвердженні користувач бачить «Неправильний код»; через cooldown не може одразу запросити новий; поки SMTP повільний, реєстрація й підтвердження затримуються для всіх |
| **Критичність** | Середня |

### R10. Пароль у тілі помилки валідації

| Поле | Значення |
|---|---|
| **Несправність** | `handleBodyValidation` повертає `fe.getRejectedValue()` для будь-якого поля, не відфільтровуючи чутливі (`password`, `code`) |
| **Помилка** | Тіло відповіді 400 на `POST /auth/register` містить пароль відкритим текстом |
| **Відмова** | Пароль потрапляє в логи ingress, фронтенд-трекер помилок, HAR-файли й скриншоти баг-репортів, тобто стає видимим третім особам (порушення конфіденційності) |
| **Критичність** | Середня |

### R11. Помилки клієнта й конфлікти БД як 500

| Поле | Значення |
|---|---|
| **Несправність** | Немає обробників `DataIntegrityViolationException` і `UncheckedIOException`; перевірка унікальності slug і пошти захищена лише `synchronized` у межах однієї JVM |
| **Помилка** | Очікувана ситуація (конфлікт унікальності, обрив завантаження клієнтом) потрапляє в `handleUnexpected` як непередбачений виняток |
| **Відмова** | Клієнт отримує 500 «Внутрішня помилка сервера» замість 409 або 400 і марно повторює запит; логи засмічуються ERROR-ами зі стектрейсами |
| **Критичність** | Низька |

---

## Що вже зроблено добре

- Тайм-аути з'єднання та читання для клієнта Supabase ([StorageConfig.java:20-33](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/blob/18cd33988cb3f1bd67f63b8903489911d2024205/src/main/java/ua/edu/ukma/community/storage/StorageConfig.java#L20-L33)) і тайм-аути SMTP.
- `LogSafe` захищає логи від підробки рядків (CWE-117).
- Межі `limit`/`offset` і `@Size` у DTO; `CHECK` на статус події в БД.
- Внутрішні деталі винятків не потрапляють у відповідь: `handleUnexpected` повертає загальне повідомлення.
- Liveness-проба не залежить від БД, тож failover Postgres не перезапускає здоровий под.

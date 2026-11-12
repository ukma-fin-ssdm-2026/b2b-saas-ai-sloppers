
## Виправлення

### R1. Розсилка листів синхронно під `synchronized`

**Реакція: fail safe.** Подія головне, лист другорядне. Краще створити подію й втратити лист (із записом у лог), ніж не відповісти на запит взагалі. Тому користувач завжди отримує створену подію, а розсилка йде у фоні.

**Патерни:** асинхронна обробка в обмеженому пулі (bulkhead), відправка після коміту (`AFTER_COMMIT`), вузький catch (`MailException`), логування кожного збою з підсумком, тайм-аути SMTP (уже були: 5 с на з'єднання, 10 с на читання й запис).

### R2. Нескінченний цикл у `collectKeys`

**Реакція: fail safe.** Організацію треба видалити за будь-якого стану сховища. Файли, які не вдалося прибрати, не шкодять користувачам, тож лишаються «сиротами» і записуються в WARN для ручного прибирання.

**Патерни:** guard clauses (ліміт сторінок і ключів, виявлення повторної сторінки), асинхронне прибирання після коміту в окремому пулі, вузький catch (`RestClientException`), явне логування неповного прибирання замість мовчазного збою.

### R5. Мовчазний перехід на заглушки (коди в лог, випадковий JWT-ключ)

**Реакція: fail fast.** Помилкова конфігурація продакшену (немає `AUTH_JWT_SECRET` або `SPRING_MAIL_HOST`) не повинна давати «здоровий» старт із небезпечними заглушками. Краще впасти на старті з чітким повідомленням, ніж мовчки писати коди в лог і міняти JWT-ключ на кожен рестарт поду.

**Патерни:** fail fast на старті (`IllegalStateException`), обмеження заглушок профілями `dev`/`test`/`inmemory`, явне іменування відсутньої змінної в повідомленні помилки.

**До / після (фрагмент):**

```java
// До: у будь-якому середовищі
if (properties.jwtSecret().isBlank()) {
    LOG.warn("AUTH_JWT_SECRET is not set: using a random key...");
    // random key
}
if (mail == null || mailHost.isBlank()) {
    return new LogCodeSender(); // коди в лог
}

// Після: заглушки лише в stub-профілях
if (properties.jwtSecret().isBlank()) {
    requireDevStubs("AUTH_JWT_SECRET is not set...");
    // random key тільки для dev/test/inmemory
}
if (mail == null || mailHost.isBlank()) {
    requireDevStubs("SPRING_MAIL_HOST is not set...");
    return new LogCodeSender();
}
```


## Режими відмови й відновлення

| Проблема | Реакція | Що бачить користувач під час збою | Як система повертається до норми |
|---|---|---|---|
| R1. SMTP повільний або недоступний | fail safe | Подія створюється й публікується одразу (201). Підписники не отримують лист | Автоматично: наступні події розсилаються, щойно SMTP оживе. Невдалі листи не надсилаються повторно, вони є в логах (`Failed announcing event …`). Якщо черга з 500 задач переповнена, задача відкидається з WARN |
| R2. Supabase Storage повертає ту саму сторінку, висить або падає | fail safe | `DELETE /organizations/{id}` одразу відповідає 204, організація зникає з каталогу. Сайт працює далі | Обхід завжди завершується: не більше 50 сторінок на папку і 10 000 файлів. Файли-сироти записуються в WARN, їх прибирають вручну в Supabase Dashboard за папкою `organizations/<id>` |
| R5. Немає `AUTH_JWT_SECRET` / SMTP поза stub-профілями | fail fast | Поки под не стартував — сайт недоступний (немає «тихої» реєстрації без листів). У логах старту — `IllegalStateException` з іменем змінної | Ручне: додати секрет у `backend-secrets` / `.env`, перезапустити под. Локально: профіль `inmemory` або задати змінні |
| Supabase Storage недоступний (health) | fail safe | Сайт працює, не вантажаться лише картинки. `/actuator/health` → 503, `components.storage = DOWN` | Автоматично: health знову `UP`, щойно бакет доступний. Readiness і liveness не залежать від сховища, тому Kubernetes не перезапускає под |

## Тести (`src/test/java/ua/edu/ukma/community/reliability/`)

| Тест | Тип | Що перевіряє |
|---|---|---|
| `AnnouncementFaultInjectionTest` | fault injection | Дубль SMTP «висить»: дві події створюються менш ніж за 1 с, листи доходять після «оживлення». Дубль SMTP кидає `MailSendException` одному адресату: подія створена, другий підписник лист отримує |
| `OrganizationDeletionErrorHandlingTest` | обробка помилок | Сховище кидає виняток або «висить»: організація видаляється з БД, запит не падає і завершується менш ніж за 1 с |
| `StorageCleanupBoundaryTest` | межі | Повтор повної сторінки → рівно 2 запити списку і DELETE без дублів. Остання сторінка рівно на `PAGE_SIZE` файлів → ще 1 запит. Нескінченні нові сторінки → зупинка на `MAX_KEYS` |
| `AuthStubGuardTest` | обробка помилок / конфіг (R5) | Поза stub-профілями відсутній `AUTH_JWT_SECRET` або SMTP → `IllegalStateException` з іменем змінної; у stub-профілях — випадковий ключ і `LogCodeSender` |
| `StorageHealthFaultInjectionTest` | fault injection (бонус 2) | Supabase 200 → `UP`; 503 → `DOWN`; тайм-аут з'єднання → `DOWN`; деталей помилки назовні немає |
| `HealthEndpointTest` | fault injection (бонус 2) | Сховище `DOWN` → `/actuator/health` 503 і `components.storage = DOWN`; readiness і liveness лишаються `UP` |

Запуск лише цих тестів: `./mvnw test -Dtest='ua.edu.ukma.community.reliability.**'`

У CI вони також йдуть окремим job **Reliability Tests** (бонус 3).

## Бонуси

### Бонус 2. Health check сервісу

- **Що зроблено.** `/actuator/health` перевіряє критичні залежності: БД (`db`, вбудований індикатор Spring Boot), Supabase Storage (`storage`, новий `SupabaseStorageHealthIndicator`) і SMTP (`mail`, вбудований, якщо задано `SPRING_MAIL_HOST`). Кожен компонент показує свій статус (`show-components=always`), але без деталей і текстів помилок (`show-details=never`).
- **Як працює `storage`.** Один запит `GET /storage/v1/bucket/{bucket}` з тайм-аутом 2 с. Успіх → `UP`; 4xx, 5xx, тайм-аут або обрив → `DOWN`, причина йде лише в лог. Без налаштованого Supabase статус `UNKNOWN`, і загальний статус через це не падає. Результат кешується на 5 с, бо ендпоінт публічний.
- **Чому сховища немає в readiness.** Без картинок сайт працює, тож збій Supabase не має виводити под з балансування (graceful degradation). Readiness і далі залежить лише від БД.
- **Доказ.** Fault injection тести `StorageHealthFaultInjectionTest` і `HealthEndpointTest` (див. вище).

### Бонус 3. Тести надійності як обов'язкова перевірка PR

- **Що зроблено.** У [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) додано окремий job **`Reliability Tests`**, який запускає лише пакет `ua.edu.ukma.community.reliability.**` (`./mvnw test -Dtest='ua.edu.ukma.community.reliability.**'`).
- **Як зробити обов'язковим.** Settings → Branches → правило для `main` → Require status checks → додати check з назвою **`Reliability Tests`** (поряд із Build and Test Backend / Frontend).
- **Доказ.** Після мержу цього PR навести посилання на Actions-прогін, де job `Reliability Tests` зелений.
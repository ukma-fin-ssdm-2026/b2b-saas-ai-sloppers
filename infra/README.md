# Інфраструктура

Застосунок розгортається в локальному кластері Minikube: frontend, backend і PostgreSQL. Зміни в кластер доставляє ArgoCD із гілки `deploy`.

## Архітектура

- [Діаграма розгортання](../docs/architecture/uml/deployment.md)
- [ADR-004](../docs/architecture/decisions/ADR-004-deployment-and-gitops.md): Minikube і GitOps
- [ADR-005](../docs/architecture/decisions/ADR-005-database-migrations.md): міграції БД

```
infra/
├── helpers/            up.sh і down.sh: підняти й видалити кластер; db-failover-test.sh: перевірка NFR-004
├── migrations/         SQL-міграції Flyway і Dockerfile їхнього образу
└── kube/
    ├── argocd/         root Application і Applications: cnpg-operator, backend, frontend
    └── Services/
        ├── Backend/    Deployment і Service; database/: кластер БД і Job міграцій
        └── Frontend/   Deployment, Service і Ingress
```

## Що потрібно для запуску

1. Docker. Потрібно 2 CPU, 6 ГБ пам'яті й щонайменше 12 ГБ диска (Docker Desktop: Settings → Resources).
2. Minikube. На macOS `kubectl` встановиться разом із ним:
   ```bash
   brew install minikube
   ```
3. Файл `.env` у корені репозиторію:
   ```bash
   cp .env.example .env
   ```

Обов'язкові змінні:

| Змінна | Що це |
|---|---|
| `GHCR_USERNAME`, `GHCR_TOKEN` | логін власника токена і [токен GitHub (classic)](https://github.com/settings/tokens/new?scopes=read:packages) зі скоупом `read:packages`, щоб завантажувати приватні образи |
| `NGROK_AUTHTOKEN` | [токен ngrok](https://dashboard.ngrok.com/get-started/your-authtoken), у кожного свій |
| `AUTH_JWT_SECRET` | ключ для JWT: `openssl rand -base64 48` |

Решта змінних у `.env.example` необов'язкові. Файл `.env` у git не потрапляє.

## Запуск

```bash
infra/helpers/up.sh
```

Скрипт можна запускати повторно. Він виконує такі кроки:

1. Перевіряє, що встановлено потрібні інструменти, запущено Docker і заповнено `.env`.
2. Запускає Minikube (профіль `community`) з Ingress і перевіряє, що на вузлі є вільне місце.
3. Встановлює ArgoCD.
4. Створює в кластері секрети: логін до GHCR, змінні backend із `.env` і TLS-сертифікат.
5. Застосовує root Application. Далі ArgoCD сам розгортає оператор бази, backend і frontend із гілки `deploy`.
6. Чекає, поки backend і frontend стануть `Healthy`.
7. Запускає тунель ngrok у Docker і виводить публічний URL сайту та доступ до ArgoCD.

## Зупинка

Щоб видалити все:

```bash
infra/helpers/down.sh
```

Скрипт питає підтвердження, зупиняє тунель ngrok і видаляє кластер разом із базою. `.env` і гілка `deploy` лишаються.

Щоб лише призупинити кластер і зберегти дані: `minikube stop -p community`. Повернутися до роботи можна через `up.sh`.

## Перевірка відмовостійкості БД (NFR-004)

[NFR-004](../docs/requirements/requirements.md) вимагає: після падіння основного пода PostgreSQL сервіс відновлює роботу щонайбільше за 30 с і не втрачає дані (TC-041). Перевіряється на піднятому кластері:

```bash
infra/helpers/db-failover-test.sh
```

Що робить скрипт:

1. Перевіряє, що обидва екземпляри БД готові, а под backend має статус `Ready`.
2. Через `kubectl port-forward svc/backend` перевіряє, що `/actuator/health/readiness` і `GET /api/v1/organizations` віддають 200.
3. Пише рядок-маркер у таблицю `failover_probe` на primary і чекає, поки рядок дійде до standby.
4. Видаляє под primary (`--grace-period=0 --force`, тобто імітує краш) і раз на секунду опитує сервіс.
5. Фіксує момент першого збою, момент промоції standby (`.status.currentPrimary`) і момент відновлення: readiness і API тричі поспіль віддають 200 після промоції.
6. Перевіряє, що маркер є на новому primary, і окремо чекає, поки старий под повернеться як standby.

Скрипт завершується з кодом 0, якщо відновлення вклалося в 30 с і маркер на місці. Інакше код 1, а вивід містить стан подів і кластера. Таблиця-маркер видаляється наприкінці; щоб її залишити, передайте `--keep-probe`.

| Змінна | За замовчуванням | Що це |
|---|---|---|
| `NFR_SECONDS` | `30` | ліміт часу на відновлення |
| `MAX_WAIT_SECONDS` | `120` | скільки чекати, щоб побачити реальний час навіть після провалу |
| `FORCE` | `1` | `0`: звичайне видалення пода з graceful shutdown |
| `LOCAL_PORT` | `18080` | локальний порт для port-forward |

Health кешується на 5 с, тож момент збою видно з точністю до кількох секунд. Через це успіхом вважається лише справжній запит до API після промоції. Якщо збою взагалі не видно, скрипт друкує WARN: перемикання пройшло швидше, ніж інтервал опитування.

## Як викочуються зміни

1. Зміни потрапляють у `main`.
2. Ви вручну запускаєте workflow: Actions → **Build and Deploy** → **Run workflow**.
3. Workflow збирає образи backend, frontend і migrations, пушить їх у GHCR і комітить у гілку `deploy` маніфести з хешами (digest) нових образів.
4. ArgoCD стежить за гілкою `deploy` і сам підхоплює зміни. Перевірка відбувається приблизно раз на 3 хвилини.
5. Відповідний Application перезапускає потрібний под із новим образом:
   - **`backend`** (backend і міграції): спершу виконується Job міграцій, потім перезапускається backend. Якщо міграція впала, backend лишається на старій версії.
   - **`frontend`**: перезапускається frontend, без простою.

Гілка `deploy` генерується автоматично, руками її не редагуємо.

# b2b-saas-ai-sloppers

[![CI](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/actions/workflows/ci.yml/badge.svg)](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/actions/workflows/ci.yml)
[![API Docs](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/actions/workflows/api-docs.yml/badge.svg)](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/actions/workflows/api-docs.yml)
[![Lint](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/actions/workflows/lint.yml/badge.svg)](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/actions/workflows/lint.yml)

**Платформа для студентських організацій**: єдиний каталог організацій і подій,
автоматичний збір анонсів з Instagram організацій з подальшою модерацією головою організації,
експорт подій у календар та дошка пропозицій для пошуку команди чи компанії.

Детальний опис ідеї, ролей користувачів і тд: [Project-Description.md](Project-Description.md).

## Демо

- Відеодемонстрація (midterm): посилання в [Labs/midterm-presentation.txt](Labs/midterm-presentation.txt)
- Документація API (GitHub Pages): https://ukma-fin-ssdm-2026.github.io/b2b-saas-ai-sloppers/
- Розгортання в Minikube з публічним доступом через ngrok: [infra/README.md](infra/README.md)

## Команда

| GitHub                                       | Роль               |
| -------------------------------------------- | ------------------ |
| [@mkostinuk](https://github.com/mkostinuk)   | Repo Maintainer    |
| [@AdeptMaks](https://github.com/AdeptMaks)   | CI Maintainer      |
| [@artbutyrin](https://github.com/artbutyrin) | Documentation Lead |
| [@urkarnad](https://github.com/urkarnad)     | Issue Tracker Lead |

Відповідальності, правила комунікації та використання ШІ описані в [TeamCharter.md](TeamCharter.md).

## Командна організація (Lab 1)

- [TeamCharter.md](TeamCharter.md) — ролі, процес, політика якості, розділ «Використання ШІ»
- [docs/issue-workflow.md](docs/issue-workflow.md) — робочий процес задач і мітки
- [.github/workflows/](.github/workflows/) — CI: `ci.yml` (збірка й тести), `lint.yml` (статичний аналіз), `api-docs.yml` / `deploy-api-docs.yml` (OpenAPI), `build-and-deploy.yml` (образи й GitOps-деплой)

## Артефакти вимог (Lab 2)

- [docs/requirements/user-stories.md](docs/requirements/user-stories.md) - користувацькі історії
- [docs/requirements/requirements.md](docs/requirements/requirements.md) - функціональні (FR) і нефункціональні (NFR) вимоги
- [docs/requirements/rtm.md](docs/requirements/rtm.md) - матриця простежуваності (RTM)
- [Project-Description.md](Project-Description.md) - міні-ТЗ: опис ідеї, ролі користувачів, межі системи

## Архітектура (Lab 3)

- [docs/architecture/](docs/architecture/) — огляд папки
- [docs/architecture/high-level-design.md](docs/architecture/high-level-design.md) — високорівневий дизайн
- [docs/architecture/traceability-matrix.md](docs/architecture/traceability-matrix.md) — вимоги ↔ компоненти, сценарії якості
- [docs/architecture/data-model.md](docs/architecture/data-model.md) — модель даних із позначеними чутливими полями
- [docs/architecture/threat-model.md](docs/architecture/threat-model.md) — моделювання загроз за STRIDE
- [docs/architecture/uml/](docs/architecture/uml/) — UML (PlantUML)
- [docs/architecture/decisions/](docs/architecture/decisions/) — ADR

## API (Lab 4)

- [docs/api/api-design.md](docs/api/api-design.md) — проєктування API
- [docs/api/openapi-generated.yaml](docs/api/openapi-generated.yaml) — специфікація OpenAPI, згенерована з коду
- [docs/api/quality-attributes.md](docs/api/quality-attributes.md) — атрибути якості API (зв'язок із NFR)
- [docs/api/validation-checks.md](docs/api/validation-checks.md) — результати перевірки ендпоінтів
- [docs/api/index.html](docs/api/index.html) — Swagger UI, опублікований на [GitHub Pages](https://ukma-fin-ssdm-2026.github.io/b2b-saas-ai-sloppers/)

## Тестування (Lab 6)

- [docs/testing/testing-strategy.md](docs/testing/testing-strategy.md) — стратегія рівнів, покриття, CI
- [docs/testing/README.md](docs/testing/README.md) — як запускати тести та оновлювати звіт JaCoCo
- [docs/testing/debugging-log.md](docs/testing/debugging-log.md) — журнал налагодження (челендж «зламай і відлагодь»)
- [docs/testing/coverage/jacoco/](docs/testing/coverage/jacoco/index.html) — знімок покриття

Локально: `./mvnw test` (поріг рядків — у `pom.xml`, зараз 50%).

## Якість коду (Lab 5)

- [docs/code-quality/static-analysis.md](docs/code-quality/static-analysis.md) — статичний аналіз: Checkstyle, SpotBugs + find-sec-bugs, npm audit
- [docs/code-quality/review-report.md](docs/code-quality/review-report.md) — огляд коду команди noproblem
- [docs/code-quality/progress.md](docs/code-quality/progress.md) — прогрес реалізації

## Валідація (Lab 7)

- [docs/validation/test-plan.md](docs/validation/test-plan.md) — план тестування
- [docs/validation/review-log.md](docs/validation/review-log.md) — журнал взаємного огляду та рішення за зауваженнями
- [docs/validation/comment.md](docs/validation/comment.md) — відгук команди unispace на наш план

## Основні можливості

Детальний стан за компонентами: [docs/code-quality/progress.md](docs/code-quality/progress.md).

**Реалізовано (бекенд):**

- Реєстрація з поштою @ukma.edu.ua і кодом підтвердження, логін (JWT)
- Ролі Гість / User / Голова організації / Global Admin; призначення й зняття голів, журнал змін ролей
- Каталог організацій з пошуком і пагінацією, профіль, логотипи (Supabase Storage)
- Події: створення, редагування, модерація (`PENDING` → `PUBLISHED` / `REJECTED` / `UNPUBLISHED`), фільтри за організацією й датами, постери
- Експорт подій у Google Calendar та `.ics`
- Дедуплікація подій (`DedupKey`) — основа для Scraper Worker

**Реалізовано (фронтенд):** каталог організацій ([web-src/](web-src/)).

**Заплановано на другу половину семестру:**

- Scraper Worker для Instagram (пакет `scraper/` — поки заготовка)
- Автопублікація спаршених подій
- Дошка пропозицій з відгуками, підтвердженням і авто-закриттям (пакет `board/` — поки заготовка)
- UI для входу, кабінету голови й адміністрування (у розробці в гілці `feature/org-event-frontend`)

## Стек

| Частина     | Технології                    |
| ----------- | ----------------------------- |
| Backend     | Java 21, Spring Boot, Maven   |
| База даних  | PostgreSQL                    |
| Frontend    | React, TypeScript, Vite (`web-src/`) |
| CI          | GitHub Actions                |
| Розгортання | Docker, Minikube (Kubernetes), ArgoCD, GHCR |

## Структура репозиторію

```
.
├── .github/workflows/     # CI-конвеєр (GitHub Actions)
├── Labs/                  # посилання на Loom-відео (LabNN/video_<Прізвище>.txt) і midterm-демо
├── config/quality/        # конфіги Checkstyle і SpotBugs
├── docs/
│   ├── issue-workflow.md
│   ├── requirements/      # Lab 2: US, FR, NFR, RTM
│   ├── architecture/      # Lab 3: HLD, UML, ADR, data model, STRIDE
│   ├── api/               # Lab 4: API design, OpenAPI, атрибути якості
│   ├── code-quality/      # Lab 5: статичний аналіз, code review, прогрес
│   ├── testing/           # Lab 6: стратегія, покриття, журнал налагодження
│   ├── validation/        # Lab 7: план тестування, журнал peer review
│   └── db/                # нотатки щодо Supabase
├── infra/
│   ├── helpers/           # up.sh / down.sh: підняти й видалити Minikube-кластер
│   ├── kube/              # маніфести Kubernetes і ArgoCD
│   └── migrations/        # SQL-міграції схеми БД (Flyway)
├── src/                   # backend (Spring Boot)
│   └── main/docker/       # Dockerfile образу backend
├── web-src/               # frontend (React + Vite)
├── docker-compose.yml     # локальне оточення: db + migrate + backend + frontend
├── Project-Description.md
├── TeamCharter.md
└── README.md
```

## Frontend

React-клієнт у [`web-src/`](web-src/). Інструкції: [`web-src/README.md`](web-src/README.md).

Зібраний фронтенд піднімається разом з усім оточенням через `docker compose up --build` (див. [Запуск](#запуск)). Для розробки з hot reload:

```bash
# 1) backend (разом із db і migrate), без контейнера frontend
docker compose up --build backend

# 2) frontend (інший термінал)
cd web-src
npm install
npm run dev
```

UI: http://localhost:5173 — запити `/api/*` проксуються на backend `:8080`.

## Запуск

Потрібен Docker з Compose.

```bash
docker compose up --build
```

Піднімаються чотири сервіси:

| Сервіс    | Що робить                                                             | Адреса                                |
| --------- | --------------------------------------------------------------------- | ------------------------------------- |
| `db`      | PostgreSQL 17                                                         | `localhost:5432`, `community/community` |
| `migrate` | Flyway: накочує `infra/migrations/` і завершується                    | —                                     |
| `backend` | Spring Boot API, стартує лише після успішних міграцій                 | http://localhost:8080/api/v1          |
| `frontend`| React-збірка за nginx; `/api/*` проксує на `backend`                  | http://localhost:3000                 |

Swagger UI: http://localhost:8080/swagger-ui.html

**Нова міграція:** додайте файл `infra/migrations/V<N>__<опис>.sql` і перезапустіть `up`. Уже застосовані файли не редагуйте: Flyway звіряє їхні контрольні суми.

**Скинути базу:** `docker compose down -v`.

### Розгортання в Minikube

Повне оточення (frontend, backend, PostgreSQL через CloudNativePG) розгортається в Minikube, зміни доставляє ArgoCD з гілки `deploy`. Публічний доступ — тунель ngrok.

```bash
cp .env.example .env   # заповнити GHCR_*, NGROK_AUTHTOKEN, AUTH_JWT_SECRET
infra/helpers/up.sh    # виводить публічний URL сайту і доступ до ArgoCD
infra/helpers/down.sh  # видалити кластер
```

Вимоги, змінні й процес викочування: [infra/README.md](infra/README.md). Архітектура: [ADR-004](docs/architecture/decisions/ADR-004-deployment-and-gitops.md), [ADR-005](docs/architecture/decisions/ADR-005-database-migrations.md).

### Віддалена база й картинки (Supabase)

Скопіюйте `.env.example` у `.env` і заповніть паролі. `.env` у `.gitignore`, у репозиторій не потрапляє.

| Змінна | Звідки взяти |
| ------ | ------------ |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Supabase → **Connect** → Direct → JDBC, **Session pooler** |
| `SUPABASE_URL` | `https://<project-ref>.supabase.co` |
| `SUPABASE_SECRET_KEY` | Project Settings → **API Keys** → Secret keys (`sb_secret_...`) |

Без `DB_URL` compose працює з локальним Postgres, як раніше. Без `SUPABASE_*` усе працює, лише завантаження картинок відповідає `503`.

Логотипи організацій і картинки подій лежать у публічному бакеті Supabase Storage `images`, у базі зберігаються лише їхні URL:

```bash
curl -X PUT -F "file=@logo.png" http://localhost:8080/api/v1/organizations/<id>/logo
curl -X PUT -F "file=@poster.jpg" http://localhost:8080/api/v1/events/<id>/image
```

**Без бази взагалі** (in-memory з демо-даними): `./mvnw spring-boot:run -Dspring-boot.run.profiles=inmemory`.

### Реєстрація, вхід і ролі

| Змінна | Що це |
| ------ | ----- |
| `AUTH_JWT_SECRET` | секрет підпису JWT, щонайменше 32 символи (`openssl rand -base64 48`). **Обов'язковий** поза профілями `dev`/`test`/`inmemory`; інакше застосунок не стартує. У stub-профілях без секрету генерується випадковий ключ (після рестарту всім треба увійти знову) |
| `AUTH_ADMIN_EMAILS` | пошти через кому, які при реєстрації отримують роль Global Admin |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP для листів з кодом. **Обов'язкові** поза `dev`/`test`/`inmemory`. У stub-профілях без SMTP код пишеться в лог (`docker compose logs backend` / консоль) |

Сценарій: `POST /api/v1/auth/register` → код з листа (або з логу) → `POST /api/v1/auth/verify` → `POST /api/v1/auth/login` → токен у Swagger UI через кнопку **Authorize**.

Ролі: **Global Admin** створює й видаляє організації та призначає голів будь-якої організації; **голова** керує профілем, подіями й головами своєї організації. Прав голови в адміна автоматично немає: щоб модерувати події, він призначає головою себе. Каталог і події для гостей відкриті без входу.

Організація створюється одразу з першим головою (`headEmail` у `POST /organizations`): це має бути зареєстрований користувач з підтвердженою поштою.

## Специфікація API

[docs/api/openapi-generated.yaml](docs/api/openapi-generated.yaml) генерується з анотацій у коді, руками її не редагують. Після змін у контролерах або DTO перегенеруйте файл і закомітьте:

```bash
./mvnw -Popenapi verify
```

Команда на час генерації піднімає застосунок на порту 8080, тож зупиніть `docker compose` перед запуском. Workflow **API Docs** на кожному PR перегенеровує специфікацію і падає, якщо закомічений файл застарів або невалідний.

Опублікована документація: **https://ukma-fin-ssdm-2026.github.io/b2b-saas-ai-sloppers/** (Swagger UI, оновлюється workflow **Deploy API Docs**). Сервер у специфікації вказано як `http://localhost:8080`, тож «Try it out» працює лише з локально піднятим бекендом.

Статична сторінка в репо: [docs/api/index.html](docs/api/index.html). Локально: `cd docs/api && npx --yes serve -p 5500`.

## Документи

- [Project-Description.md](Project-Description.md): опис проєкту, ролі, функції, міні-ТЗ
- [TeamCharter.md](TeamCharter.md): командний статут
- [docs/issue-workflow.md](docs/issue-workflow.md): робочий процес задач і мітки
- [docs/api/api-design.md](docs/api/api-design.md): проєктування API
- [docs/api/quality-attributes.md](docs/api/quality-attributes.md): атрибути якості API
- [docs/api/openapi-generated.yaml](docs/api/openapi-generated.yaml): специфікація OpenAPI (генерується з коду)
- [docs/architecture/high-level-design.md](docs/architecture/high-level-design.md): архітектурний огляд
- [docs/architecture/traceability-matrix.md](docs/architecture/traceability-matrix.md): вимоги ↔ компоненти
- [docs/architecture/data-model.md](docs/architecture/data-model.md): модель даних
- [docs/architecture/threat-model.md](docs/architecture/threat-model.md): модель загроз (STRIDE)
- [docs/architecture/decisions/](docs/architecture/decisions/): ADR-001…ADR-005
- [docs/code-quality/static-analysis.md](docs/code-quality/static-analysis.md): статичний аналіз і безпека (Lab 5)
- [docs/code-quality/review-report.md](docs/code-quality/review-report.md): огляд коду іншої команди (Lab 5)
- [docs/code-quality/progress.md](docs/code-quality/progress.md): прогрес реалізації (Lab 5)
- [docs/testing/testing-strategy.md](docs/testing/testing-strategy.md): стратегія тестування (Lab 6)
- [docs/testing/debugging-log.md](docs/testing/debugging-log.md): журнал налагодження (Lab 6)
- [docs/validation/test-plan.md](docs/validation/test-plan.md): план тестування (Lab 7)
- [docs/validation/review-log.md](docs/validation/review-log.md): журнал взаємного огляду (Lab 7)

## Використання ШІ

Оформлено за допомогою Claude
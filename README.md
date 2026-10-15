# b2b-saas-ai-sloppers

**Платформа для студентських організацій**: єдиний каталог організацій і подій,
автоматичний збір анонсів із соцмереж організацій з подальшою модерацією координатором,
експорт подій у календар та дошка пропозицій для пошуку команди чи компанії.

Детальний опис ідеї, ролей користувачів і тд: [Project-Description.md](Project-Description.md).

## Команда

| GitHub                                       | Роль               |
| -------------------------------------------- | ------------------ |
| [@mkostinuk](https://github.com/mkostinuk)   | Repo Maintainer    |
| [@AdeptMaks](https://github.com/AdeptMaks)   | CI Maintainer      |
| [@artbutyrin](https://github.com/artbutyrin) | Documentation Lead |
| [@urkarnad](https://github.com/urkarnad)     | Issue Tracker Lead |

Відповідальності, правила комунікації та використання ШІ описані в [TeamCharter.md](TeamCharter.md).

## Артефакти вимог (Lab 2)

- [docs/requirements/user-stories.md](docs/requirements/user-stories.md) - користувацькі історії
- [docs/requirements/requirements.md](docs/requirements/requirements.md) - функціональні (FR) і нефункціональні (NFR) вимоги
- [docs/requirements/rtm.md](docs/requirements/rtm.md) - матриця простежуваності (RTM)

## Архітектура (Lab 3)

- [docs/architecture/](docs/architecture/) — огляд папки
- [docs/architecture/high-level-design.md](docs/architecture/high-level-design.md) — високорівневий дизайн
- [docs/architecture/uml/](docs/architecture/uml/) — UML (PlantUML)
- [docs/architecture/decisions/](docs/architecture/decisions/) — ADR

## Основні можливості

- Каталог організацій з тегами, повнотекстовим пошуком і пагінацією
- Профіль організації
- Автоматичний парсинг подій із соцмереж (Scraper Worker) з дедуплікацією
- Модерація спаршених подій координатором
- Експорт подій у Google Calendar та `.ics`
- Дошка пропозицій з відгуками, підтвердженням і авто-закриттям
- Реєстрація, логін і рольова модель доступу (Global Admin / Coordinator / User)

## Стек

| Частина     | Технології                    |
| ----------- | ----------------------------- |
| Backend     | Java 21, Spring Boot, Maven   |
| База даних  | PostgreSQL                    |
| Frontend    | React, TypeScript, Vite (`web-src/`) |
| CI          | GitHub Actions                |
| Розгортання | Docker, Minikube (Kubernetes) |

## Структура репозиторію

```
.
├── .github/workflows/     # CI-конвеєр (GitHub Actions)
├── docs/
│   ├── issue-workflow.md
│   ├── requirements/      # Lab 2: US, FR, NFR, RTM
│   └── architecture/      # Lab 3: HLD, UML, ADR
├── infra/
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

Поки що backend тримає дані в пам'яті й до бази не підключається. Розгортання в Minikube буде додано окремо.

## Специфікація API

[docs/api/openapi-generated.yaml](docs/api/openapi-generated.yaml) генерується з анотацій у коді, руками її не редагують. Після змін у контролерах або DTO перегенеруйте файл і закомітьте:

```bash
./mvnw -Popenapi verify
```

Команда на час генерації піднімає застосунок на порту 8080, тож зупиніть `docker compose` перед запуском. Workflow **API Docs** на кожному PR перегенеровує специфікацію і падає, якщо закомічений файл застарів або невалідний.

Статична сторінка в репо: [docs/api/index.html](docs/api/index.html). Локально: `cd docs/api && npx --yes serve -p 5500`.

## Документи

- [Project-Description.md](Project-Description.md): опис проєкту, ролі, функції, міні-ТЗ
- [TeamCharter.md](TeamCharter.md): командний статут
- [docs/issue-workflow.md](docs/issue-workflow.md): робочий процес задач і мітки
- [docs/api/api-design.md](docs/api/api-design.md): проєктування API
- [docs/api/quality-attributes.md](docs/api/quality-attributes.md): атрибути якості API
- [docs/code-quality/progress.md](docs/code-quality/progress.md): прогрес реалізації (Lab 5)

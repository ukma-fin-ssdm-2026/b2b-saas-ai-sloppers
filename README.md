# b2b-saas-ai-sloppers

[![Mock CI Pipeline](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/actions/workflows/mock-ci.yml/badge.svg)](https://github.com/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers/actions/workflows/mock-ci.yml)

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
| Frontend    | React                         |
| CI          | GitHub Actions                |
| Розгортання | Docker, Minikube (Kubernetes) |

## Структура репозиторію

```
.
├── .github/workflows/     # CI-конвеєр (GitHub Actions)
├── docs/                  # проєктна документація
│   ├── issue-workflow.md
│   └── requirements/      # Lab 2: US, FR, NFR, RTM
├── src/                   # backend (Spring Boot)
├── Project-Description.md
├── TeamCharter.md
└── README.md
```

## Запуск

_Інструкції з локального запуску та розгортання в Minikube буде додано разом із реалізацією._

## Документи

- [Project-Description.md](Project-Description.md): опис проєкту, ролі, функції, міні-ТЗ
- [TeamCharter.md](TeamCharter.md): командний статут
- [docs/issue-workflow.md](docs/issue-workflow.md): робочий процес задач і мітки

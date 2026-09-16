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
│   └── issue-workflow.md  # робочий процес задач
├── src/                   # вихідний код backend (Spring Boot)
├── pom.xml, mvnw          # збірка backend (Maven Wrapper)
├── Project-Description.md # опис проєкту та міні-ТЗ
├── TeamCharter.md         # командний статут
└── README.md
```

> Структура буде розширюватися: окремі директорії для frontend та маніфестів розгортання
> додаватимуться разом із відповідними частинами проєкту.

## Запуск

_Інструкції з локального запуску та розгортання в Minikube буде додано разом із реалізацією._

## Документи

- [Project-Description.md](Project-Description.md): опис проєкту, ролі, функції, міні-ТЗ
- [TeamCharter.md](TeamCharter.md): командний статут
- [docs/issue-workflow.md](docs/issue-workflow.md): робочий процес задач і мітки

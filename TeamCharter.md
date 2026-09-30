# Командний статут

## 1. Основна інформація
- Назва команди: **b2b-saas-ai-sloppers**
- Учасники:
  - Max Kostyniuk (GitHub: [@mkostinuk](https://github.com/mkostinuk), корпоративна пошта: m.kostyniuk@ukma.edu.ua)
  - Maksim Havrasiienko (GitHub: [@AdeptMaks](https://github.com/AdeptMaks), корпоративна пошта: m.havrasiienko@ukma.edu.ua)
  - Artem Butyrin (GitHub: [@artbutyrin](https://github.com/artbutyrin), корпоративна пошта: a.butyrin@ukma.edu.ua)
  - Daria Hrechko (GitHub: [@urkarnad](https://github.com/urkarnad), корпоративна пошта: d.hrechko@ukma.edu.ua)

## 2. Ролі та відповідальності

### 2.1 Постійні ролі репозиторію (з Lab 1)
- Супроводжувач репозиторію (Repo Maintainer): **Max Kostyniuk** — структура репозиторію, гілкування, захист `main`, злиття PR.
- Супроводжувач CI (CI Maintainer): **Maksim Havrasiienko** — GitHub Actions, статус CI, значок у README.
- Керівник документації (Documentation Lead) / Project Brief Lead: **Artem Butyrin** — README, TeamCharter, актуальність документів і посилань.
- Керівник трекера завдань (Issue Tracker Lead): **Daria Hrechko** — Issues, мітки, призначення, робочий процес задач.

### 2.2 Ролі Lab 2 — інженерія вимог
- Керівник вимог (Requirements Lead): **Daria Hrechko** — користувацькі історії, узгодження формулювань FR.
- Керівник якості (Quality Lead): **Maksim Havrasiienko** — вимірювані NFR (ISO 25010), огляд якості вимог.
- Керівник простежуваності (Traceability Lead): **Max Kostyniuk** — `docs/requirements/rtm.md` (US → FR/NFR → тести).
- Керівник документації вимог: **Artem Butyrin** — структура `/docs/requirements/`, посилання в README.

Кожен учасник пише щонайменше 2 користувацькі історії; авторство зафіксовано в `user-stories.md`.

### 2.3 Ролі Lab 3 — архітектура (команда з 4 осіб)
- Керівник архітектури (Architecture Lead): **Artem Butyrin** — стиль, діаграма компонентів, інтерфейси, координація HLD.
- Керівник документації / ADR (Documentation Lead): **Artem Butyrin** — структура `/docs/architecture/`, ADR, статут, README (поєднано з Architecture Lead).
- Керівник простежуваності вимог→архітектура (Requirements-Architecture Mapper): **Daria Hrechko** — `traceability-matrix.md`.
- Керівник моделі даних і загроз (Data & Threat Modeler): **Maksim Havrasiienko** — `data-model.md`, STRIDE `threat-model.md`.
- Керівник UML (UML Lead): **Max Kostyniuk** — коректність UML; sequence і deployment (компоненти координує Architecture Lead).

Ролі можуть ротуватися за згодою команди. Кожен також пише код.

## 3. План комунікації
- Основний канал: Telegram-чат команди та Discord-канал (оперативні питання, дедлайни, посилання на PR/Issues).
- Розклад зустрічей: щотижня на лабораторній парі + короткі онлайн-синхрони за потреби (15–30 хв).
- Очікування щодо відповідей: відповідати в чаті протягом **24 годин**; блокери позначати явно в чаті або в Issue.

## 4. Співпраця та робочий процес
- Стратегія гілкування: **GitHub Flow** — feature/chore/bug/docs гілки від `main`, інтеграція лише через Pull Request.
- Іменування гілок: `<тип>/<номер-issue>-<короткий-опис>`.
- Практики комітів: conventional commits (`feat`, `fix`, `docs`, `chore`, `ci`, `refactor`, `test`).
- Правила огляду коду: прямий push у `main` заборонено; кожне злиття — через PR з **принаймні 1 схваленням**; CI має бути зеленим перед merge.
- Робочий процес завдань: відкрити Issue → тріаж (label + assignee) → гілка → PR з `Closes #N` → merge → Issue закривається.

### 4.1 Практики вимог (Lab 2)
- Користувацькі історії та зміни FR/NFR/RTM проходять **рев’ю в PR** перед merge у `main`.
- NFR перевіряються на **вимірюваність** (метрика + спосіб перевірки) під час огляду Quality Lead або спільної сесії команди.
- Усі артефакти вимог зберігаються в **`/docs/requirements/`**.
- Documentation Lead підтримує актуальні посилання в **README.md**.

### 4.2 Практики архітектури (Lab 3)
- Архітектурні рішення фіксуються як **ADR** у `/docs/architecture/decisions/` і потрапляють у `main` лише через PR.
- Діаграми UML — PlantUML (`.puml`) у `/docs/architecture/uml/`; зміни рев’юються як код.
- Усі артефакти архітектури — у **`/docs/architecture/`** (HLD, RTM архітектури, data-model, threat-model, uml, decisions).
- README містить розділ із посиланням на `/docs/architecture/`.

## 5. Вирішення конфліктів
- Спочатку обговорити в чаті або на зустрічі команди.
- Якщо згоди немає — голосування більшістю (3 з 4).
- Якщо конфлікт блокує роботу більше ніж 48 годин — ескалувати до викладача курсу.
- Архітектурні розбіжності спочатку фіксуються в чернетці ADR; спірні пункти — на синхроні команди.

## 6. Доступність та робоче навантаження
- Очікувана доступність (годин на тиждень):
  - Max Kostyniuk: 6–7
  - Maksim Havrasiienko: 6–7
  - Artem Butyrin: 6–7
  - Daria Hrechko: 6–7
- Балансування: якщо хтось перевантажений — повідомити в чаті; Issue Tracker Lead і команда перерозподіляють Issues або зменшують scope на тиждень.

## 7. Етична та професійна поведінка
- Дотримуємося правил академічної доброчесності та поважаємо Кодекс етики ACM.
- Не плагіатимо код і документацію; чужий код — лише з ліцензією та атрибуцією.
- Ставимося один до одного з повагою; фідбек у PR — конструктивний і по суті.

## 8. Використання ШІ
- Інструменти: кодові агенти та асистенти (наприклад, Cursor, GitHub Copilot), чат-моделі для чернеток документації, діаграм і ADR.
- Правила розкриття: у артефактах лабораторних додавати короткі нотатки «Використання ШІ».
- Правило верифікації: згенерований код і діаграми проходять **огляд людини** (і тести/CI для коду) перед злиттям; неперевірений згенерований вміст у `main` не потрапляє.

## 9. Індивідуальні Loom-відео
- Кожен учасник записує Loom ≤5 хв зі **своїм** внеском і кладе посилання в `Labs/Lab0X/video_<Прізвище>.txt`.
- **Lab 2:** історії / NFR / RTM / структура docs — за роллю з §2.2.
- **Lab 3:** архітектура / UML / матриця / data+STRIDE / ADR+статут — за роллю з §2.3.
- Перевірка: Issue Tracker Lead або Documentation Lead звіряє наявність файлів перед дедлайном; у відео статуту показують `git log` змін TeamCharter.

## 10. Підписи

Згода з початковим статутом (Lab 1):

- [x] Max Kostyniuk (GitHub: @mkostinuk)
- [x] Maksim Havrasiienko (GitHub: @AdeptMaks)
- [x] Artem Butyrin (GitHub: @artbutyrin)
- [x] Daria Hrechko (GitHub: @urkarnad)

## Використання ШІ

Оновлення статуту (структура ролей Lab 2/3 і формулювання практик); зміст звірено з вимогами Lab02-TeamCharter і Lab 3; переглянуто Documentation Lead.

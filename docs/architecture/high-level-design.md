# High-Level Design

## 1. Призначення

Бекенд-сервіс каталогу студентських організацій і подій, скрейпінгу анонсів, модерації та дошки оголошень (див. [Project-Description.md](../../Project-Description.md), [requirements](../requirements/requirements.md)).

## 2. Архітектурний стиль

**Рішення:** [ADR-001](decisions/ADR-001-architectural-style.md).

| Рівень | Стиль | Що це означає на практиці |
|--------|--------|---------------------------|
| Реалізація | **Клієнт–сервер** + шари всередині бекенду | Браузер запитує наш API; бізнес-логіка і AuthZ — на сервері; дані — у PostgreSQL. |
| Платформа (ціль) | **Мікросервіс** у SmartUkma | Цей сервіс — один автономний доменний сервіс («організації та події»). AuthZ (`account-server`) і UI-рендер (`ui-constructor`) — **окремі** сервіси екосистеми; у семестрі їх замінюємо своїми модулями. |

Чому не «чисті мікросервіси» одразу: команда 4 людини, 10 тижнів, обсяг НаУКМА — зайва операційна складність без виграшу для NFR.

Майбутні залежності (поза реалізацією семестру): `account-server`, `ui-constructor`.

## 3. Компоненти

Діаграма: [uml/components.puml](uml/components.puml) (пояснення: [uml/components.md](uml/components.md)).

| Компонент | Відповідальність | Обсяг |
|-----------|------------------|--------|
| React Web Client | UI: каталоги, модерація, дошка | семестр |
| API Controllers | REST/JSON вхід, маршрутизація до модулів | семестр |
| Auth Module | реєстрація @ukma.edu.ua, логін, JWT, ролі | семестр (пізніше → account-server) |
| Organizations Module | CRUD організацій, голови, каталог/пошук | семестр |
| Events & Moderation | життєвий цикл подій, черга модерації, автопублікація | семестр |
| Bulletin Board | оголошення, відгуки, контакти, автозакриття | семестр |
| Scraper Worker | cron-збір постів, розпізнавання, дедуп | семестр (у межах сервісу) |
| Calendar Export | Google deep link, `.ics` | семестр |
| PostgreSQL | єдине сховище сервісу | семестр |
| Instagram / WhatsApp | джерела анонсів | зовнішнє |
| Google Calendar | відкриття події в календарі користувача | зовнішнє |
| account-server | AuthZ / профілі SmartUkma | future |
| ui-constructor | рендер UI SmartUkma | future |

## 4. Інтерфейси між компонентами

| Від | До | Інтерфейс / протокол | Що передається | Auth |
|-----|-----|----------------------|----------------|------|
| React Client | Auth Module | `IAuth` REST/JSON | реєстрація, логін, токен | — / JWT |
| React Client | Organizations | `IOrganizations` REST/JSON | каталог, профіль, CRUD (Admin/голова) | JWT за роллю |
| React Client | Events & Moderation | `IEvents` REST/JSON | каталог подій, модерація, джерела | гість на читання; JWT на запис |
| React Client | Bulletin Board | `IBoard` REST/JSON | оголошення, відгуки; контакт лише за правилами NFR-009 | JWT |
| React Client | Calendar Export | `ICalendar` REST/JSON | deep link / файл `.ics` | за потреби JWT |
| Modules (Auth, Orgs, Events, Board, Scraper) | PostgreSQL | SQL (JDBC/JPA) | сутності домену | credentials сервісу |
| Scraper Worker | Instagram / WhatsApp | HTTP scrape | сирі пости / метадані | токени джерел (secrets) |
| Calendar Export | Google Calendar | deep link / `.ics` (RFC 5545) | назва, час (Europe/Kyiv), локація | — |
| Scraper Worker | Events Module | внутрішній виклик / запис у БД | чернетки подій «На модерації» / автопублікація | внутрішній |
| account-server → Auth (майбутнє) | — | REST (план) | валідація сесії / ролі | платформний токен |
| ui-constructor → Client (майбутнє) | — | JSON → UI blocks | дані для рендеру | платформний |

Правило семестру: клієнт **не** ходить у БД і **не** скрейпить соцмережі напряму — лише через наш API.

## 5. Пов’язані артефакти

- Рішення: [decisions/](decisions/)
- Вимоги Lab 2: [../requirements/](../requirements/)

## Використання ШІ

Структура HLD, діаграма та таблиця інтерфейсів; звірено з ADR-001, міні-ТЗ і FR/NFR Lab 2.

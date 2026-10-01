# Діаграма послідовності

Файл для здачі Lab 3: **[sequence.puml](sequence.puml)**.

Рендер: розширення PlantUML в IDE або [plantuml.com](https://www.plantuml.com/plantuml).

Сценарій: **скрейпер → модерація → публікація події**. Анонс з Instagram автоматично стає подією, а голова організації контролює, що саме публікується.

```plantuml
@startuml Lab3_Sequence_Diagram
title Скрейпер → модерація → публікація події

autonumber
skinparam shadowing false
skinparam defaultFontSize 13

participant "Instagram" as Insta
participant "Scraper\nWorker" as Scraper
participant "Events &\nModeration" as Events
database "PostgreSQL" as DB
actor "Голова\nорганізації" as Head

Scraper -> Insta : отримати нові пости
activate Scraper
Insta --> Scraper : пости
Scraper -> Events : передати подію
activate Events
Events -> DB : зберегти подію
Events --> Scraper : готово
deactivate Events
deactivate Scraper

alt автопублікація ввімкнена
  Events -> DB : статус «Опубліковано»
else автопублікація вимкнена
  Events -> DB : статус «На модерації»
  Head -> Events : схвалити подію
  activate Events
  Events -> DB : статус «Опубліковано»
  Events --> Head : подію опубліковано
  deactivate Events
end

@enduml
```

## Як читати

- Scraper Worker сам за розкладом (FR-023) забирає пости з Instagram і передає подію в Events & Moderation, який зберігає її в PostgreSQL.
- `alt` розходиться за налаштуванням організації `auto_publish` (FR-029…031): подія або одразу стає опублікованою, або отримує статус «На модерації» і невидима гостям і User (FR-030).
- У другій гілці голова схвалює подію (FR-035), і вона публікується.
- Усі повідомлення синхронні (викликач чекає відповіді). Асинхронних немає, бо брокера повідомлень у системі нема (ADR-003).
- Навмисно спрощено: Web Client і API Controllers між головою та Events не показані, так само дедуплікація, повтори та відмови джерел (NFR-005, NFR-007). Показано лише Instagram, як у `data-model.md`.

## Використання ШІ

Діаграма складена в Claude Code за FR-023…035 і `components.puml`; вибір сценарію й рівня деталізації узгоджено з командою.

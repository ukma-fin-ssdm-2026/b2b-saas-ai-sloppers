# ADR-004: Розгортання в Minikube і GitOps

- **Статус:** Under consideration
- **Дата:** 2026-10-01
- **Пов’язано:** [ADR-001](ADR-001-architectural-style.md), [ADR-003](ADR-003-postgres-and-scraper.md), NFR-003, NFR-004, NFR-005, NFR-010, R-004
- **Діаграма:** [deployment.puml](../uml/deployment.puml)

## Context

Демо працює на Minikube (один вузол). Є одна PostgreSQL (ADR-003) і один бекенд-сервіс, скрейпер працює всередині нього (ADR-001, ADR-003). Середовище одне: ні тестового, ні stage. Потрібні автоматична доставка змін і відмовостійкість БД (NFR-004: якщо БД не відповідає понад 15 с, піднімається репліка, сервіс відновлюється ≤ 30 с).

## Decision

1. **Одиниці розгортання: frontend, backend, PostgreSQL.** Кожна має свій Deployment (або кластер БД). Модулі всередині backend окремо не розгортаються.
2. **Namespace.** Сервіси додатку, Ingress, ConfigMap і Secret — у `default`. Утиліти (ArgoCD, CloudNativePG, ingress-nginx) — у власних стандартних namespace.
3. **Backend: `replicas: 1`.** Scraper Worker (`@Scheduled`) працює всередині backend, при двох подах він стартував би двічі. Живучість — через readiness/liveness probe на `/actuator/health`.
4. **PostgreSQL через оператор CloudNativePG:** ресурс `Cluster` з 2 інстансами (primary + standby, streaming-реплікація, окремий PVC на кожен). Backend ходить у Service `postgres-rw`, який завжди веде на поточний primary. При падінні primary оператор промотує standby.
5. **Вхід: Ingress** (NGINX addon Minikube): назовні веде лише на frontend, TLS. nginx у frontend проксує `/api` на backend усередині кластера (Service типу ClusterIP), тож backend ззовні недоступний.
6. **CI/CD за GitOps:**
   - GitHub Actions на push у `main` збирає образи `frontend` і `backend` і пушить їх у GHCR.
   - Тієї ж миті Actions бере вихідні маніфести з `k8s/` у `main`, підставляє digest нових образів і комітить результат у згенеровану гілку **`deploy`**. У `main` ці коміти не потрапляють.
   - ArgoCD стежить за гілкою `deploy` (`targetRevision: deploy`) і сам застосовує зміни в кластер. Кластер ходить назовні сам, тому Actions не потрібен доступ до Minikube.
   - Відкат — revert коміту в `deploy`.
7. **Міграції БД** — окреме рішення, див. майбутній ADR про міграції.

## Alternatives considered

| Альтернатива | Чому ні зараз |
|--------------|---------------|
| Окремий Deployment на кожен модуль (Auth, Orgs, Events…) | Суперечить ADR-001 і ADR-003 (один сервіс); зайва складність для команди 4 |
| Scraper як окремий Deployment або CronJob | Зайвий артефакт; `@Scheduled` у backend простіший і вже закладений в ADR-003 |
| Один под PostgreSQL + liveness probe | Перезапуск без standby, NFR-004 виконується лише частково |
| Власна реплікація PostgreSQL без оператора | Більше ручної роботи, failover доводиться писати самим |
| Push-деплой з Actions через `kubectl` | Actions не має доступу до Minikube на ноутбуці; немає відкату через git |
| Коміт digest прямо в `main` | Засмічує історію `main` ботовими комітами |
| ArgoCD Image Updater | Ще один компонент; достатньо, щоб CI сам комітив digest |
| Окремий репозиторій для маніфестів | Для команди з чотирьох зайва складність |

## Consequences

**Плюси**
- Кожен сервіс розгортається й відкочується незалежно; історія змін розгортання лежить у git.
- Історія `main` чиста, структура маніфестів має одне джерело правди (`main`).
- Оператор сам піднімає й перемикає репліку БД, що закриває NFR-004 без власних скриптів.

**Мінуси / ризики**
- Обидва поди БД на одному вузлі: захист від падіння пода, але не машини (R-004). Вимогу «≤ 30 с» потрібно перевірити заміром і за потреби підлаштувати оператор.
- `replicas: 1` для backend: доступність тримається на швидкому рестарті. Якщо знадобиться більше реплік, потрібен ShedLock або винесення скрейпера.
- `deploy` — згенерована гілка, руками її редагувати не можна. Боту потрібен дозвіл на пуш у неї (без branch protection або з винятком).
- ArgoCD і оператор споживають ресурси Minikube.
- Секрети не зберігаються в git (NFR-010): створюються в кластері вручну або через SealedSecrets.
- Наявний `mock-ci.yml` буде замінено реальним workflow збирання й публікації образів.

## Додатки

- [deployment.md](../uml/deployment.md)
- [high-level-design.md](../high-level-design.md)

## Використання ШІ

Чернетка ADR у Claude Code; рішення обговорено й ухвалено командою.

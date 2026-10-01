# Діаграма розгортання

Файл для здачі Lab 3: **[deployment.puml](deployment.puml)**.

Рендер: розширення PlantUML в IDE або [plantuml.com](https://www.plantuml.com/plantuml).

Рішення, що за нею стоять: [ADR-004](../decisions/ADR-004-deployment-and-gitops.md).

```plantuml
@startuml Lab3_Deployment_Diagram
title Community Events Service — діаграма розгортання (Minikube)

skinparam shadowing false
skinparam nodesep 50
skinparam ranksep 60
skinparam defaultFontSize 13
skinparam node {
  BorderColor #333333
}

actor "Користувач\n(браузер)" as User

node "Minikube (Kubernetes, 1 вузол)" as K8s {
  component "Ingress\n(NGINX)" as Ingress
  component "ArgoCD" as Argo
  component "CloudNativePG\noperator" as Operator

  node "Frontend" as Front {
    component "nginx + React" as FrontApp
  }

  node "Backend\nreplicas: 1" as Back {
    component "Spring Boot API\n+ Scraper Worker" as BackApp
  }

  node "Кластер БД (CloudNativePG)" as DbCluster {
    node "postgres-1\n(primary)" as Pg1 {
      database "PostgreSQL" as Db1
    }
    node "postgres-2\n(standby)" as Pg2 {
      database "PostgreSQL\n(репліка, read-only)" as Db2
    }
  }
}

cloud "Зовнішні сервіси" as Ext {
  component "Instagram" as Insta
}

node "GitHub" as GH {
  node "Репозиторій\n(main, deploy)" as Repo
  node "GitHub Actions" as Actions
  node "GHCR\n(образи)" as Ghcr
}

User --> Ingress : HTTPS
Ingress --> FrontApp : HTTP
FrontApp --> BackApp : /api\n(лише всередині кластера)
BackApp --> Pg1 : SQL
Pg1 --> Pg2 : streaming\nреплікація
Operator ..> DbCluster : керує,\nпромотує standby
BackApp --> Insta : HTTP scrape

Repo --> Actions : push у main
Actions --> Ghcr : build + push образів
Argo --> Repo : pull маніфестів\n(гілка deploy)
Argo ..> Front : observe
Argo ..> Back
K8s ..> Ghcr : pull образів

note right of Actions
  Після збирання Actions комітить
  digest образів у гілку deploy.
end note

note bottom of Back
  Backend недоступний ззовні:
  його викликає лише Frontend (nginx).
  replicas = 1: Scraper усередині backend,
  при 2 подах стартував би двічі.
end note

note bottom of DbCluster
  Обидва поди на одному вузлі:
  захист від падіння пода, не машини (R-004).
end note

@enduml
```

## Вузли та артефакти

Діаграма навмисно без деталей конфігурації: Service-и (`frontend-svc`, `backend-svc`, `postgres-rw`), Secret-и, ConfigMap, PVC і namespace-и показані лише тут. Сервіси додатку живуть у namespace `default` (одне середовище, без dev/stage), утиліти (ArgoCD, CloudNativePG, ingress-nginx) — у власних стандартних namespace.

| Вузол | Артефакт | Що робить | Вимоги |
|-------|----------|-----------|--------|
| Ingress | NGINX ingress addon Minikube (+ Service `frontend-svc`, `backend-svc`) | Єдина точка входу ззовні, веде лише на frontend; TLS | NFR-010 |
| Deployment `frontend` | образ `frontend` (nginx + React-білд) | Віддає статичний SPA і проксує `/api` на backend усередині кластера (backend ззовні недоступний) | ADR-002 |
| Deployment `backend` (replicas 1) | образ `backend` (Spring Boot) | Доступний лише з frontend (Service типу ClusterIP). API, Auth, Organizations, Events & Moderation, Bulletin Board, Calendar Export + Scraper Worker (`@Scheduled`) | ADR-001, ADR-003, NFR-003 |
| ConfigMap / Secret (на діаграмі не показані) | `backend-config`, секрети | Налаштування, JWT-секрет, токени соцмереж, доступ до БД | NFR-010 |
| CNPG Cluster `postgres` | 2 поди PostgreSQL, кожен зі своїм PVC | primary + standby зі streaming-реплікацією; Service `postgres-rw` завжди веде на primary | NFR-004, NFR-005, NFR-006 |
| CloudNativePG operator | Deployment у `cnpg-system` | Створює поди БД, стежить за ними, промотує standby при падінні primary | NFR-004 |
| ArgoCD | `argocd`, `targetRevision: deploy` | Тягне маніфести з гілки `deploy` і застосовує їх у `default` | ADR-004 |
| GitHub Actions + GHCR | pipeline, образи `frontend` і `backend` | Збирає образи, пушить у GHCR, комітить digest у гілку `deploy` | ADR-004 |

## Використання ШІ

Діаграма й опис складені в Claude Code на основі HLD, ADR-001…003, data-model і NFR; рішення (один Deployment на сервіс, CloudNativePG, GitOps з гілкою `deploy`) ухвалила команда.

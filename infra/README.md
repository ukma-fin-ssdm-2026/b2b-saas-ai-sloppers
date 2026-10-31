## Quick guide for working with local dev and minikube deployment

Рішення: [ADR-004](../docs/architecture/decisions/ADR-004-deployment-and-gitops.md) (Minikube, GitOps), [ADR-005](../docs/architecture/decisions/ADR-005-database-migrations.md) (міграції).

### Структура

```
infra/
├── migrations/                 SQL-міграції Flyway + Dockerfile образу migrations
└── kube/
    ├── argocd/
    │   ├── root.yaml           app-of-apps, застосовується вручну один раз
    │   └── apps/               cnpg-operator (wave -1), backend (0), frontend (1)
    └── Services/
        ├── Backend/            Deployment + Service (wave 2)
        │   └── database/       CNPG Cluster (wave 0) + Flyway Job (Sync hook, wave 1)
        └── Frontend/           Deployment + Service + Ingress (community.local)
```

Порядок у Application `backend`: кластер БД → міграції → backend. Якщо міграція падає, backend не оновлюється.

### Секрети (не в git)

| Secret | Звідки |
|--------|--------|
| `postgres-app` | генерує CloudNativePG (логін/пароль власника БД `community`) |
| `backend-secrets` | з `.env`: `AUTH_JWT_SECRET`, `AUTH_ADMIN_EMAILS`, `SUPABASE_*`, `SPRING_MAIL_*`, `MAIL_FROM` |
| `frontend-tls` | самопідписаний сертифікат для `community.local` |
| `ghcr-pull` | логін до приватних пакетів GHCR, із `GHCR_USERNAME` і `GHCR_TOKEN` у `.env` |

### Запуск кластера

```bash
cp .env.example .env   # заповнити обов'язкові змінні
infra/helpers/up.sh
```

Скрипт піднімає Minikube (профіль `community`), ArgoCD і секрети, застосовує root Application, чекає, поки backend і frontend стануть `Healthy`, запускає тунель ngrok у Docker і виводить його публічний URL. Потрібен запущений Docker, а гілка `deploy` має містити актуальні маніфести (workflow **Build and Deploy**). Запускати повторно безпечно.

- Зупинити тунель: `docker rm -f community-ngrok`
- Зупинити кластер: `minikube stop -p community`

### Ручний запуск у Minikube (без ArgoCD)

```bash
minikube start --cpus 4 --memory 6g
minikube addons enable ingress

# образи збираються прямо в Minikube з тегом :local, як у маніфестах
eval $(minikube docker-env)
R=ghcr.io/ukma-fin-ssdm-2026/b2b-saas-ai-sloppers
docker build -t $R/backend:local -f src/main/docker/Dockerfile .
docker build -t $R/frontend:local web-src
docker build -t $R/migrations:local infra/migrations

# оператор CloudNativePG
kubectl apply --server-side -f \
  https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/release-1.30/releases/cnpg-1.30.1.yaml

# секрети
kubectl create secret generic backend-secrets --from-literal=AUTH_JWT_SECRET="$(openssl rand -base64 48)"
openssl req -x509 -nodes -days 365 -newkey rsa:2048 -subj /CN=community.local \
  -addext subjectAltName=DNS:community.local -keyout tls.key -out tls.crt
kubectl create secret tls frontend-tls --cert tls.crt --key tls.key

# без ArgoCD hook-анотації ігноруються, тому порядок хвиль відтворюємо через wait
kubectl apply -f infra/kube/Services/Backend/database/cluster.yaml
kubectl wait cluster/postgres --for=condition=Ready --timeout=5m
kubectl apply -f infra/kube/Services/Backend/database/migrate-job.yaml
kubectl wait job/db-migrate --for=condition=complete --timeout=3m
kubectl apply -f infra/kube/Services/Backend/ -f infra/kube/Services/Frontend/
```

Доступ: додати `$(minikube ip) community.local` у `/etc/hosts` (на macOS з драйвером docker — `minikube tunnel` і `127.0.0.1 community.local`), відкрити https://community.local.

Повторний запуск міграцій вручну: `kubectl delete job db-migrate`, потім знову `kubectl apply` для Job.

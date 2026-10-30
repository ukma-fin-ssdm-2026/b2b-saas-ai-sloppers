#!/usr/bin/env bash
# Brings up the local cluster: Minikube, ArgoCD, secrets from .env, root Application.
# Safe to run again. ArgoCD takes the application itself from the deploy branch (ADR-004).
set -euo pipefail

PROFILE=community
KUBERNETES_VERSION=v1.36.4 # supported by CloudNativePG 1.30
ARGOCD_VERSION=v3.5.6
HOST=community.local
WAIT_TIMEOUT_SECONDS=900

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$REPO_ROOT/.env"

# always this cluster, whatever the current kubectl context is
k() { kubectl --context="$PROFILE" "$@"; }

log() { printf '\n==> %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

apply_stdin() { k apply -f - > /dev/null; }

check_prerequisites() {
  local tool name
  for tool in minikube kubectl openssl docker; do
    command -v "$tool" > /dev/null || fail "$tool is not installed"
  done
  docker info > /dev/null 2>&1 || fail "Docker is not running"

  [ -f "$ENV_FILE" ] || fail "$ENV_FILE not found: copy .env.example to .env and fill it in"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a

  for name in GHCR_USERNAME GHCR_TOKEN AUTH_JWT_SECRET; do
    [ -n "${!name:-}" ] || fail "$name is empty in .env"
  done
}

start_cluster() {
  log "Starting Minikube (profile $PROFILE)"
  minikube start -p "$PROFILE" --driver=docker \
    --kubernetes-version="$KUBERNETES_VERSION" --cpus=max --memory=6g
  minikube addons enable ingress -p "$PROFILE"
  k -n ingress-nginx rollout status deployment/ingress-nginx-controller --timeout=5m
}

install_argocd() {
  log "Installing ArgoCD $ARGOCD_VERSION"
  k create namespace argocd --dry-run=client -o yaml | apply_stdin
  k apply -n argocd --server-side --force-conflicts \
    -f "https://raw.githubusercontent.com/argoproj/argo-cd/$ARGOCD_VERSION/manifests/install.yaml" > /dev/null

  # without this health check the root Application does not wait between its sync waves
  k -n argocd patch configmap argocd-cm --type merge --patch '
data:
  resource.customizations.health.argoproj.io_Application: |
    hs = {}
    hs.status = "Progressing"
    hs.message = ""
    if obj.status ~= nil then
      if obj.status.health ~= nil then
        hs.status = obj.status.health.status
        if obj.status.health.message ~= nil then
          hs.message = obj.status.health.message
        end
      end
    end
    return hs
' > /dev/null

  k -n argocd wait --for=condition=available deployment --all --timeout=5m
  k -n argocd rollout status statefulset/argocd-application-controller --timeout=5m
}

create_secrets() {
  log "Creating secrets"
  k create secret docker-registry ghcr-pull \
    --docker-server=ghcr.io \
    --docker-username="$GHCR_USERNAME" \
    --docker-password="$GHCR_TOKEN" \
    --dry-run=client -o yaml | apply_stdin

  # only what the backend reads: the GHCR token stays out of its environment
  local name
  local args=()
  for name in AUTH_JWT_SECRET AUTH_ADMIN_EMAILS MAIL_FROM \
    SUPABASE_URL SUPABASE_SECRET_KEY SUPABASE_BUCKET \
    SPRING_MAIL_HOST SPRING_MAIL_PORT SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD; do
    if [ -n "${!name:-}" ]; then
      args+=("--from-literal=$name=${!name}")
    fi
  done
  k create secret generic backend-secrets "${args[@]}" --dry-run=client -o yaml | apply_stdin

  if ! k get secret frontend-tls > /dev/null 2>&1; then
    local tls_dir
    tls_dir="$(mktemp -d)"
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 -subj "/CN=$HOST" \
      -addext "subjectAltName=DNS:$HOST" \
      -keyout "$tls_dir/tls.key" -out "$tls_dir/tls.crt" 2> /dev/null
    k create secret tls frontend-tls --cert "$tls_dir/tls.crt" --key "$tls_dir/tls.key" > /dev/null
    rm -rf "$tls_dir"
  fi
}

app_health() {
  k -n argocd get application "$1" -o jsonpath='{.status.health.status}' 2> /dev/null || true
}

wait_for_applications() {
  log "Applying the root Application, waiting for backend and frontend"
  k apply -f "$REPO_ROOT/infra/kube/argocd/root.yaml" > /dev/null

  local deadline=$((SECONDS + WAIT_TIMEOUT_SECONDS))
  local backend frontend
  while [ "$SECONDS" -lt "$deadline" ]; do
    backend="$(app_health backend)"
    frontend="$(app_health frontend)"
    printf 'backend: %s, frontend: %s\n' "${backend:-pending}" "${frontend:-pending}"
    if [ "$backend" = Healthy ] && [ "$frontend" = Healthy ]; then
      return 0
    fi
    sleep 15
  done

  k -n argocd get applications || true
  k get pods || true
  fail "not healthy after $WAIT_TIMEOUT_SECONDS s. Look at: kubectl --context=$PROFILE describe pod <name>"
}

print_summary() {
  local password
  password="$(k -n argocd get secret argocd-initial-admin-secret \
    -o jsonpath='{.data.password}' | base64 --decode)"
  log "Done"
  printf 'ArgoCD UI:     kubectl --context=%s -n argocd port-forward svc/argocd-server 8081:443\n' "$PROFILE"
  printf '               https://localhost:8081, login admin, password %s\n' "$password"
  printf 'Stop cluster:  minikube stop -p %s\n' "$PROFILE"
}

check_prerequisites
start_cluster
install_argocd
create_secrets
wait_for_applications
print_summary

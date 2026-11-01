#!/usr/bin/env bash
# Brings up the local cluster: Minikube, ArgoCD, secrets from .env, root Application, ngrok tunnel.
# Safe to run again. ArgoCD takes the application itself from the deploy branch (ADR-004).
set -euo pipefail

PROFILE=community
KUBERNETES_VERSION=v1.36.4 # supported by CloudNativePG 1.30
ARGOCD_VERSION=v3.5.6
HOST=community.local
NGROK_IMAGE=ngrok/ngrok:3.39.13-alpine
NGROK_CONTAINER=community-ngrok
MIN_FREE_DISK_GB=3
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
  for tool in minikube kubectl openssl docker curl; do
    command -v "$tool" > /dev/null || fail "$tool is not installed"
  done
  docker info > /dev/null 2>&1 || fail "Docker is not running"

  [ -f "$ENV_FILE" ] || fail "$ENV_FILE not found: copy .env.example to .env and fill it in"
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a

  for name in GHCR_USERNAME GHCR_TOKEN NGROK_AUTHTOKEN AUTH_JWT_SECRET; do
    [ -n "${!name:-}" ] || fail "$name is empty in .env"
  done
}

check_node_disk() {
  # the node lives on the Docker Desktop disk; when it fills up, database pods fail with
  # "no space left on device"
  local free_gb
  free_gb="$(docker exec "$PROFILE" df -Pk / | awk 'NR == 2 { print int($4 / 1024 / 1024) }')"
  if [ "$free_gb" -lt "$MIN_FREE_DISK_GB" ]; then
    fail "only $free_gb GB free on the node, need $MIN_FREE_DISK_GB: raise the disk limit in Docker Desktop or free space (docker system df)"
  fi
}

start_cluster() {
  log "Starting Minikube (profile $PROFILE)"
  minikube start -p "$PROFILE" --driver=docker \
    --kubernetes-version="$KUBERNETES_VERSION" --cpus=max --memory=6g
  check_node_disk
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

  # only what the backend reads: the GHCR and ngrok tokens stay out of its environment
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

start_tunnel() {
  log "Starting the ngrok tunnel"
  docker rm -f "$NGROK_CONTAINER" > /dev/null 2>&1 || true
  # the docker driver names the network and the node after the profile;
  # the ingress controller listens on ports 80 and 443 of the node
  docker run -d --name "$NGROK_CONTAINER" --restart unless-stopped \
    --network "$PROFILE" -p 127.0.0.1:4040:4040 \
    -e NGROK_AUTHTOKEN "$NGROK_IMAGE" \
    http "https://$PROFILE:443" > /dev/null

  TUNNEL_URL=""
  for _ in $(seq 1 30); do
    TUNNEL_URL="$(curl -fsS http://localhost:4040/api/tunnels 2> /dev/null \
      | sed -nE 's/.*"public_url":"([^"]+)".*/\1/p')" || true
    [ -n "$TUNNEL_URL" ] && break
    sleep 2
  done
  if [ -z "$TUNNEL_URL" ]; then
    docker logs "$NGROK_CONTAINER" >&2 || true
    fail "no tunnel URL after 60 s: see the log above"
  fi
}

print_summary() {
  local password
  password="$(k -n argocd get secret argocd-initial-admin-secret \
    -o jsonpath='{.data.password}' | base64 --decode)"
  log "Done"
  printf 'Site:          %s\n' "$TUNNEL_URL"
  printf 'Stop tunnel:   docker rm -f %s\n' "$NGROK_CONTAINER"
  printf 'ArgoCD UI:     kubectl --context=%s -n argocd port-forward svc/argocd-server 8081:443\n' "$PROFILE"
  printf '               https://localhost:8081, login admin, password %s\n' "$password"
  printf 'Stop cluster:  minikube stop -p %s\n' "$PROFILE"
}

check_prerequisites
start_cluster
install_argocd
create_secrets
wait_for_applications
start_tunnel
print_summary

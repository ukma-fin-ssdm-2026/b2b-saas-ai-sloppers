#!/usr/bin/env bash
# NFR-004 / TC-041: kills the PostgreSQL primary and measures how fast the service recovers.
#
# Pass: the standby is promoted, the backend serves real API requests again within NFR_SECONDS
# of the kill, and a row written (and replicated) before the kill is present on the new primary.
#
# Usage:   infra/helpers/db-failover-test.sh [--keep-probe]
# Env:     NFR_SECONDS=30       recovery budget from NFR-004
#          MAX_WAIT_SECONDS=120 give up waiting after this long (to report the real time)
#          LOCAL_PORT=18080     local port for kubectl port-forward to the backend
#          FORCE=1              1: kill without graceful shutdown (crash); 0: normal pod delete
#
# Needs a running cluster from up.sh. Changes nothing in git: the operator (CNPG) recreates
# the killed pod as a standby.
set -euo pipefail

PROFILE=community
CLUSTER=postgres
DB=community
PROBE_TABLE=failover_probe
NFR_SECONDS="${NFR_SECONDS:-30}"
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-120}"
LOCAL_PORT="${LOCAL_PORT:-18080}"
FORCE="${FORCE:-1}"
KEEP_PROBE=0
[ "${1:-}" = "--keep-probe" ] && KEEP_PROBE=1

BASE_URL="http://127.0.0.1:$LOCAL_PORT"
READINESS_URL="$BASE_URL/actuator/health/readiness"
# public catalog: a real read from the database through postgres-rw
API_URL="$BASE_URL/api/v1/organizations"
# readiness and the API must both be 200 this many polls in a row to count as recovered
STABLE_POLLS=3

k() { kubectl --context="$PROFILE" "$@"; }
log() { printf '\n==> %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# wall clock in milliseconds: macOS date has no %N and its bash 3.2 has no EPOCHREALTIME
now_ms() { perl -MTime::HiRes=time -e 'printf "%d\n", time * 1000'; }
# "12345" ms since t0 -> "12.3"
secs_since_t0() { awk -v d="$(($1 - T0))" 'BEGIN { printf "%.1f", d / 1000 }'; }

http_code() { curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$1" || true; }

cluster_field() { k get cluster "$CLUSTER" -o jsonpath="{.status.$1}" 2> /dev/null || true; }

# psql inside a database pod as the local postgres superuser (peer auth over the socket)
psql_on() {
  local pod="$1"
  shift
  k exec "$pod" -c postgres -- psql -d "$DB" -v ON_ERROR_STOP=1 -qtAX "$@"
}

PF_PID=""
start_port_forward() {
  k port-forward svc/backend "$LOCAL_PORT:8080" > /dev/null 2>&1 &
  PF_PID=$!
  local i
  for i in $(seq 1 20); do
    [ "$(http_code "$READINESS_URL")" != 000 ] && return 0
    sleep 0.5
  done
  fail "port-forward to svc/backend on port $LOCAL_PORT did not come up (port busy?)"
}

# port-forward binds to one pod; if it drops (e.g. the backend restarted), open it again
ensure_port_forward() {
  if [ -n "$PF_PID" ] && ! kill -0 "$PF_PID" 2> /dev/null; then
    printf '  port-forward dropped, reopening\n'
    start_port_forward
  fi
}

cleanup() {
  local code=$?
  if [ "$KEEP_PROBE" = 0 ] && [ -n "${PROBE_CREATED:-}" ]; then
    local primary
    primary="$(cluster_field currentPrimary)"
    [ -n "$primary" ] && psql_on "$primary" -c "DROP TABLE IF EXISTS $PROBE_TABLE" > /dev/null 2>&1 || true
  fi
  [ -n "$PF_PID" ] && kill "$PF_PID" 2> /dev/null || true
  exit "$code"
}
trap cleanup EXIT

diagnostics() {
  printf '\n--- diagnostics ---\n' >&2
  k get cluster "$CLUSTER" >&2 || true
  k get pods -l "cnpg.io/cluster=$CLUSTER" -L cnpg.io/instanceRole >&2 || true
  k get pods -l app.kubernetes.io/name=backend >&2 || true
}

check_prerequisites() {
  log "Checking the cluster"
  local tool
  for tool in kubectl curl perl awk; do
    command -v "$tool" > /dev/null || fail "$tool is not installed"
  done
  k get cluster "$CLUSTER" > /dev/null 2>&1 \
    || fail "no CNPG cluster '$CLUSTER' in context '$PROFILE': run infra/helpers/up.sh first"

  local instances ready
  instances="$(k get cluster "$CLUSTER" -o jsonpath='{.spec.instances}')"
  ready="$(cluster_field readyInstances)"
  [ "${ready:-0}" -ge 2 ] \
    || fail "only ${ready:-0}/$instances database instances ready: no standby to fail over to, try again later"

  k wait --for=condition=Ready pod -l app.kubernetes.io/name=backend --timeout=10s > /dev/null \
    || fail "backend pod is not Ready"
  printf 'database instances ready: %s/%s, backend Ready\n' "$ready" "$instances"
}

write_probe() {
  log "Baseline"
  start_port_forward

  local readiness api
  readiness="$(http_code "$READINESS_URL")"
  api="$(http_code "$API_URL")"
  printf 'readiness: %s, GET /api/v1/organizations: %s\n' "$readiness" "$api"
  [ "$readiness" = 200 ] && [ "$api" = 200 ] || fail "service is not healthy before the test"

  OLD_PRIMARY="$(cluster_field currentPrimary)"
  [ -n "$OLD_PRIMARY" ] || fail "cannot read .status.currentPrimary"
  printf 'primary: %s\n' "$OLD_PRIMARY"

  TOKEN="probe-$(date +%s)-$RANDOM"
  psql_on "$OLD_PRIMARY" -c "CREATE TABLE IF NOT EXISTS $PROBE_TABLE (token text PRIMARY KEY, written_at timestamptz DEFAULT now())" > /dev/null
  PROBE_CREATED=1
  psql_on "$OLD_PRIMARY" -c "INSERT INTO $PROBE_TABLE (token) VALUES ('$TOKEN')" > /dev/null
  printf 'marker row written: %s\n' "$TOKEN"

  # replication is asynchronous: kill only after the standby has the row, otherwise a lost
  # row would be the expected cost of async replication, not a failover bug
  local replica i
  replica="$(k get pods -l "cnpg.io/cluster=$CLUSTER,cnpg.io/instanceRole=replica" \
    -o jsonpath='{.items[0].metadata.name}')"
  for i in $(seq 1 20); do
    if [ "$(psql_on "$replica" -c "SELECT count(*) FROM $PROBE_TABLE WHERE token = '$TOKEN'" 2> /dev/null)" = 1 ]; then
      printf 'marker replicated to standby %s\n' "$replica"
      return 0
    fi
    sleep 0.5
  done
  fail "marker did not reach standby $replica in 10 s: replication is broken, failover would lose data"
}

kill_primary() {
  log "Killing primary $OLD_PRIMARY (FORCE=$FORCE)"
  local args=(delete pod "$OLD_PRIMARY" --wait=false)
  [ "$FORCE" = 1 ] && args+=(--grace-period=0 --force)
  T0="$(now_ms)"
  k "${args[@]}" > /dev/null 2>&1
}

wait_for_recovery() {
  log "Waiting for recovery (budget ${NFR_SECONDS} s, polling every 1 s)"
  T_FAIL=""
  T_PROMOTED=""
  T_RECOVERED=""
  NEW_PRIMARY=""

  local deadline=$((T0 + MAX_WAIT_SECONDS * 1000))
  local streak=0 first_ok="" now readiness api primary
  while :; do
    now="$(now_ms)"
    [ "$now" -lt "$deadline" ] || break
    ensure_port_forward

    readiness="$(http_code "$READINESS_URL")"
    api="$(http_code "$API_URL")"
    primary="$(cluster_field currentPrimary)"
    printf '  t+%5ss  readiness=%s  api=%s  primary=%s\n' \
      "$(secs_since_t0 "$now")" "$readiness" "$api" "${primary:-?}"

    if [ -z "$T_FAIL" ] && { [ "$readiness" != 200 ] || [ "$api" != 200 ]; }; then
      T_FAIL="$now"
    fi
    if [ -z "$T_PROMOTED" ] && [ -n "$primary" ] && [ "$primary" != "$OLD_PRIMARY" ]; then
      T_PROMOTED="$now"
      NEW_PRIMARY="$primary"
    fi

    # count only answers after the promotion: before it, a 200 may be a cached health result
    if [ -n "$T_PROMOTED" ] && [ "$readiness" = 200 ] && [ "$api" = 200 ]; then
      [ "$streak" = 0 ] && first_ok="$now"
      streak=$((streak + 1))
      if [ "$streak" -ge "$STABLE_POLLS" ]; then
        T_RECOVERED="$first_ok"
        return 0
      fi
    else
      streak=0
    fi
    sleep 1
  done
}

check_data() {
  [ -n "$NEW_PRIMARY" ] || { DATA_OK=0; return; }
  local found
  found="$(psql_on "$NEW_PRIMARY" -c "SELECT count(*) FROM $PROBE_TABLE WHERE token = '$TOKEN'" 2> /dev/null || true)"
  if [ "$found" = 1 ]; then DATA_OK=1; else DATA_OK=0; fi
}

# not part of the NFR budget: how long until CNPG has a standby again
wait_for_second_replica() {
  log "Waiting for the killed pod to come back as a standby"
  T_REPLICA=""
  local deadline=$(($(now_ms) + 300 * 1000))
  while [ "$(now_ms)" -lt "$deadline" ]; do
    if [ "$(cluster_field readyInstances)" = 2 ]; then
      T_REPLICA="$(now_ms)"
      return 0
    fi
    sleep 2
  done
}

fmt() { if [ -n "$1" ]; then printf '%s s' "$(secs_since_t0 "$1")"; else printf '—'; fi; }

report() {
  log "Result (times from the kill)"
  printf 'old primary:            %s\n' "$OLD_PRIMARY"
  printf 'new primary:            %s\n' "${NEW_PRIMARY:-none}"
  printf 'first failed request:   %s\n' "$(fmt "$T_FAIL")"
  printf 'standby promoted:       %s\n' "$(fmt "$T_PROMOTED")"
  printf 'service recovered:      %s   (budget %s s)\n' "$(fmt "$T_RECOVERED")" "$NFR_SECONDS"
  printf 'marker row survived:    %s\n' "$([ "$DATA_OK" = 1 ] && echo yes || echo NO)"
  printf 'second replica back:    %s   (not part of NFR-004)\n' "$(fmt "${T_REPLICA:-}")"

  if [ -z "$T_FAIL" ]; then
    printf '\nWARN: no failed request was observed: the outage was shorter than the poll interval or the 5 s health cache\n'
  fi

  local ok=1
  if [ -z "$T_RECOVERED" ]; then
    printf '\nFAIL: service did not recover within %s s\n' "$MAX_WAIT_SECONDS"
    ok=0
  elif [ $((T_RECOVERED - T0)) -gt $((NFR_SECONDS * 1000)) ]; then
    printf '\nFAIL: recovery took %s s, NFR-004 allows %s s\n' "$(secs_since_t0 "$T_RECOVERED")" "$NFR_SECONDS"
    ok=0
  fi
  if [ "$DATA_OK" != 1 ]; then
    printf '\nFAIL: marker row %s is missing on the new primary\n' "$TOKEN"
    ok=0
  fi

  if [ "$ok" = 1 ]; then
    printf '\nPASS: NFR-004 met\n'
  else
    diagnostics
    exit 1
  fi
}

check_prerequisites
write_probe
kill_primary
wait_for_recovery
check_data
wait_for_second_replica
report

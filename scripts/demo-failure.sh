#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="${NAMESPACE:-bestsellers}"

echo "Turn on FAIL_HEALTH. Startup still uses /ready, then liveness fails on /health."
helm upgrade bestsellers "${ROOT}/helm/bestsellers" \
  --namespace "${NAMESPACE}" \
  --reuse-values \
  --set chaos.failHealth=true

echo "Watch restart counts. Stop with Ctrl-C when a pod shows restarts, then this script recovers."
kubectl -n "${NAMESPACE}" get pods -w &
WATCH=$!
sleep 45
kill "${WATCH}" >/dev/null 2>&1 || true

echo "Turn the flag off and wait for a healthy rollout."
helm upgrade bestsellers "${ROOT}/helm/bestsellers" \
  --namespace "${NAMESPACE}" \
  --reuse-values \
  --set chaos.failHealth=false \
  --wait \
  --timeout 180s

kubectl -n "${NAMESPACE}" rollout status deploy/bestsellers --timeout=180s
"${ROOT}/scripts/smoke.sh"

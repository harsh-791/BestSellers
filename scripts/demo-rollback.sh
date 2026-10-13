#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NAMESPACE="${NAMESPACE:-bestsellers}"

echo "Point the release at an image tag that was never built."
helm upgrade bestsellers "${ROOT}/helm/bestsellers" \
  --namespace "${NAMESPACE}" \
  --reuse-values \
  --set image.tag=missing-release \
  --set image.pullPolicy=IfNotPresent

echo "The rolling update keeps the previous pods because maxUnavailable is 0."
sleep 8
kubectl -n "${NAMESPACE}" get pods -o wide || true

echo "Roll the release back to the last good revision."
helm rollback bestsellers --namespace "${NAMESPACE}"
kubectl -n "${NAMESPACE}" rollout status deploy/bestsellers --timeout=180s
"${ROOT}/scripts/smoke.sh"

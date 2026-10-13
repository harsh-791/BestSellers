#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-bestsellers}"
PORT="${PORT:-18080}"

kubectl -n "${NAMESPACE}" port-forward svc/bestsellers "${PORT}:8080" >/tmp/bestsellers-port-forward.log 2>&1 &
PF=$!
cleanup() {
  kill "${PF}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

ready=0
for _ in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:${PORT}/health" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done

if [[ "${ready}" -ne 1 ]]; then
  echo "API did not answer on port ${PORT}. Port-forward log:" >&2
  cat /tmp/bestsellers-port-forward.log >&2 || true
  exit 1
fi

echo "health: $(curl -fsS "http://127.0.0.1:${PORT}/health")"
echo "ready: $(curl -fsS "http://127.0.0.1:${PORT}/ready")"
echo "count: $(curl -fsS --get "http://127.0.0.1:${PORT}/books/count" --data-urlencode "author=George Orwell")"
echo "metrics:"
curl -fsS "http://127.0.0.1:${PORT}/metrics" | head -n 12
echo

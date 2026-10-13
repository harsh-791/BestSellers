#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-bestsellers}"

echo "API        http://127.0.0.1:8080"
echo "Grafana    http://127.0.0.1:3000"
echo "Prometheus http://127.0.0.1:9090"
echo "Jaeger     http://127.0.0.1:16686"
echo "Press Ctrl-C to stop."

kubectl -n "${NAMESPACE}" port-forward svc/bestsellers 8080:8080 &
kubectl -n "${NAMESPACE}" port-forward svc/grafana 3000:3000 &
kubectl -n "${NAMESPACE}" port-forward svc/prometheus 9090:9090 &
kubectl -n "${NAMESPACE}" port-forward svc/jaeger 16686:16686 &

trap 'kill $(jobs -p) >/dev/null 2>&1 || true' EXIT INT TERM
wait

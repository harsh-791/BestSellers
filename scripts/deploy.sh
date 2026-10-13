#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

need docker
need kind
need helm
need kubectl
need git

CLUSTER="${CLUSTER:-bestsellers}"
NAMESPACE="${NAMESPACE:-bestsellers}"
GIT_SHA="$(git rev-parse HEAD)"
if [[ -n "$(git status --porcelain)" ]]; then
  GIT_SHA="${GIT_SHA}-dirty"
fi
IMAGE="${IMAGE:-bestsellers:${GIT_SHA}}"

if command -v mvn >/dev/null 2>&1; then
  mvn -B verify
else
  echo "mvn not found; host tests were skipped. The image build still compiles the application."
fi

docker build -t "${IMAGE}" .

if ! kind get clusters | grep -qx "${CLUSTER}"; then
  kind create cluster --name "${CLUSTER}" --config kind/cluster.yaml
fi

kind load docker-image "${IMAGE}" --name "${CLUSTER}"

kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.7.2/components.yaml
if ! kubectl -n kube-system get deploy metrics-server -o jsonpath='{.spec.template.spec.containers[0].args}' | grep -q kubelet-insecure-tls; then
  kubectl -n kube-system patch deployment metrics-server --type=json \
    -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
fi

helm upgrade --install observability "${ROOT}/helm/observability" \
  --namespace "${NAMESPACE}" \
  --create-namespace \
  --wait \
  --timeout 180s

helm upgrade --install bestsellers "${ROOT}/helm/bestsellers" \
  --namespace "${NAMESPACE}" \
  --set "image.repository=bestsellers" \
  --set "image.tag=${GIT_SHA}" \
  --set image.pullPolicy=IfNotPresent \
  --wait \
  --timeout 180s

kubectl -n "${NAMESPACE}" get pods -o wide
"${ROOT}/scripts/smoke.sh"

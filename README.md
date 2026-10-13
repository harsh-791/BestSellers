# BestSellers

BestSellers answers catalog questions about a CSV of bestselling books: how many titles an author has, which titles they wrote, which books share a user rating, and what those titles cost.

The original program is still the command-line class `bestsellers.Driver`. The same query code now also runs as a small HTTP API so it can stay up on Kubernetes, expose health and metrics, and emit a trace for each request.

## Identity

- Name: Harsh Verma
- Student: 23BCS10183
- Project: BestSellers
- Repository: https://github.com/harsh-791/BestSellers
- Baseline commit: `4629097dd21fbe5d0425d86019199023b2c8f196`

That commit is the unchanged starting point: four Java classes and `data.csv` at the repository root, run on a local machine. It had no container, Kubernetes resources, pipeline, observability, or security controls.

## What this adds

| Area | Choice |
| --- | --- |
| Runtime | HTTP API in a container, Kubernetes Deployment |
| Configuration | Helm values for image tag, feature flag, probes, resources |
| Delivery | `.github/workflows/ci.yml` tests and builds an image. `.github/workflows/cd.yml` deploys that image when you start it |
| Release | Image tag is the git commit. Rolling update with `maxUnavailable: 0`. `helm rollback` restores the previous revision |
| Signals | JSON logs, Prometheus metrics, OpenTelemetry traces in Jaeger |
| Security | Non-root read-only container, no Kubernetes API token, ClusterIP only, secret scan in CI |

Supporting material:

- [docs/DECISIONS.md](docs/DECISIONS.md) explains the choices.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) shows the request path and the delivery path.
- [docs/RECOVERY.md](docs/RECOVERY.md) records a bad release, the rollback, and the CI runs.

## Prerequisites

- Docker
- [kind](https://kind.sigs.k8s.io/)
- Helm 3
- kubectl
- About 4 GB of memory for Docker, because the cluster has two worker nodes

Java 21 and Maven 3.9 are only needed if you want to run tests or the CLI outside Docker. This machine can build the image without them. The image build compiles the code inside Docker.

## Run on local Kubernetes

```bash
./scripts/deploy.sh
./scripts/forward.sh
```

`deploy.sh` runs tests when Maven is installed, builds `bestsellers:<git sha>`, creates the kind cluster when it does not exist, loads the image, installs metrics-server, then installs the observability chart and the application chart.

Open:

- API: http://127.0.0.1:8080
- Grafana: http://127.0.0.1:3000 (anonymous view, local only)
- Prometheus: http://127.0.0.1:9090
- Jaeger: http://127.0.0.1:16686

```bash
curl -s http://127.0.0.1:8080/health
curl -s http://127.0.0.1:8080/ready
curl -s --get http://127.0.0.1:8080/books/count --data-urlencode "author=George Orwell"
curl -s --get http://127.0.0.1:8080/books/titles --data-urlencode "author=George Orwell"
curl -s --get http://127.0.0.1:8080/books/by-rating --data-urlencode "rating=4.7"
curl -s --get http://127.0.0.1:8080/books/prices --data-urlencode "author=George Orwell"
curl -s http://127.0.0.1:8080/authors
curl -s http://127.0.0.1:8080/metrics
```

Rating matches are exact, the same rule as the original program. `4.7` does not mean "at least 4.7".

## Run without Kubernetes

```bash
mvn -B verify
mvn -B package
java -cp target/bestsellers.jar bestsellers.Driver src/main/resources/data.csv
java -jar target/bestsellers.jar
```

The server listens on port 8080 and, when `CSV_PATH` is unset, uses the CSV packaged in the jar. Traces are printed to the console unless `OTEL_TRACES_EXPORTER=otlp` and `OTEL_EXPORTER_OTLP_ENDPOINT` are set.

```bash
docker build -t bestsellers:local .
docker run --rm -p 8080:8080 bestsellers:local
```

## Request path

1. A client calls the ClusterIP service, or a port-forward to that service.
2. kube-proxy sends the request to one of the two pods.
3. `ApiServer` checks the route, writes a JSON log line with a trace id, and records a Prometheus histogram.
4. The matching method in `BookQueries` reads the in-memory catalog that was loaded from the CSV at startup.
5. The process exports the span to Jaeger. If Jaeger is down, the HTTP response still succeeds.

`/health` means the process can answer. `/ready` means the catalog loaded at least one book. Kubernetes uses `/ready` for startup and readiness, and `/health` for liveness.

## Failure demos

Pod replacement:

```bash
kubectl -n bestsellers delete pod -l app.kubernetes.io/name=bestsellers
kubectl -n bestsellers get pods -o wide
./scripts/smoke.sh
```

Bad release, then rollback. The bad tag does not remove the currently serving pods, because the rolling update refuses to take the last ready pod down:

```bash
./scripts/demo-rollback.sh
```

Liveness failure, then recovery:

```bash
./scripts/demo-failure.sh
```

`FAIL_HEALTH=true` makes `/health` return 500. The pod can still pass `/ready`, then Kubernetes restarts it. Setting the flag back to false is the fix.

Price lookup can be turned off without a code change:

```bash
helm upgrade bestsellers helm/bestsellers -n bestsellers --reuse-values --set featureFlags.priceLookup=false
```

`/books/prices` then returns 403. Set the value back to `true` to restore it.

## Pipeline

On every push and pull request, `.github/workflows/ci.yml` runs `scripts/check-secrets.sh`, `mvn verify`, and `docker build`. The image is uploaded as a workflow artifact. CI does not deploy.

`.github/workflows/cd.yml` is started by hand from the Actions tab after that CI run is green. It downloads the image artifact for the same commit, loads it into a kind cluster, installs both Helm charts, and runs `scripts/smoke.sh`. Locally, `./scripts/deploy.sh` does the same deploy on your machine.

## Limitations

- The cluster is local kind. There is no container registry and no cloud account. Terraform is intentionally not used.
- Grafana anonymous access is only for this local demo. Do not reuse that setting on a shared network.
- The HorizontalPodAutoscaler needs metrics-server. A later `helm upgrade` sets the Deployment back to `replicaCount` (2). That interaction is a reason teams often let the autoscaler own the replica field.
- A dirty working tree is tagged `<commit>-dirty`, so the running image is not described as a clean commit.
- The catalog is loaded once at startup. Changing the CSV requires a new pod.
- Jaeger in this chart is a single in-memory collector for the demo, not a durable trace store.

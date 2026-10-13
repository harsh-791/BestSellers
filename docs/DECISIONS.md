# Decisions

BestSellers stays one process. The course outcomes are covered by how that process is built, deployed, and watched, not by splitting it into services.

## Architecture

`DatasetReader` loads the CSV. `BookQueries` answers the original questions. `Driver` is the old command-line entry and still calls those classes directly. `ApiServer` is the new entry used in the container. It does not reimplement the queries.

The catalog is an immutable input, not user data that changes after startup. A failed CSV is a startup failure: the process exits, and Kubernetes restarts the container. A few malformed rows are counted in `books_rows_skipped` and the valid rows are still served. An empty catalog stays unready so traffic is not sent to a process that has nothing to answer.

The dependency boundary is the file at startup and Jaeger after startup. A Jaeger outage cannot change an HTTP status code. A missing file can.

## Kubernetes

A Deployment fits this workload. The pods are interchangeable, and the CSV is inside the image rather than a disk that belongs to one pod. A StatefulSet would imply identity and stable storage that this catalog does not need.

Two replicas, a PodDisruptionBudget of one available pod, and topology spread across hostnames are there so a node or a single pod can disappear without emptying the service. Spread is `ScheduleAnyway`, so a one-node cluster still runs. The kind config uses two workers so the spread has somewhere to go.

Probes are split on purpose. Startup and readiness use `/ready`. Liveness uses `/health`. A forced health failure can restart a container that was able to load the catalog. Resource requests and limits are set so the scheduler and the autoscaler have numbers to use.

The Service is ClusterIP. Access from the laptop is an explicit port-forward, not a host port.

## Deployment configuration

Helm is the only copy of the runtime configuration. Image tag, the price-lookup flag, the health-failure flag, probes, resources, and the trace endpoint live in `helm/bestsellers/values.yaml`. A checksum annotation rolls pods when those values change.

The CSV is not copied into the chart. It ships once, inside the jar. `CSV_PATH` exists for a mounted replacement, but the default chart does not create a second catalog. Observability is a separate chart so Prometheus and Jaeger can be upgraded without pretending they are part of the application.

## CI and CD

Automatic work ends at a tested image artifact. Tests, a secret scan, and `docker build` run on every push and pull request. Deploying that artifact is manual: `workflow_dispatch` in GitHub Actions, or `./scripts/deploy.sh` on a laptop. The manual boundary is there because this environment has no shared cluster and a bad deploy should be a deliberate action.

The pipeline does not push to a registry. kind loads the image that Docker just built. That is a local constraint, not a production promotion flow. The same image tag is the Helm value, so the pod spec and the build refer to one identifier.

## Release control

The image tag is the full git commit. A dirty tree gets a `-dirty` suffix so it cannot be confused with that commit.

The Deployment strategy is a rolling update with `maxUnavailable: 0` and `maxSurge: 1`. A new pod must become ready before an old one is removed. `scripts/demo-rollback.sh` points the release at a tag that does not exist, shows the previous pods still present, then runs `helm rollback`.

`FEATURE_PRICE_LOOKUP` is the feature flag. It is a Helm value, rendered into an environment variable. Turning it off returns 403 from `/books/prices` and does not require a query-code change. It still rolls pods, because the process reads the flag at startup.

There is no blue-green or canary environment. With one local cluster and no user traffic to split, a second stack would be ceremony. Rolling update plus rollback is the strategy I can actually show.

## Observability

Logs are one JSON object per request on stdout: time, route, status, latency, and trace id. Kubernetes and `kubectl logs` are the log store. Query strings are not copied in whole; the author is a separate escaped field when the route uses it.

Metrics are served at `/metrics` in Prometheus text format. The useful ones are `http_requests_total`, `http_request_duration_seconds`, `books_loaded`, `books_rows_skipped`, and `feature_price_lookup_enabled`. Prometheus scrapes the Service every five seconds. Grafana has one dashboard for rate, p95 latency, errors, and catalog size.

Traces use the OpenTelemetry SDK. Each request is a span, and the catalog call is a child span (`book.count`, `book.titles`, and so on). The cluster sets `OTEL_TRACES_EXPORTER=otlp` and sends spans to Jaeger on port 4318. A local `java -jar` run logs spans instead, because Jaeger is not assumed to be there.

Healthy means: deployment available, `/ready` returns 200, `books_loaded` is greater than zero, and a known author query returns a count. Recovery is verified by `scripts/smoke.sh` after a rollback, a pod delete, or clearing `FAIL_HEALTH`.

## Security

The application container runs as uid 1000, with a read-only root filesystem, a writable emptyDir only at `/tmp`, all capabilities dropped, and no privilege escalation. Its ServiceAccount does not mount a Kubernetes API token, because the process never calls the API.

Nothing in this repository is a credential. Grafana's local anonymous viewer is a demo exception and is called out in the README. CI runs `scripts/check-secrets.sh` to reject private keys and AWS-style access key ids.

NetworkPolicy allows ingress to the API port only from pods in the same namespace, and egress only to DNS and Jaeger's OTLP port. kubectl port-forward is not blocked by that policy, which is how the demo reaches the Service.

## What I would change

- A real registry and a pinned digest once more than one cluster needed the image.
- Let the HorizontalPodAutoscaler own `replicas`, instead of Helm setting them on every upgrade.
- Replace the in-memory Jaeger with a collector that keeps traces.
- Put Grafana behind a login, with the admin password in a Kubernetes Secret created outside git.
- Add a second environment only if a human had to approve production separately from a laptop demo.

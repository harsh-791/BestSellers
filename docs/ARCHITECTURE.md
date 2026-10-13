# Architecture

BestSellers is one process. Kubernetes keeps two copies of that process running. Helm is the source of the pod spec. CI builds the image, and CD is a separate manual workflow.

## Runtime

```mermaid
flowchart LR
  client[Client or port-forward]
  svc[ClusterIP service]
  podA[API pod]
  podB[API pod]
  queries[BookQueries]
  csv[CSV inside the image]
  logs[JSON logs on stdout]
  metrics["/metrics"]
  jaeger[Jaeger]
  prom[Prometheus]
  grafana[Grafana]

  client --> svc
  svc --> podA
  svc --> podB
  podA --> queries
  podB --> queries
  queries --> csv
  podA --> logs
  podA --> metrics
  podA --> jaeger
  prom --> metrics
  grafana --> prom
```

A request hits the Service, then one of the two pods. `ApiServer` logs the request, records latency, and starts a trace. `BookQueries` reads the catalog that was loaded at startup. The response does not wait on Jaeger. Prometheus scrapes `/metrics` every five seconds, and Grafana reads Prometheus.

## Delivery

```mermaid
flowchart LR
  push[git push]
  ci["ci.yml"]
  checks[secret scan and tests]
  artifact[image artifact]
  cd["cd.yml, started by hand"]
  local["scripts/deploy.sh"]
  kind[kind cluster]
  helm[Helm release]

  push --> ci
  ci --> checks
  checks --> artifact
  artifact --> cd
  cd --> kind
  local --> kind
  kind --> helm
```

The image tag is the git commit. kind loads that image. There is no registry push.

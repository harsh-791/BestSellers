# Fault and recovery

This was run on the local kind cluster on 13 October 2026, using `scripts/demo-rollback.sh`.

## Symptom

After a Helm upgrade, a new API pod stayed in `ContainerCreating`. The service still answered.

## Hypothesis

The upgrade set the image tag to `missing-release`, a tag that was never built. The Deployment uses `maxUnavailable: 0`, so Kubernetes will not remove a ready pod until a replacement is ready. The bad pod cannot become ready, and the previous pods should keep serving.

## Evidence

Helm revision 2 reported the image tag `missing-release`. The pods were:

| Pod | Ready | Status | Node |
| --- | --- | --- | --- |
| bestsellers-5df7c4d998-tv8cs | 0/1 | ContainerCreating | bestsellers-worker2 |
| bestsellers-945b9c95f-74s27 | 1/1 | Running | bestsellers-worker2 |
| bestsellers-945b9c95f-sns28 | 1/1 | Running | bestsellers-worker |

The two ready pods were the previous revision, on two different workers.

## Change

```bash
helm rollback bestsellers --namespace bestsellers
```

That is revision 1, whose image tag is the commit that was loaded into kind.

## Verification

`kubectl rollout status deploy/bestsellers` completed. `scripts/smoke.sh` then returned:

- `/health`: `{"status":"up"}`
- `/ready`: `{"status":"ready","books":550}`
- `/books/count?author=George Orwell`: `{"count":1}`

Before the failure demo, the same cluster had already shown the other signals: Prometheus target `bestsellers` was up with `books_loaded` equal to 550, and Jaeger listed the service `bestsellers`.

## Pipeline runs

CI on `devops` completed successfully for each pushed commit:

- [ci for ad3c49f](https://github.com/harsh-791/BestSellers/actions/runs/38460451615)
- [ci for 0f6c0b8](https://github.com/harsh-791/BestSellers/actions/runs/38449384711)
- [ci for 92a31a2](https://github.com/harsh-791/BestSellers/actions/runs/38439614426)

`cd.yml` is manual and has not been started on GitHub. The deploy shown above was `scripts/deploy.sh` on the laptop, which installs the same charts into kind.

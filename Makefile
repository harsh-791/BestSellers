.PHONY: verify image deploy smoke secrets

verify:
	mvn -B verify

image:
	docker build -t bestsellers:local .

deploy:
	./scripts/deploy.sh

smoke:
	./scripts/smoke.sh

secrets:
	./scripts/check-secrets.sh

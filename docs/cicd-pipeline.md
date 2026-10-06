# CI/CD Pipeline

## 1. Overview

This project uses GitHub Actions to implement a CI/CD pipeline.

The pipeline performs:

```text
Git Push
   |
   v
Read Version
   |
   v
Run Tests
   |
   v
Build Docker Images
   |
   v
Push Images to GHCR
   |
   v
Deploy to Kubernetes
```

The deployment job runs on a self-hosted GitHub Actions runner located inside the Killercoda Kubernetes environment.

---

# 2. Pipeline Architecture

```text
Developer
    |
    | git push main
    v
GitHub
    |
    v
GitHub Actions
    |
    +-----------------------+
    |                       |
    v                       v
Version                Tests
                         |
               +---------+---------+
               |                   |
               v                   v
        Order Service      Notification Service
               |                   |
               +---------+---------+
                         |
                         v
                    Publish Images
                         |
                         v
                        GHCR
                         |
                         v
                  Self-Hosted Runner
                         |
                         v
                       kubectl
                         |
                         v
                    Kubernetes
```

---

# 3. Workflow File

The workflow is:

```text
.github/workflows/ci-cd.yml
```

The workflow is triggered by a push to `main`:

```yaml
on:
  push:
    branches:
      - main
```

---

# 4. Permissions

The workflow contains:

```yaml
permissions:
  contents: read
  packages: write
```

### `contents: read`

Allows the workflow to read repository contents.

This is needed for operations such as checking out the source code.

### `packages: write`

Allows the workflow to publish Docker images to GitHub Container Registry.

---

# 5. Job 1 - Version

The first job is:

```yaml
version:
  name: Read Version
```

It runs on:

```yaml
runs-on: ubuntu-latest
```

The job reads:

```text
VERSION
```

Example:

```text
v1.0.0
```

The version is validated using:

```text
vMAJOR.MINOR.PATCH
```

Example:

```text
v1.0.0
v1.2.3
v2.10.5
```

Invalid examples:

```text
1.0.0
version-1.0.0
v1
```

The version is exported as a job output:

```yaml
outputs:
  image_version: ${{ steps.set_version.outputs.image_version }}
```

This allows later jobs to access it.

---

# 6. Why Use a Version File?

The project has:

```text
VERSION
```

containing:

```text
v1.0.0
```

This version is used for Docker image tags.

Example:

```text
ghcr.io/precise-charmerr/order-service:v1.0.0
```

and:

```text
ghcr.io/precise-charmerr/notification-service:v1.0.0
```

The same version is then used by Kubernetes during deployment.

Therefore:

```text
VERSION
   |
   v
Docker Image Tag
   |
   v
Kubernetes Deployment
```

---

# 7. Job 2 - Order Service Tests

Job:

```yaml
order-service:
  name: Test Order Service
```

It runs on:

```yaml
runs-on: ubuntu-latest
```

It uses Java 21:

```yaml
uses: actions/setup-java@v4
```

with:

```yaml
distribution: temurin
java-version: '21'
```

Tests are executed using Maven:

```bash
./mvnw -B verify
```

Working directory:

```text
order-service
```

---

# 8. Job 3 - Notification Service Tests

The Notification Service depends on Kafka.

Therefore the GitHub Actions job starts Kafka as a service container.

Kafka image:

```text
apache/kafka:4.0.1
```

Kafka is exposed on:

```text
9092
```

The test environment uses:

```text
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

The workflow waits for Kafka to become available before running tests.

Then:

```bash
./mvnw -B verify
```

is executed.

---

# 9. Job Dependencies

The order-service and notification-service tests depend on the version job:

```yaml
needs: version
```

The publish job depends on:

```yaml
needs:
  - version
  - order-service
  - notification-service
```

The deploy job depends on:

```yaml
needs:
  - version
  - publish
```

Therefore the pipeline is:

```text
version
   |
   +--------------------+
   |                    |
   v                    v
order-service     notification-service
   |                    |
   +---------+----------+
             |
             v
          publish
             |
             v
           deploy
```

GitHub Actions jobs run independently by default, while `needs` creates explicit job dependencies. If a required job fails, dependent jobs are skipped unless explicitly configured otherwise.

---

# 10. Job 4 - Build and Publish

The publish job uses a matrix:

```yaml
strategy:
  matrix:
    service:
      - order-service
      - notification-service
```

This allows the same job definition to build both services.

Conceptually:

```text
publish
   |
   +-- order-service
   |
   +-- notification-service
```

---

# 11. Maven Package

Each service is packaged using:

```bash
./mvnw -B -DskipTests package
```

Tests were already executed in the previous jobs.

The JAR is then available for Docker image creation.

---

# 12. Docker Buildx

The workflow configures:

```yaml
docker/setup-qemu-action@v3
```

and:

```yaml
docker/setup-buildx-action@v3
```

This allows multi-platform image builds.

Platforms:

```text
linux/amd64
linux/arm64
```

---

# 13. GHCR Authentication

The workflow logs into GitHub Container Registry:

```yaml
- name: Log in to GHCR
  uses: docker/login-action@v3
```

Registry:

```text
ghcr.io
```

Authentication uses:

```yaml
username: ${{ github.actor }}
password: ${{ secrets.GITHUB_TOKEN }}
```

Important distinction:

```text
GITHUB_TOKEN
```

is used by the GitHub Actions workflow to authenticate with GitHub resources such as the package registry.

It is different from the temporary registration token used when registering a self-hosted runner.

---

# 14. Docker Image Publishing

The images are pushed using:

```yaml
docker/build-push-action@v6
```

Example:

```text
ghcr.io/precise-charmerr/order-service:v1.0.0
```

and:

```text
ghcr.io/precise-charmerr/notification-service:v1.0.0
```

---

# 15. Job 5 - Deploy

The deployment job is:

```yaml
deploy:
  name: Deploy to Kubernetes
  runs-on: self-hosted
```

Unlike the CI jobs, this job does not run on GitHub's hosted Ubuntu machine.

It runs on:

```text
Killercoda controlplane
```

through the registered GitHub Actions self-hosted runner.

---

# 16. Self-Hosted Runner Flow

```text
GitHub Actions
      |
      | deploy job
      v
Self-Hosted Runner
      |
      | executes shell commands
      v
kubectl
      |
      v
Killercoda Kubernetes API
```

GitHub routes a self-hosted job to an online, idle runner matching the requested labels/groups. The default `self-hosted` label is automatically applied to a self-hosted runner.

---

# 17. Verify Kubernetes Access

The deployment job first runs:

```bash
kubectl version --client
kubectl get nodes
```

This verifies:

1. `kubectl` is installed.
2. The runner can communicate with Kubernetes.

---

# 18. Version Setup

The deploy job reads:

```text
VERSION
```

and exports:

```text
IMAGE_VERSION
```

Example:

```text
IMAGE_VERSION=v1.0.0
```

---

# 19. Deploy Namespace

The workflow runs:

```bash
kubectl apply -f k8s/infra/namespace.yaml
```

This creates or updates:

```text
kafka-demo
```

---

# 20. Deploy Kafka

The workflow runs:

```bash
kubectl apply -f k8s/infra/kafka.yaml
```

This creates:

```text
Kafka Deployment
Kafka Service
```

The Kafka service is:

```text
kafka:9092
```

inside the namespace.

---

# 21. Deploy Order Service

The Kubernetes manifest contains:

```yaml
image: ghcr.io/precise-charmerr/order-service:${IMAGE_VERSION}
```

The CD job substitutes the version using:

```bash
sed "s|\${IMAGE_VERSION}|${IMAGE_VERSION}|g" \
  k8s/apps/order-service.yaml | kubectl apply -f -
```

For:

```text
IMAGE_VERSION=v1.0.0
```

the final image becomes:

```text
ghcr.io/precise-charmerr/order-service:v1.0.0
```

---

# 22. Deploy Notification Service

The same process is used:

```bash
sed "s|\${IMAGE_VERSION}|${IMAGE_VERSION}|g" \
  k8s/apps/notification-service.yaml | kubectl apply -f -
```

The resulting image is:

```text
ghcr.io/precise-charmerr/notification-service:v1.0.0
```

---

# 23. Rollout Verification

The workflow waits for deployments:

```bash
kubectl rollout status deployment/kafka \
  -n kafka-demo \
  --timeout=180s
```

```bash
kubectl rollout status deployment/order-service \
  -n kafka-demo \
  --timeout=180s
```

```bash
kubectl rollout status deployment/notification-service \
  -n kafka-demo \
  --timeout=180s
```

This prevents the pipeline from reporting success before Kubernetes has successfully rolled out the deployments.

---

# 24. Final Deployment Verification

The workflow executes:

```bash
kubectl get deployments -n kafka-demo
```

```bash
kubectl get pods -n kafka-demo
```

```bash
kubectl get services -n kafka-demo
```

Expected architecture:

```text
kafka                  1/1
order-service          1/1
notification-service   3/3
```

---

# 25. Complete Pipeline

```text
                    Git Push
                       |
                       v
                +-------------+
                |   version   |
                +------+------+
                       |
             +---------+---------+
             |                   |
             v                   v
     +---------------+   +-------------------+
     | order-service |   | notification      |
     |     tests     |   |     tests         |
     +-------+-------+   +---------+---------+
             |                     |
             +----------+----------+
                        |
                        v
                +---------------+
                |    publish    |
                +-------+-------+
                        |
                 Docker Images
                        |
                        v
                       GHCR
                        |
                        v
                +---------------+
                |    deploy     |
                | self-hosted   |
                +-------+-------+
                        |
                        v
                     kubectl
                        |
                        v
                  Kubernetes
                        |
          +-------------+-------------+
          |             |             |
          v             v             v
        Kafka        Order         Notification
                     Service        Service x3
```

---

# 26. CI vs CD

## Continuous Integration

CI is responsible for:

```text
Code
 |
 v
Checkout
 |
 v
Compile/Test
 |
 v
Build
```

In this project:

```text
Order Service tests
Notification Service tests
```

are CI activities.

---

## Continuous Delivery/Deployment

CD is responsible for:

```text
Docker Image
     |
     v
Registry
     |
     v
Kubernetes
```

In this project:

```text
GHCR
 |
 v
Self-Hosted Runner
 |
 v
kubectl
 |
 v
Kubernetes
```

---

# 27. Why GHCR is Between Build and Kubernetes

Kubernetes does not build our Spring Boot application.

The pipeline first creates a Docker image:

```text
Order Service
     |
     v
Docker Image
```

Then pushes it to GHCR:

```text
GHCR
```

Kubernetes pulls the image:

```text
GHCR
  |
  | docker image
  v
Kubernetes Node
  |
  v
Pod
```

This separates:

```text
Build
```

from:

```text
Deployment
```

---

# 28. Version Flow

The same version flows through the pipeline:

```text
VERSION
v1.0.0
   |
   +------------------------+
   |                        |
   v                        v
Docker Image              Kubernetes
   |                        |
   v                        v
order-service:v1.0.0      Deployment
notification-service:v1.0.0
```

This makes it possible to identify which application version is being deployed.

---

# 29. Important GitHub Actions Concepts Used

## `runs-on`

Defines where a job runs.

Example:

```yaml
runs-on: ubuntu-latest
```

uses a GitHub-hosted runner.

Example:

```yaml
runs-on: self-hosted
```

uses a matching self-hosted runner.

---

## `needs`

Defines dependencies between jobs.

Example:

```yaml
needs:
  - version
  - publish
```

means the deployment waits for those jobs to complete successfully.

---

## Matrix

The publish job uses:

```yaml
strategy:
  matrix:
    service:
      - order-service
      - notification-service
```

This allows one job definition to process multiple services.

---

## Secrets / `GITHUB_TOKEN`

The workflow uses:

```text
GITHUB_TOKEN
```

for GitHub-related authentication, including GHCR publishing.

Do not hard-code credentials in workflow files.

---

# 30. Production Improvements

The current pipeline is a learning/demo implementation.

Possible production improvements include:

- Immutable image tags
- Deploy by image digest
- Kubernetes RBAC for deployment
- GitHub Environments
- Production approval gates
- Separate staging and production
- Dedicated self-hosted runner
- Runner as a system service
- Runner groups
- Custom runner labels
- Secret management
- Deployment rollback strategy
- Health checks
- Automated smoke tests
- Monitoring
- Alerting

These are not required for the current Killercoda demonstration.

---

# 31. Interview Explanation

A concise explanation:

> "I implemented CI/CD using GitHub Actions. On every push to main, the pipeline validates the application version, runs tests for both Spring Boot services, builds multi-platform Docker images, and pushes them to GHCR. After successful publishing, a deployment job runs on a self-hosted GitHub Actions runner inside the Kubernetes environment. The runner executes kubectl commands to deploy Kafka and the microservices and waits for Kubernetes rollouts to complete."

---
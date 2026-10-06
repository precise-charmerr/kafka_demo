# Docker and Kubernetes Commands Used in Kafka Demo

This document contains the Docker and Kubernetes commands used while building and deploying the Kafka + Spring Boot microservices project.

---

# 1. Docker

## Check Docker Version

```bash
docker --version
```

Example:

```text
Docker version 29.8.0
```

---

## Check Docker Compose Version

```bash
docker compose version
```

---

## Test Docker Installation

```bash
docker run hello-world
```

This downloads and runs the `hello-world` image to verify that Docker is working.

---

# 2. Docker Images

## List Images

```bash
docker images
```

---

## Pull an Image

Example:

```bash
docker pull apache/kafka:4.0.1
```

---

## Remove an Image

```bash
docker rmi <image>
```

---

# 3. Docker Containers

## List Running Containers

```bash
docker ps
```

---

## List All Containers

```bash
docker ps -a
```

---

## Run a Container

Example:

```bash
docker run -d \
  --name kafka \
  -p 9092:9092 \
  apache/kafka:4.0.1
```

---

## View Container Logs

```bash
docker logs kafka
```

Follow logs:

```bash
docker logs -f kafka
```

---

## Execute a Command Inside a Container

```bash
docker exec -it kafka bash
```

If bash is unavailable:

```bash
docker exec -it kafka sh
```

---

## Stop a Container

```bash
docker stop kafka
```

---

## Start a Container

```bash
docker start kafka
```

---

## Remove a Container

```bash
docker rm kafka
```

---

# 4. Docker Compose

Our project contains:

```text
compose.yaml
```

Start services:

```bash
docker compose up -d
```

View services:

```bash
docker compose ps
```

View logs:

```bash
docker compose logs
```

Follow logs:

```bash
docker compose logs -f
```

Stop services:

```bash
docker compose down
```

---

# 5. Docker Build

Build a Docker image:

```bash
docker build -t order-service .
```

Example with a specific Dockerfile:

```bash
docker build \
  -t order-service:v1.0.0 \
  ./order-service
```

---

# 6. Docker Multi-Architecture Build

Our CI pipeline builds:

```text
linux/amd64
linux/arm64
```

The GitHub Actions workflow uses:

```yaml
platforms: linux/amd64,linux/arm64
```

Docker Buildx is used for multi-platform builds.

The workflow uses:

```yaml
- name: Set up QEMU
  uses: docker/setup-qemu-action@v3

- name: Set up Docker Buildx
  uses: docker/setup-buildx-action@v3
```

---

# 7. GitHub Container Registry

Our images are published to:

```text
ghcr.io
```

Example:

```text
ghcr.io/precise-charmerr/order-service:v1.0.0
```

and:

```text
ghcr.io/precise-charmerr/notification-service:v1.0.0
```

Docker login:

```bash
docker login ghcr.io
```

In GitHub Actions, authentication is performed using:

```yaml
uses: docker/login-action@v3
```

with:

```yaml
username: ${{ github.actor }}
password: ${{ secrets.GITHUB_TOKEN }}
```

---

# 8. Kubernetes

## Check kubectl Version

```bash
kubectl version --client
```

---

# 9. Check Kubernetes Cluster

```bash
kubectl cluster-info
```

This displays information about the Kubernetes control plane.

---

## List Nodes

```bash
kubectl get nodes
```

More details:

```bash
kubectl get nodes -o wide
```

---

# 10. Kubernetes Namespaces

## List Namespaces

```bash
kubectl get namespaces
```

Short form:

```bash
kubectl get ns
```

---

## Create Namespace

```bash
kubectl create namespace kafka-demo
```

In our project, the namespace is defined declaratively:

```bash
kubectl apply -f k8s/infra/namespace.yaml
```

---

# 11. Apply Kubernetes YAML

Generic command:

```bash
kubectl apply -f <file>
```

Our project:

```bash
kubectl apply -f k8s/infra/namespace.yaml
```

Kafka:

```bash
kubectl apply -f k8s/infra/kafka.yaml
```

---

# 12. Kubernetes Deployments

List deployments:

```bash
kubectl get deployments -n kafka-demo
```

Short form:

```bash
kubectl get deploy -n kafka-demo
```

Example:

```text
NAME                   READY
kafka                  1/1
order-service          1/1
notification-service   3/3
```

---

# 13. Kubernetes Pods

List pods:

```bash
kubectl get pods -n kafka-demo
```

Watch pods:

```bash
kubectl get pods -n kafka-demo -w
```

More details:

```bash
kubectl get pods -n kafka-demo -o wide
```

The `-o wide` output shows information such as the node on which the pod is running.

---

# 14. Pod Logs

Generic:

```bash
kubectl logs <pod-name> -n kafka-demo
```

Follow logs:

```bash
kubectl logs -f <pod-name> -n kafka-demo
```

Example:

```bash
kubectl logs -f notification-service-xxxxx -n kafka-demo
```

---

# 15. Execute Commands Inside a Pod

Example:

```bash
kubectl exec -it <pod-name> -n kafka-demo -- sh
```

Or execute a specific command:

```bash
kubectl exec <pod-name> -n kafka-demo -- printenv
```

We used:

```bash
kubectl exec deployment/order-service \
  -n kafka-demo \
  -- printenv | grep SPRING_KAFKA
```

This verified:

```text
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

---

# 16. Kubernetes Services

List services:

```bash
kubectl get services -n kafka-demo
```

Short form:

```bash
kubectl get svc -n kafka-demo
```

Our Kafka service looked similar to:

```text
NAME    TYPE        CLUSTER-IP      PORT(S)
kafka   ClusterIP   10.x.x.x        9092/TCP,9093/TCP
```

---

# 17. Kubernetes DNS

Inside Kubernetes, services receive DNS names.

Our Kafka service was:

```text
kafka
```

From another pod in the same namespace:

```text
kafka:9092
```

Full DNS name:

```text
kafka.kafka-demo.svc.cluster.local
```

We tested DNS using a temporary BusyBox pod:

```bash
kubectl run test-client \
  -n kafka-demo \
  --image=busybox:1.36 \
  --rm \
  -it \
  --restart=Never \
  -- nslookup kafka
```

The important result was:

```text
Name: kafka.kafka-demo.svc.cluster.local
Address: <cluster IP>
```

---

# 18. Port Forwarding

To access the Order Service from our local machine:

```bash
kubectl port-forward \
  -n kafka-demo \
  svc/order-service \
  8080:8080
```

This creates:

```text
localhost:8080
      |
      v
Kubernetes Service
      |
      v
Order Service Pod
```

Then we could send:

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"product":"pencil","quantity":10}'
```

---

# 19. Kubernetes Rollout Status

Check deployment rollout:

```bash
kubectl rollout status \
  deployment/order-service \
  -n kafka-demo
```

Notification Service:

```bash
kubectl rollout status \
  deployment/notification-service \
  -n kafka-demo
```

Kafka:

```bash
kubectl rollout status \
  deployment/kafka \
  -n kafka-demo
```

With timeout:

```bash
kubectl rollout status \
  deployment/order-service \
  -n kafka-demo \
  --timeout=180s
```

---

# 20. Restart a Deployment

```bash
kubectl rollout restart deployment/order-service -n kafka-demo
```

Notification Service:

```bash
kubectl rollout restart deployment/notification-service -n kafka-demo
```

---

# 21. Deployment Details

```bash
kubectl describe deployment \
  order-service \
  -n kafka-demo
```

---

# 22. Pod Details

```bash
kubectl describe pod \
  <pod-name> \
  -n kafka-demo
```

This is useful when a pod is:

```text
Pending
CrashLoopBackOff
ImagePullBackOff
ErrImagePull
```

---

# 23. Service Details

```bash
kubectl describe service \
  kafka \
  -n kafka-demo
```

---

# 24. Delete Kubernetes Resources

Delete a specific manifest:

```bash
kubectl delete -f k8s/infra/kafka.yaml
```

Delete a deployment:

```bash
kubectl delete deployment \
  order-service \
  -n kafka-demo
```

Delete a service:

```bash
kubectl delete service \
  order-service \
  -n kafka-demo
```

Delete the entire namespace:

```bash
kubectl delete namespace kafka-demo
```

Warning:

Deleting the namespace deletes the resources inside it.

---

# 25. Environment Variable Substitution

Our application manifests contain:

```yaml
image: ghcr.io/precise-charmerr/order-service:${IMAGE_VERSION}
```

The deployment process sets:

```text
IMAGE_VERSION=v1.0.0
```

The CD workflow substitutes the value before applying the manifest.

The final Kubernetes image becomes:

```text
ghcr.io/precise-charmerr/order-service:v1.0.0
```

Our CD workflow uses:

```bash
sed "s|\${IMAGE_VERSION}|${IMAGE_VERSION}|g" \
  k8s/apps/order-service.yaml | kubectl apply -f -
```

and similarly for notification-service.

---

# 26. Kubernetes Architecture Used in This Project

```text
Kubernetes Cluster
│
├── Namespace: kafka-demo
│
├── Kafka Deployment
│     └── Kafka Pod
│
├── Kafka Service
│     └── kafka:9092
│
├── Order Service Deployment
│     └── Order Service Pod
│
├── Order Service
│     └── ClusterIP Service
│
└── Notification Service Deployment
      ├── Notification Pod
      ├── Notification Pod
      └── Notification Pod
```

---

# 27. Why Kafka Uses `kafka:9092`

Inside Kubernetes, the application does not use:

```text
localhost:9092
```

because `localhost` means the current container/pod.

Instead:

```text
kafka:9092
```

means:

```text
Kafka Kubernetes Service
        |
        v
Kafka Pod
```

Therefore the application environment variable is:

```yaml
SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

---

# 28. Checking Application Configuration

```bash
kubectl exec deployment/order-service \
  -n kafka-demo \
  -- printenv | grep SPRING_KAFKA
```

Expected:

```text
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

---

# 29. Useful Debugging Sequence

When an application isn't working, check in this order:

## 1. Nodes

```bash
kubectl get nodes
```

## 2. Pods

```bash
kubectl get pods -n kafka-demo
```

## 3. Services

```bash
kubectl get svc -n kafka-demo
```

## 4. Pod logs

```bash
kubectl logs <pod-name> -n kafka-demo
```

## 5. Pod details

```bash
kubectl describe pod <pod-name> -n kafka-demo
```

## 6. Deployment

```bash
kubectl get deployment -n kafka-demo
```

## 7. Rollout

```bash
kubectl rollout status deployment/<deployment-name> -n kafka-demo
```

---

# 30. Useful Combined Verification

```bash
kubectl get nodes
```

```bash
kubectl get deployments -n kafka-demo
```

```bash
kubectl get pods -n kafka-demo -o wide
```

```bash
kubectl get services -n kafka-demo
```

```bash
kubectl get all -n kafka-demo
```

---

# 31. Important Commands to Remember for Interviews

### Docker

```bash
docker ps
docker ps -a
docker images
docker build
docker run
docker exec
docker logs
docker stop
docker start
docker rm
docker compose up -d
docker compose down
```

### Kubernetes

```bash
kubectl get nodes
kubectl get pods
kubectl get deployments
kubectl get services
kubectl apply -f
kubectl delete -f
kubectl logs
kubectl describe
kubectl exec
kubectl rollout status
kubectl rollout restart
kubectl port-forward
```

---

# 32. Most Important Mental Model

Docker:

```text
Image
  |
  v
Container
```

Kubernetes:

```text
Deployment
   |
   v
Pod
   |
   v
Container
```

Kubernetes networking:

```text
Application Pod
      |
      | kafka:9092
      v
Kafka Service
      |
      v
Kafka Pod
```

Our deployment:

```text
Docker Image
      |
      v
GHCR
      |
      v
Kubernetes Deployment
      |
      v
Pod
      |
      v
Container
```

CI/CD:

```text
Git Push
   |
   v
GitHub Actions
   |
   +-- Test
   |
   +-- Build
   |
   +-- Push Docker Image
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
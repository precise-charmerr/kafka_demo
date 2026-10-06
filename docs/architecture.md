# Kafka Demo - Architecture

## 1. Project Overview

This project demonstrates an end-to-end Spring Boot microservices architecture using:

- Java 21
- Spring Boot
- Apache Kafka
- Docker
- Kubernetes
- GitHub Actions
- GitHub Container Registry (GHCR)
- Self-hosted GitHub Actions Runner
- Killercoda Kubernetes Playground

The project contains two Spring Boot microservices:

1. `order-service`
2. `notification-service`

Kafka is used for asynchronous communication between the services.

---

# 2. High-Level Architecture

```text
                         Client
                      Postman / curl
                           |
                           | HTTP POST
                           v
                 +---------------------+
                 |    Order Service    |
                 |     Spring Boot     |
                 |       :8080         |
                 +----------+----------+
                            |
                            | KafkaTemplate
                            | order-created
                            v
                 +---------------------+
                 |       Kafka         |
                 |                     |
                 | Topic:              |
                 | order-created       |
                 +----------+----------+
                            |
                            | Consumer Group
                            v
              +-------------+-------------+
              |             |             |
              v             v             v
       +-------------+ +-------------+ +-------------+
       | Notification| | Notification| | Notification|
       |   Service   | |   Service   | |   Service   |
       |   Pod #1    | |   Pod #2    | |   Pod #3    |
       +-------------+ +-------------+ +-------------+
```

---

# 3. Request Flow

When a client creates an order:

```text
POST /orders
```

the request reaches:

```text
Order Service
```

The Order Service creates the order and publishes an event to Kafka.

The Kafka topic is:

```text
order-created
```

The Notification Service consumes the event.

The overall flow is:

```text
HTTP Request
     |
     v
Order Service
     |
     v
Kafka
     |
     v
Notification Service
```

---

# 4. Order Service

The Order Service is responsible for handling order creation.

Example request:

```http
POST /orders
Content-Type: application/json
```

```json
{
  "product": "pencil",
  "quantity": 10
}
```

The service creates an order and publishes an event.

Example event:

```json
{
  "orderId": 3,
  "product": "pencil",
  "quantity": 10,
  "status": "CREATED"
}
```

The event is sent to Kafka.

---

# 5. Kafka

Kafka acts as the messaging layer between the microservices.

The topic used by the project is:

```text
order-created
```

Kafka allows the Order Service and Notification Service to communicate asynchronously.

Instead of:

```text
Order Service
      |
      | direct HTTP call
      v
Notification Service
```

we use:

```text
Order Service
      |
      v
    Kafka
      |
      v
Notification Service
```

This reduces direct coupling between the services.

---

# 6. Kafka Topic

Topic:

```text
order-created
```

The topic contains events produced by the Order Service.

Initially the topic was created with one partition.

Later the topic was increased to two partitions while experimenting with Kafka behavior.

---

# 7. Kafka Partition

A Kafka topic is divided into partitions.

Example:

```text
order-created
       |
       +-- Partition 0
       |
       +-- Partition 1
```

Partitions provide:

- Parallelism
- Scalability
- Ordering within each partition

Kafka ordering is guaranteed within a partition, not globally across all partitions.

---

# 8. Kafka Message Key

The Order Service sends the `orderId` as the Kafka message key.

Conceptually:

```text
Key:
orderId

Value:
Order JSON
```

Example:

```text
Key   = 11

Value = {
          "orderId": 11,
          "product": "pencil",
          "quantity": 25,
          "status": "CREATED"
        }
```

Using the same key causes related messages to be routed consistently to the same partition when the default partitioning behavior is used.

This is useful when we want messages for the same order to maintain ordering.

---

# 9. Consumer Group

The Notification Service instances belong to the same Kafka consumer group.

Conceptually:

```text
Consumer Group
notification-group
       |
       +-- Notification Pod 1
       +-- Notification Pod 2
       +-- Notification Pod 3
```

Consumers in the same group share the work of consuming partitions.

---

# 10. One Partition vs Multiple Consumers

An important experiment in this project was running:

```text
3 Notification Service replicas
```

while Kafka had:

```text
1 partition
```

The result was that only one consumer could actively consume that partition within the consumer group.

Conceptually:

```text
Kafka

Partition 0
    |
    v
Notification Pod 1

Notification Pod 2
    |
    +-- idle for this partition

Notification Pod 3
    |
    +-- idle for this partition
```

Therefore:

```text
1 partition
    =>
maximum 1 active consumer for that partition
```

If the topic has three partitions:

```text
Partition 0 -> Consumer 1
Partition 1 -> Consumer 2
Partition 2 -> Consumer 3
```

then three consumers can actively consume in parallel.

This is why simply increasing Kubernetes replicas does not automatically increase Kafka consumption parallelism.

---

# 11. Kubernetes Architecture

The application is deployed into the namespace:

```text
kafka-demo
```

The namespace contains:

```text
kafka-demo
|
+-- Kafka Deployment
|     |
|     +-- Kafka Pod
|
+-- Kafka Service
|
+-- Order Service Deployment
|     |
|     +-- Order Service Pod
|
+-- Order Service Service
|
+-- Notification Service Deployment
      |
      +-- Notification Pod
      +-- Notification Pod
      +-- Notification Pod
|
+-- Notification Service Service
```

---

# 12. Kubernetes Service Discovery

Applications inside Kubernetes communicate with Kafka through:

```text
kafka:9092
```

The Kafka Service provides a stable DNS name.

Full DNS name:

```text
kafka.kafka-demo.svc.cluster.local
```

Therefore the Spring Boot services use:

```text
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

We do not use:

```text
localhost:9092
```

inside Kubernetes.

`localhost` would refer to the current pod/container, not the Kafka pod.

---

# 13. Docker Architecture

Each Spring Boot service has its own Docker image.

Images:

```text
ghcr.io/precise-charmerr/order-service:v1.0.0

ghcr.io/precise-charmerr/notification-service:v1.0.0
```

The images are built for:

```text
linux/amd64
linux/arm64
```

The images are pushed to GitHub Container Registry.

---

# 14. Kubernetes Image Flow

```text
Spring Boot Source Code
          |
          v
       Maven
          |
          v
        JAR
          |
          v
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

---

# 15. CI/CD Architecture

The complete system is:

```text
Developer
    |
    | git push
    v
 GitHub
    |
    v
GitHub Actions
    |
    +--------------------+
    |                    |
    v                    v
Run Tests             Build Images
    |                    |
    |                    v
    |                   GHCR
    |                    |
    +---------+----------+
              |
              v
       Self-Hosted Runner
          Killercoda
              |
              | kubectl
              v
       Kubernetes Cluster
              |
       +------+------+
       |      |      |
      Kafka  Order  Notification
```

---

# 16. Why Self-Hosted Runner?

The deployment job uses:

```yaml
runs-on: self-hosted
```

This means the deployment job runs on a registered self-hosted runner instead of a GitHub-hosted runner.

In this project:

```text
Self-Hosted Runner
        |
        v
Killercoda controlplane
        |
        v
Killercoda Kubernetes
```

The runner can therefore execute:

```bash
kubectl apply
kubectl get pods
kubectl rollout status
```

against the Killercoda cluster.

GitHub supports routing self-hosted jobs using labels and groups.

---

# 17. Complete Architecture

```text
                         GitHub
                            |
                            |
                     GitHub Actions
                            |
                +-----------+-----------+
                |                       |
                v                       v
             CI Tests              Build/Publish
                                        |
                                        v
                                       GHCR
                                        |
                                        v
                              Self-Hosted Runner
                                  Killercoda
                                        |
                                      kubectl
                                        |
                                        v
                         +-------------------------+
                         | Kubernetes Cluster      |
                         |                         |
                         | Namespace: kafka-demo   |
                         |                         |
                         | Kafka                   |
                         | Order Service            |
                         | Notification Service x3 |
                         +------------+------------+
                                      |
                                      |
                                      v
                              Kafka Messaging
                                      |
                         +------------+------------+
                         |                         |
                         v                         v
                  Order Service             Notification
```

---

# 18. Main Technologies

| Technology | Purpose |
|---|---|
| Java 21 | Application runtime |
| Spring Boot | Microservices |
| Kafka | Asynchronous messaging |
| Docker | Containerization |
| GHCR | Docker image registry |
| Kubernetes | Container orchestration |
| GitHub Actions | CI/CD |
| Self-hosted Runner | Executes deployment job |
| Killercoda | Kubernetes learning environment |

---

# 19. Key Interview Concepts Demonstrated

This project demonstrates:

- REST APIs
- Spring Boot
- Microservices
- Kafka producers
- Kafka consumers
- Kafka topics
- Kafka partitions
- Kafka consumer groups
- Kafka offsets
- Kafka message keys
- Docker images
- Docker containers
- Multi-platform Docker builds
- Kubernetes Deployments
- Kubernetes Pods
- Kubernetes Services
- Kubernetes DNS
- Kubernetes namespaces
- Kubernetes scaling
- GitHub Actions
- CI/CD
- GHCR
- Self-hosted runners
- `kubectl`
- Kubernetes rollout management

---

# 20. One-Line Architecture Explanation

For an interview:

> "I built two Spring Boot microservices where Order Service publishes order-created events to Kafka and Notification Service consumes them through a consumer group. I containerized the services using Docker, published multi-platform images to GHCR, deployed them to Kubernetes, and implemented CI/CD using GitHub Actions with a self-hosted runner for Kubernetes deployment."
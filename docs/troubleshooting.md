# Troubleshooting Guide

This document contains problems encountered while building and deploying the Kafka + Spring Boot + Docker + Kubernetes + GitHub Actions project.

The purpose is to provide a quick reference when something breaks later.

---

# 1. GitHub Actions Runner - `kubectl localhost:8080`

## Symptom

The CD job fails at:

```bash
kubectl get nodes
```

with:

```text
The connection to the server localhost:8080 was refused
```

Example:

```text
Get "http://localhost:8080/api?timeout=32s":
dial tcp 127.0.0.1:8080:
connect: connection refused
```

---

## Cause

The GitHub Actions runner was running as the Linux user:

```text
runner
```

but that user did not have a Kubernetes kubeconfig.

Therefore `kubectl` could not determine which Kubernetes API server to use.

---

## Diagnosis

Check:

```bash
whoami
```

Expected:

```text
runner
```

Check:

```bash
ls -la ~/.kube/
```

If the config is missing, `kubectl` may fall back to:

```text
localhost:8080
```

---

## Fix

As root:

```bash
mkdir -p /home/runner/.kube
```

Copy the Kubernetes configuration:

```bash
cp /etc/kubernetes/admin.conf \
  /home/runner/.kube/config
```

Change ownership:

```bash
chown -R runner:runner \
  /home/runner/.kube
```

Set permissions:

```bash
chmod 600 \
  /home/runner/.kube/config
```

Switch back to runner:

```bash
su - runner
```

Test:

```bash
kubectl get nodes
```

---

# 2. Check Which Kubernetes Context Is Being Used

Run:

```bash
kubectl config current-context
```

List contexts:

```bash
kubectl config get-contexts
```

View configuration:

```bash
kubectl config view
```

Do not expose or commit sensitive credential information from the kubeconfig.

---

# 3. GitHub Runner Cannot Start as Root

## Symptom

Runner configuration reports:

```text
Must not run with sudo
```

---

## Cause

The runner was being configured as root.

---

## Fix

Create/use a normal user:

```bash
su - runner
```

Then:

```bash
cd /home/runner/actions-runner
```

Run:

```bash
./config.sh
```

and:

```bash
./run.sh
```

---

# 4. Runner Shows Offline

## Symptom

GitHub shows:

```text
killercoda-runner
Offline
```

---

## Cause

The runner process is not running.

---

## Fix

On the runner machine:

```bash
cd /home/runner/actions-runner
./run.sh
```

Expected:

```text
√ Connected to GitHub
Listening for Jobs
```

The runner application must be running for it to accept jobs. GitHub also requires the runner host to have outbound network connectivity to GitHub, including HTTPS on port 443.

---

# 5. GitHub Workflow Is Stuck in Queued

## Symptom

The deploy job remains:

```text
Queued
```

---

## Possible Causes

### Runner is offline

Check:

```text
GitHub
→ Actions
→ Runners
```

---

### No matching label

The workflow might use:

```yaml
runs-on: [self-hosted, linux, x64]
```

but the runner does not have the required labels.

A self-hosted runner must match all specified labels.

---

### Runner is busy

Another workflow may already be using the runner.

---

# 6. Runner Group Problem

## Symptom

During runner configuration:

```text
Could not find any self-hosted runner group named ...
```

---

## Cause

A value was entered into the runner group prompt that does not exist.

---

## Correct Setup

When prompted:

```text
Enter the name of the runner group to add this runner to:
[press Enter for Default]
```

Press:

```text
Enter
```

to use:

```text
Default
```

Then provide the runner name when prompted.

---

# 7. Registration Token Problem

## Symptom

Runner registration fails.

---

## Possible Cause

The registration token has expired.

Registration tokens are temporary.

---

## Fix

Generate a new runner registration token from GitHub and repeat the registration process.

Never commit the token to Git.

Do not put it into:

```text
README.md
workflow YAML
source code
shell scripts
```

---

# 8. `kubectl` Command Not Found

## Symptom

```text
kubectl: command not found
```

---

## Diagnosis

Run:

```bash
which kubectl
```

or:

```bash
command -v kubectl
```

---

## Fix

Install kubectl on the runner machine.

After installation:

```bash
kubectl version --client
```

---

# 9. Kubernetes Nodes Not Ready

## Symptom

```bash
kubectl get nodes
```

shows:

```text
NotReady
```

---

## Diagnosis

```bash
kubectl describe node <node-name>
```

Also check:

```bash
kubectl get pods -n kube-system
```

Look for pods that are:

```text
Pending
CrashLoopBackOff
Error
```

---

# 10. Pod Stuck in `Pending`

## Diagnosis

```bash
kubectl get pods -n kafka-demo
```

Then:

```bash
kubectl describe pod <pod-name> -n kafka-demo
```

Look at the Events section.

Possible causes:

- Insufficient resources
- Scheduling constraints
- Node problems
- Volume problems

---

# 11. Pod in `CrashLoopBackOff`

## Diagnosis

Check logs:

```bash
kubectl logs <pod-name> -n kafka-demo
```

If the container restarted:

```bash
kubectl logs <pod-name> \
  -n kafka-demo \
  --previous
```

Then inspect:

```bash
kubectl describe pod <pod-name> -n kafka-demo
```

---

# 12. `ImagePullBackOff`

## Symptom

```text
ImagePullBackOff
```

or:

```text
ErrImagePull
```

---

## Diagnosis

```bash
kubectl describe pod <pod-name> -n kafka-demo
```

Check:

```text
Events
```

Possible causes:

- Image name is wrong
- Image tag does not exist
- Registry is unavailable
- Registry authentication is required

---

## Check Image

Example:

```text
ghcr.io/precise-charmerr/order-service:v1.0.0
```

Make sure the version in:

```text
VERSION
```

matches the published image tag.

---

# 13. Application Cannot Connect to Kafka

## Symptom

Spring Boot logs show Kafka connection failures.

---

## Check Environment Variable

For Order Service:

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

## Check Kafka Service

```bash
kubectl get svc -n kafka-demo
```

Expected Kafka service:

```text
kafka
```

with port:

```text
9092
```

---

# 14. Kafka DNS Problem

Test from inside the Kubernetes cluster:

```bash
kubectl run test-client \
  -n kafka-demo \
  --image=busybox:1.36 \
  --rm \
  -it \
  --restart=Never \
  -- nslookup kafka
```

Expected:

```text
Name: kafka.kafka-demo.svc.cluster.local
Address: <cluster IP>
```

If DNS works, Kubernetes service discovery is functioning.

---

# 15. Important Kafka `localhost` Difference

Outside Kubernetes:

```text
localhost:9092
```

may refer to Kafka exposed on the local machine.

Inside Kubernetes:

```text
localhost:9092
```

means:

```text
the current pod/container
```

Therefore applications inside Kubernetes should use:

```text
kafka:9092
```

because `kafka` is the Kubernetes Service name.

---

# 16. Notification Service Has Multiple Pods but Only One Gets Messages

## Symptom

There are three replicas:

```text
notification-service-1
notification-service-2
notification-service-3
```

but only one receives the Kafka message.

---

## Cause

Kafka topic has one partition.

All Notification Service pods belong to the same consumer group.

One partition can be actively assigned to only one consumer in that group at a time.

Therefore:

```text
1 partition
    |
    +-- Consumer 1 -> active
    |
    +-- Consumer 2 -> idle
    |
    +-- Consumer 3 -> idle
```

---

## To Increase Parallel Consumption

Increase Kafka topic partitions.

Example:

```text
3 partitions
```

can allow up to:

```text
3 active consumers
```

within the same consumer group.

However, partitioning also affects ordering and message distribution.

---

# 17. Kafka Message Always Goes to the Same Partition

If the producer uses a message key such as:

```text
orderId
```

Kafka's partitioning behavior can consistently map the same key to the same partition.

Therefore:

```text
orderId = 11
```

will generally continue going to the same partition as long as the relevant partitioning setup remains compatible.

This is useful for preserving ordering for messages belonging to the same entity.

---

# 18. Kubernetes Service Cannot Be Reached

## Check Service

```bash
kubectl get svc -n kafka-demo
```

## Check Endpoints

```bash
kubectl get endpoints -n kafka-demo
```

For newer Kubernetes versions, also check EndpointSlices:

```bash
kubectl get endpointslices -n kafka-demo
```

If a Service has no endpoints, check its selector and pod labels.

---

# 19. Port Forwarding Not Working

Command:

```bash
kubectl port-forward \
  -n kafka-demo \
  svc/order-service \
  8080:8080
```

Then:

```bash
curl http://localhost:8080
```

If it fails, check:

```bash
kubectl get svc -n kafka-demo
```

and:

```bash
kubectl get pods -n kafka-demo
```

Also verify the application is listening on the expected port.

---

# 20. Order Service Request Test

Use:

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"product":"pencil","quantity":10}'
```

Then check Notification Service logs:

```bash
kubectl logs \
  deployment/notification-service \
  -n kafka-demo
```

The notification service should show the consumed order event.

---

# 21. Deployment Rollout Stuck

Check:

```bash
kubectl rollout status \
  deployment/order-service \
  -n kafka-demo
```

Then:

```bash
kubectl get pods -n kafka-demo
```

If a pod is failing:

```bash
kubectl describe pod <pod-name> -n kafka-demo
```

and:

```bash
kubectl logs <pod-name> -n kafka-demo
```

---

# 22. Deployment Uses Wrong Image Version

Check the deployment:

```bash
kubectl get deployment order-service \
  -n kafka-demo \
  -o jsonpath='{.spec.template.spec.containers[0].image}'
```

Expected:

```text
ghcr.io/precise-charmerr/order-service:v1.0.0
```

Check Notification Service:

```bash
kubectl get deployment notification-service \
  -n kafka-demo \
  -o jsonpath='{.spec.template.spec.containers[0].image}'
```

---

# 23. Check All Application Resources

```bash
kubectl get all -n kafka-demo
```

Useful for getting a quick overview of:

- Pods
- Services
- Deployments
- ReplicaSets

---

# 24. Complete Health Check

Run:

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

Then inspect logs if required:

```bash
kubectl logs deployment/order-service -n kafka-demo
```

```bash
kubectl logs deployment/notification-service -n kafka-demo
```

---

# 25. Emergency: Delete and Redeploy

If this is a temporary learning environment and you want to completely recreate the application:

```bash
kubectl delete namespace kafka-demo
```

Then recreate:

```bash
kubectl apply -f k8s/infra/namespace.yaml
```

Deploy Kafka:

```bash
kubectl apply -f k8s/infra/kafka.yaml
```

Deploy applications using the appropriate image version.

Warning:

Deleting the namespace deletes the resources inside it.

Do not use this casually in production.

---

# 26. Useful Debugging Order

When something breaks, do not immediately change code.

Follow this sequence:

```text
1. Is Kubernetes healthy?
        |
        v
   kubectl get nodes

2. Are pods running?
        |
        v
   kubectl get pods

3. Are services present?
        |
        v
   kubectl get svc

4. Are applications logging errors?
        |
        v
   kubectl logs

5. Are pods correctly configured?
        |
        v
   kubectl describe pod

6. Can services resolve?
        |
        v
   nslookup

7. Can applications reach Kafka?
        |
        v
   check bootstrap server

8. Is the correct Docker image deployed?
        |
        v
   kubectl get deployment -o ...
```

---

# 27. Quick Command Cheat Sheet

## Docker

```bash
docker ps
docker ps -a
docker images
docker logs <container>
docker exec -it <container> sh
docker stop <container>
docker start <container>
docker rm <container>
docker build -t <image> .
docker compose up -d
docker compose down
```

## Kubernetes

```bash
kubectl get nodes
kubectl get pods -n kafka-demo
kubectl get deployments -n kafka-demo
kubectl get services -n kafka-demo
kubectl get all -n kafka-demo
kubectl logs <pod> -n kafka-demo
kubectl describe pod <pod> -n kafka-demo
kubectl exec -it <pod> -n kafka-demo -- sh
kubectl apply -f <file>
kubectl delete -f <file>
kubectl rollout status deployment/<name> -n kafka-demo
kubectl rollout restart deployment/<name> -n kafka-demo
kubectl port-forward -n kafka-demo svc/<service> 8080:8080
```

## GitHub Actions Runner

```bash
cd /home/runner/actions-runner
./run.sh
```

Expected:

```text
√ Connected to GitHub
Listening for Jobs
```

## Kubernetes Runner Access

```bash
kubectl config current-context
kubectl get nodes
kubectl get pods -n kafka-demo
```

---

# 28. Most Important Lessons From the Troubleshooting

### Lesson 1

A runner being online does not automatically mean it can access Kubernetes.

```text
Runner connected to GitHub
        !=
Runner authenticated to Kubernetes
```

Both must work.

---

### Lesson 2

Linux users have separate environments.

```text
root
```

having a kubeconfig does not mean:

```text
runner
```

has one.

---

### Lesson 3

`localhost` is relative to where the command runs.

Inside Kubernetes:

```text
localhost
```

means the current container/pod.

On the Killercoda controlplane:

```text
localhost
```

means the controlplane machine.

---

### Lesson 4

More Kubernetes replicas do not automatically mean more Kafka consumers can actively process messages.

Kafka partition count determines consumer parallelism within a consumer group.

---

### Lesson 5

When debugging Kubernetes, start with:

```bash
kubectl get pods
```

then:

```bash
kubectl logs
```

then:

```bash
kubectl describe
```

before changing application code.

---

# 29. Final Mental Model

```text
GitHub Actions Problem
        |
        v
Is runner online?
        |
        v
Can runner run kubectl?
        |
        v
Can kubectl reach Kubernetes?
        |
        v
Are Kubernetes pods healthy?
        |
        v
Can pods communicate?
        |
        v
Can application communicate with Kafka?
        |
        v
Is Kafka topic/partition/consumer setup correct?
        |
        v
Application behavior
```

This sequence prevents randomly changing multiple parts of the system at once.
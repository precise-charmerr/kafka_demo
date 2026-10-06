# Killercoda Self-Hosted GitHub Actions Runner

## 1. Purpose

This document explains how a temporary GitHub Actions self-hosted runner was configured inside the Killercoda Kubernetes control-plane node.

The goal was to allow the GitHub Actions CD job to execute `kubectl` commands directly against the Killercoda Kubernetes cluster.

Architecture:

```text
GitHub
   |
   | GitHub Actions job
   v
Self-Hosted Runner
(Killercoda controlplane)
   |
   | kubectl
   v
Killercoda Kubernetes Cluster
   |
   +-- Kafka
   +-- Order Service
   +-- Notification Service
```

---

# 2. What is a Self-Hosted Runner?

A self-hosted runner is a machine that we manage ourselves and register with GitHub Actions.

Instead of:

```yaml
runs-on: ubuntu-latest
```

which uses a GitHub-hosted machine, we can use:

```yaml
runs-on: self-hosted
```

which tells GitHub Actions to find an available registered self-hosted runner.

The runner machine can be:

- Physical server
- Virtual machine
- Cloud VM
- On-premises server
- Container
- Special hardware machine
- Machine inside a private network

GitHub documents that self-hosted runners can be physical, virtual, containerized, on-premises, or cloud-based.

Reference:

https://docs.github.com/en/actions/concepts/runners/self-hosted-runners

---

# 3. Why Did We Use a Self-Hosted Runner?

Our Kubernetes cluster was running inside Killercoda.

The GitHub-hosted runner:

```text
GitHub-hosted Ubuntu
```

was not the machine that had direct access to the Killercoda Kubernetes API.

We therefore placed a GitHub Actions runner directly inside the Killercoda environment.

The final deployment path became:

```text
GitHub Actions
       |
       v
Killercoda Self-Hosted Runner
       |
       v
kubectl
       |
       v
Killercoda Kubernetes API
```

This allowed the CD workflow to execute:

```bash
kubectl apply ...
kubectl rollout status ...
kubectl get pods ...
```

against the Killercoda cluster.

---

# 4. What is the Runner Actually Doing?

The runner is an application/process running on our machine.

We started it using:

```bash
./run.sh
```

After starting it, we saw:

```text
√ Connected to GitHub
Listening for Jobs
```

This means:

```text
Runner
   |
   | connects to GitHub
   v
GitHub Actions
```

The runner waits for GitHub to assign it a job.

When GitHub sees:

```yaml
runs-on: self-hosted
```

it looks for an available self-hosted runner with the required labels.

When the runner receives the job, it executes the workflow commands on its own machine.

For example:

```yaml
- name: Verify Kubernetes access
  run: |
    kubectl get nodes
```

is executed on the Killercoda machine.

Therefore:

```text
GitHub
   |
   | sends job
   v
Killercoda Runner
   |
   | executes kubectl
   v
Killercoda Kubernetes
```

---

# 5. Creating the Runner User

GitHub's runner should not be configured as root.

During setup, running the configuration as root resulted in:

```text
Must not run with sudo
```

Therefore we created a normal Linux user:

```bash
useradd -m runner
```

The runner files were placed under:

```text
/home/runner/actions-runner
```

Ownership was changed:

```bash
chown -R runner:runner /home/runner/actions-runner
```

We then switched to the runner user:

```bash
su - runner
```

The runner application was executed as:

```text
runner
```

instead of:

```text
root
```

---

# 6. Downloading the GitHub Actions Runner

The GitHub repository provides instructions for downloading the appropriate runner version.

The runner used in this setup was:

```text
actions-runner-linux-x64-2.337.0
```

The runner was extracted into:

```text
/home/runner/actions-runner
```

The directory contained files such as:

```text
config.sh
run.sh
svc.sh
```

---

# 7. What Does the Registration Token Do?

This is an important distinction.

The registration token is NOT the token that executes every GitHub Actions job.

It is primarily used to authenticate the process of registering the machine as a GitHub Actions runner.

The flow is:

```text
GitHub
   |
   | Generate temporary registration token
   v
Registration Token
   |
   | used by config.sh
   v
Runner Configuration
   |
   v
Runner registered with GitHub
```

GitHub's documentation states that the registration token is automatically generated, time-limited, and expires after one hour.

Reference:

https://docs.github.com/en/rest/actions/self-hosted-runners

---

# 8. Registration Flow

Conceptually, GitHub gives us:

```text
Repository
    |
    v
Settings
    |
    v
Actions
    |
    v
Runners
    |
    v
New self-hosted runner
```

GitHub provides a temporary registration token.

The runner configuration process uses:

```bash
./config.sh
```

and asks for information such as:

```text
Runner group
Runner name
Labels
```

The registration process connects the machine to the GitHub repository.

Important:

The registration token should NEVER be committed to Git.

Do not put it in:

```text
README.md
workflow YAML
source code
shell scripts
```

The token is temporary and should be treated as a credential.

---

# 9. Runner Group

During configuration we were asked:

```text
Enter the name of the runner group to add this runner to:
[press Enter for Default]
```

We selected:

```text
Default
```

A runner group is a way of organizing and controlling access to runners.

For a simple repository-level setup, the default group is sufficient.

---

# 10. Runner Name

We used a runner name similar to:

```text
killercoda-runner
```

This name identifies the machine in:

```text
GitHub
→ Settings
→ Actions
→ Runners
```

---

# 11. Starting the Runner

As the runner user:

```bash
cd /home/runner/actions-runner
```

Start:

```bash
./run.sh
```

Expected output:

```text
√ Connected to GitHub
Listening for Jobs
```

The terminal must remain active while using this temporary setup.

If the runner process stops, GitHub cannot send jobs to it.

---

# 12. Changing the GitHub Actions Workflow

The CD job was changed from:

```yaml
runs-on: ubuntu-latest
```

to:

```yaml
runs-on: self-hosted
```

Example:

```yaml
deploy:
  name: Deploy to Kubernetes
  runs-on: self-hosted
```

This means:

```text
GitHub
   |
   | find matching self-hosted runner
   v
killercoda-runner
   |
   v
kubectl
```

---

# 13. The Kubernetes Access Problem

Initially the runner was online, but this failed:

```bash
kubectl get nodes
```

The error was:

```text
The connection to the server localhost:8080 was refused
```

The reason was that the `runner` user did not have a Kubernetes kubeconfig.

---

# 14. What is kubeconfig?

`kubectl` needs configuration that tells it:

```text
Which Kubernetes cluster?
Which API server?
Which credentials?
Which context?
```

By default, kubectl looks for:

```text
$HOME/.kube/config
```

For the runner user this becomes:

```text
/home/runner/.kube/config
```

Kubernetes documentation describes this kubeconfig loading behavior.

Reference:

https://kubernetes.io/docs/reference/kubectl/generated/kubectl_config/

---

# 15. Existing Killercoda Kubernetes Configuration

The Killercoda control-plane node had:

```text
/etc/kubernetes/admin.conf
```

It was owned by:

```text
root:root
```

and had restrictive permissions:

```text
-rw------- root root
```

Therefore the normal `runner` user could not simply use it.

---

# 16. Giving the Runner Kubernetes Access

As root:

```bash
mkdir -p /home/runner/.kube
```

Copy the Kubernetes configuration:

```bash
cp /etc/kubernetes/admin.conf /home/runner/.kube/config
```

Change ownership:

```bash
chown -R runner:runner /home/runner/.kube
```

Secure the file:

```bash
chmod 600 /home/runner/.kube/config
```

The resulting structure was:

```text
/home/runner/
    |
    +-- actions-runner/
    |
    +-- .kube/
          |
          +-- config
```

Now the runner user could execute:

```bash
kubectl get nodes
```

against the Killercoda cluster.

---

# 17. Verify Kubernetes Access

Switch to the runner user:

```bash
su - runner
```

Run:

```bash
kubectl get nodes
```

Expected:

```text
NAME           STATUS   ROLES
controlplane   Ready    control-plane
node01         Ready    <none>
```

Also verify the application namespace:

```bash
kubectl get pods -n kafka-demo
```

---

# 18. Final CD Flow

Once Kubernetes access was fixed:

```text
GitHub
   |
   v
CI
   |
   +-- Test Order Service
   |
   +-- Test Notification Service
   |
   v
Build Docker Images
   |
   v
Push Images to GHCR
   |
   v
CD Job
   |
   v
Self-Hosted Runner
   |
   v
kubectl
   |
   v
Killercoda Kubernetes
   |
   +-- Kafka
   +-- Order Service
   +-- Notification Service
```

---

# 19. Commands Used

### Create runner user

```bash
useradd -m runner
```

### Change ownership

```bash
chown -R runner:runner /home/runner/actions-runner
```

### Switch user

```bash
su - runner
```

### Start runner

```bash
cd /home/runner/actions-runner
./run.sh
```

### Check Kubernetes configuration

```bash
ls -l /etc/kubernetes/admin.conf
```

### Create kubeconfig directory

```bash
mkdir -p /home/runner/.kube
```

### Copy kubeconfig

```bash
cp /etc/kubernetes/admin.conf /home/runner/.kube/config
```

### Change ownership

```bash
chown -R runner:runner /home/runner/.kube
```

### Secure kubeconfig

```bash
chmod 600 /home/runner/.kube/config
```

### Test cluster

```bash
kubectl get nodes
```

### Test application namespace

```bash
kubectl get pods -n kafka-demo
```

---

# 20. Production vs Killercoda

This setup was intentionally simple because Killercoda was being used as a temporary learning environment.

In production, you would normally consider:

- Dedicated runner VM
- Runner service using systemd
- Kubernetes RBAC instead of broad admin credentials
- Restricted network access
- Runner groups
- Runner labels
- Secret management
- Monitoring
- Runner lifecycle management
- Ephemeral runners for stronger isolation

GitHub supports configuring the runner as a system service so that it automatically starts when the machine starts.

Example:

```bash
sudo ./svc.sh install
sudo ./svc.sh start
```

Reference:

https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/configure-the-application

---

# 21. Important Security Warning

Self-hosted runners execute workflow code on machines that we control.

Therefore, workflows must be trusted.

GitHub recommends particular caution with public repositories because workflows originating from forks can potentially execute dangerous code on a self-hosted runner.

For production:

```text
Do NOT blindly allow
untrusted PRs
        |
        v
self-hosted runner
        |
        v
private infrastructure
```

Always control which workflows and repositories can use the runner.

---

# 22. Quick Mental Model

Remember these three things:

### Registration token

```text
Temporary credential
        |
        v
Registers the machine as a runner
```

### Runner

```text
Program running on our machine
        |
        v
Receives GitHub Actions jobs
        |
        v
Executes workflow commands
```

### Kubeconfig

```text
Configuration/credentials for kubectl
        |
        v
Allows runner user to communicate
with Kubernetes
```

Together:

```text
Registration Token
       |
       v
GitHub knows about Runner
       |
       v
Runner receives CD Job
       |
       v
Runner has kubeconfig
       |
       v
kubectl
       |
       v
Kubernetes
```

---

# 23. References

- GitHub self-hosted runners:
  https://docs.github.com/en/actions/concepts/runners/self-hosted-runners

- Add a self-hosted runner:
  https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/add-runners

- Self-hosted runner reference:
  https://docs.github.com/en/actions/reference/runners/self-hosted-runners

- Runner registration API:
  https://docs.github.com/en/rest/actions/self-hosted-runners

- Configure runner as a service:
  https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/configure-the-application

- Kubernetes kubeconfig:
  https://kubernetes.io/docs/reference/kubectl/generated/kubectl_config/
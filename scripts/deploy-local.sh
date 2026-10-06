#!/usr/bin/env bash

set -euo pipefail

NAMESPACE="kafka-demo"

echo "========================================="
echo " Starting local CD deployment"
echo "========================================="

# --------------------------------------------------
# 1. Check Kubernetes context
# --------------------------------------------------

echo ""
echo "Checking Kubernetes context..."

CURRENT_CONTEXT="$(kubectl config current-context)"

if [[ "$CURRENT_CONTEXT" != "docker-desktop" ]]; then
    echo "ERROR: Current Kubernetes context is not docker-desktop."
    echo "Current context: $CURRENT_CONTEXT"
    echo ""
    echo "Run:"
    echo "  kubectl config use-context docker-desktop"
    exit 1
fi

echo "Kubernetes context: $CURRENT_CONTEXT"

# --------------------------------------------------
# 2. Read version
# --------------------------------------------------

echo ""
echo "Reading VERSION file..."

VERSION="$(tr -d '[:space:]' < VERSION)"

if [[ ! "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "ERROR: Invalid version: $VERSION"
    echo "Expected format: v1.0.0"
    exit 1
fi

export IMAGE_VERSION="$VERSION"

echo "Application version: $IMAGE_VERSION"

# --------------------------------------------------
# 3. Check envsubst
# --------------------------------------------------

if ! command -v envsubst >/dev/null 2>&1; then
    echo ""
    echo "ERROR: envsubst is not installed."
    echo ""
    echo "On macOS, install it with:"
    echo "  brew install gettext"
    echo ""
    echo "Then run the script again."
    exit 1
fi

# --------------------------------------------------
# 4. Infrastructure
# --------------------------------------------------

echo ""
echo "========================================="
echo " Applying infrastructure"
echo "========================================="

echo ""
echo "Applying namespace..."

kubectl apply -f k8s/infra/namespace.yaml

echo ""
echo "Applying Kafka..."

kubectl apply -f k8s/infra/kafka.yaml

# --------------------------------------------------
# 5. Applications
# --------------------------------------------------

echo ""
echo "========================================="
echo " Deploying applications"
echo "========================================="

echo ""
echo "Deploying Order Service..."

envsubst < k8s/apps/order-service.yaml | kubectl apply -f -

echo ""
echo "Deploying Notification Service..."

envsubst < k8s/apps/notification-service.yaml | kubectl apply -f -

# --------------------------------------------------
# 6. Wait for rollout
# --------------------------------------------------

echo ""
echo "========================================="
echo " Waiting for rollouts"
echo "========================================="

echo ""
echo "Waiting for Kafka..."

kubectl rollout status deployment/kafka \
    -n "$NAMESPACE" \
    --timeout=180s

echo ""
echo "Waiting for Order Service..."

kubectl rollout status deployment/order-service \
    -n "$NAMESPACE" \
    --timeout=180s

echo ""
echo "Waiting for Notification Service..."

kubectl rollout status deployment/notification-service \
    -n "$NAMESPACE" \
    --timeout=180s

# --------------------------------------------------
# 7. Final status
# --------------------------------------------------

echo ""
echo "========================================="
echo " Deployment successful!"
echo "========================================="

echo ""
echo "Application version:"
echo "  $IMAGE_VERSION"

echo ""
echo "Images deployed:"

kubectl get deployment order-service \
    -n "$NAMESPACE" \
    -o jsonpath='{.spec.template.spec.containers[0].image}'

echo ""

kubectl get deployment notification-service \
    -n "$NAMESPACE" \
    -o jsonpath='{.spec.template.spec.containers[0].image}'

echo ""

echo ""
echo "Current Kubernetes resources:"

kubectl get deployment,service,pods \
    -n "$NAMESPACE"


                    __
        ____   __  / / _____  ____
       / __ \ / / / / / ___/ / __ \
      / /_/ // /_/ / / /    / /_/ /
     / .___/ \__, / /_/     \____/
    /_/     /____/
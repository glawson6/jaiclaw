#!/usr/bin/env bash
# push.sh — Build Docker image and push to private registry
# Run from the JaiClaw repo root on your dev machine
# Usage: ./deploy/ssdnodes/push.sh [version]
set -euo pipefail

REGISTRY="tooling.taptech.net:8082"
IMAGE_PREFIX="io.jaiclaw"
VERSION="${1:-0.1.0-SNAPSHOT}"

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

export JAVA_HOME="${JAVA_HOME:-/Users/tap/.sdkman/candidates/java/21.0.9-oracle}"

echo "==> Building Docker image with JKube"
./mvnw package k8s:build \
    -pl :jaiclaw-gateway-app -am \
    -Pk8s -DskipTests

GATEWAY_IMAGE="${IMAGE_PREFIX}/jaiclaw-gateway-app:${VERSION}"

echo "==> Tagging for registry"
docker tag "${GATEWAY_IMAGE}" "${REGISTRY}/${GATEWAY_IMAGE}"

echo "==> Pushing to ${REGISTRY}"
docker push "${REGISTRY}/${GATEWAY_IMAGE}"

echo ""
echo "==> Pushed ${REGISTRY}/${GATEWAY_IMAGE}"
echo ""
echo "Deploy on VM with:"
echo "  ssh jaiclaw@<vm-ip> '/opt/jaiclaw/deploy.sh ${VERSION}'"
echo ""
echo "Or wait for Watchtower to pick it up (~5 min)."

#!/usr/bin/env bash
# deploy.sh — Pull latest images and restart on the VM
# Usage: ./deploy.sh [version]
set -euo pipefail

REGISTRY="tooling.taptech.net:8082"
IMAGE_PREFIX="io.jaiclaw"
VERSION="${1:-0.1.0-SNAPSHOT}"
DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"

cd "${DEPLOY_DIR}"

echo "==> Pulling gateway image (${VERSION})"
docker pull "${REGISTRY}/${IMAGE_PREFIX}/jaiclaw-gateway-app:${VERSION}"

echo "==> Restarting gateway"
docker compose up -d gateway

echo "==> Waiting for health check..."
for i in $(seq 1 30); do
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo "==> Gateway is healthy"
        docker compose ps
        exit 0
    fi
    sleep 2
done

echo "==> WARNING: Gateway did not become healthy within 60s"
echo "==> Recent logs:"
docker compose logs --tail=50 gateway
exit 1

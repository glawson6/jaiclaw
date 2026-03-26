#!/usr/bin/env bash
# status.sh — Quick health check of all JaiClaw services
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${DEPLOY_DIR}"

echo "=== Container Status ==="
docker compose ps
echo ""

echo "=== Gateway Health ==="
if curl -sf http://localhost:8080/actuator/health 2>/dev/null | jq . 2>/dev/null; then
    echo ""
else
    echo "  UNREACHABLE"
    echo ""
fi

echo "=== Caddy Status ==="
systemctl is-active caddy 2>/dev/null && echo "  Running" || echo "  Not running"
echo ""

echo "=== Disk Usage ==="
echo "  Docker:     $(docker system df --format '{{.TotalCount}} images, {{.Size}}' 2>/dev/null | head -1)"
echo "  Data dir:   $(du -sh "${DEPLOY_DIR}/data" 2>/dev/null | cut -f1)"
echo "  Backups:    $(du -sh "${DEPLOY_DIR}/backups" 2>/dev/null | cut -f1)"
echo ""

echo "=== Memory ==="
free -h | head -2
echo ""

echo "=== Recent Gateway Logs (last 10 lines) ==="
docker compose logs --tail=10 gateway 2>/dev/null || echo "  No gateway container"

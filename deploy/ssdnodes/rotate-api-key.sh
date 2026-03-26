#!/usr/bin/env bash
# rotate-api-key.sh — Generate a new JaiClaw API key and restart gateway
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${DEPLOY_DIR}/.env"

if [ ! -f "${ENV_FILE}" ]; then
    echo "ERROR: ${ENV_FILE} not found"
    exit 1
fi

NEW_KEY="jaiclaw_ak_$(openssl rand -hex 16)"

sed -i "s/^JAICLAW_API_KEY=.*/JAICLAW_API_KEY=${NEW_KEY}/" "${ENV_FILE}"

echo "==> API key rotated"
echo "==> New key: ${NEW_KEY}"
echo ""

read -p "Restart gateway now? [y/N] " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    cd "${DEPLOY_DIR}"
    docker compose up -d gateway
    echo "==> Gateway restarting with new key"
fi

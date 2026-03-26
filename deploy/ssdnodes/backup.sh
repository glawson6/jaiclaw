#!/usr/bin/env bash
# backup.sh — Backup secrets and persistent data
# Runs daily via cron (installed by vm-setup.sh)
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKUP_DIR="${DEPLOY_DIR}/backups"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/jaiclaw-backup-${TIMESTAMP}.tar.gz"

mkdir -p "${BACKUP_DIR}"

tar -czf "${BACKUP_FILE}" \
    -C "${DEPLOY_DIR}" \
    .env \
    data/cron-manager-db/ \
    2>/dev/null || true

# Keep last 7 backups
ls -t "${BACKUP_DIR}"/jaiclaw-backup-*.tar.gz 2>/dev/null | tail -n +8 | xargs -r rm

echo "Backup: ${BACKUP_FILE} ($(du -h "${BACKUP_FILE}" | cut -f1))"

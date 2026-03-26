#!/usr/bin/env bash
# vm-setup.sh — Run once on a fresh SSDNodes Ubuntu VM as root
# Usage: ssh root@<vm-ip> 'bash -s' < deploy/ssdnodes/vm-setup.sh
set -euo pipefail

DEPLOY_USER="jaiclaw"
DEPLOY_DIR="/opt/jaiclaw"

echo "==> Updating system"
apt update && apt upgrade -y

echo "==> Installing essentials"
apt install -y curl wget ufw fail2ban ca-certificates gnupg lsb-release jq

# ── Docker ─────────────────────────────────────────────────────
echo "==> Installing Docker"
if ! command -v docker &>/dev/null; then
    curl -fsSL https://get.docker.com | sh
fi
systemctl enable docker
systemctl start docker

# ── Caddy ──────────────────────────────────────────────────────
echo "==> Installing Caddy"
if ! command -v caddy &>/dev/null; then
    apt install -y debian-keyring debian-archive-keyring apt-transport-https
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | \
        gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | \
        tee /etc/apt/sources.list.d/caddy-stable.list
    apt update && apt install -y caddy
fi

# ── Firewall ───────────────────────────────────────────────────
echo "==> Configuring firewall"
ufw default deny incoming
ufw default allow outgoing
ufw allow ssh
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

# ── Deploy user ────────────────────────────────────────────────
echo "==> Creating deploy user: ${DEPLOY_USER}"
if ! id "${DEPLOY_USER}" &>/dev/null; then
    adduser "${DEPLOY_USER}" --disabled-password --gecos ""
fi
usermod -aG docker "${DEPLOY_USER}"

# ── Deployment directory ───────────────────────────────────────
echo "==> Creating ${DEPLOY_DIR}"
mkdir -p "${DEPLOY_DIR}/data/cron-manager-db"
mkdir -p "${DEPLOY_DIR}/data/ollama"
mkdir -p "${DEPLOY_DIR}/backups"
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${DEPLOY_DIR}"
chmod 700 "${DEPLOY_DIR}"

# ── Caddy log directory ───────────────────────────────────────
mkdir -p /var/log/caddy
chown caddy:caddy /var/log/caddy

# ── Systemd service ───────────────────────────────────────────
echo "==> Installing systemd service"
cat > /etc/systemd/system/jaiclaw.service <<'EOF'
[Unit]
Description=JaiClaw Gateway
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
User=jaiclaw
WorkingDirectory=/opt/jaiclaw
ExecStart=/usr/bin/docker compose up -d gateway watchtower
ExecStop=/usr/bin/docker compose down
TimeoutStartSec=120

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable jaiclaw

# ── Daily backup cron ─────────────────────────────────────────
echo "==> Installing daily backup cron"
(crontab -u "${DEPLOY_USER}" -l 2>/dev/null || true; echo "0 3 * * * /opt/jaiclaw/backup.sh") | sort -u | crontab -u "${DEPLOY_USER}" -

echo ""
echo "========================================="
echo " VM setup complete."
echo ""
echo " Next steps:"
echo "   1. ssh ${DEPLOY_USER}@<vm-ip>"
echo "   2. docker login tooling.taptech.net:8082"
echo "   3. Copy files to ${DEPLOY_DIR}/:"
echo "      scp deploy/ssdnodes/{docker-compose.yml,Caddyfile,.env.template,deploy.sh,backup.sh} ${DEPLOY_USER}@<vm-ip>:${DEPLOY_DIR}/"
echo "   4. cp .env.template .env && vim .env  (fill in secrets)"
echo "   5. chmod 600 .env && chmod +x deploy.sh backup.sh"
echo "   6. sudo cp Caddyfile /etc/caddy/Caddyfile && sudo systemctl reload caddy"
echo "   7. docker compose up -d gateway watchtower"
echo "========================================="

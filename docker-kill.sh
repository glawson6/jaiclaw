#!/usr/bin/env bash
#
# docker-kill.sh — list and kill Docker containers
#
# Usage:
#   ./docker-kill.sh              # list all containers, confirm, then kill
#   ./docker-kill.sh <id>         # kill a specific container by ID or name
#   ./docker-kill.sh --force      # kill all with SIGKILL (-9)
#   ./docker-kill.sh --force <id> # kill a specific container with SIGKILL (-9)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/scripts/common.sh"

# ─── Parse args ──────────────────────────────────────────────────────────────

FORCE=false
TARGET=""

for arg in "$@"; do
    case "$arg" in
        --force)       FORCE=true ;;
        -h|--help|help)
            echo "Usage: ./docker-kill.sh [--force] [<container-id-or-name>]"
            echo ""
            echo "Options:"
            echo "  --force    Use SIGKILL (-9) instead of SIGTERM"
            echo ""
            echo "Examples:"
            echo "  ./docker-kill.sh              # list all, confirm, kill all"
            echo "  ./docker-kill.sh abc123       # kill container abc123"
            echo "  ./docker-kill.sh --force      # SIGKILL all"
            echo "  ./docker-kill.sh --force abc  # SIGKILL container abc"
            exit 0
            ;;
        *)             TARGET="$arg" ;;
    esac
done

SIGNAL="SIGTERM"
[ "$FORCE" = true ] && SIGNAL="SIGKILL"

# ─── Docker check ───────────────────────────────────────────────────────────

if ! command -v docker &>/dev/null; then
    err "Docker is not installed."
    exit 1
fi
if ! docker info &>/dev/null; then
    err "Docker daemon is not running."
    exit 1
fi

# ─── Kill specific container ────────────────────────────────────────────────

if [ -n "$TARGET" ]; then
    if ! docker inspect "$TARGET" &>/dev/null; then
        err "Container not found: $TARGET"
        exit 1
    fi
    info "Killing container $TARGET ($SIGNAL)..."
    docker kill -s "$SIGNAL" "$TARGET"
    ok "Killed: $TARGET"
    exit 0
fi

# ─── Kill all containers ────────────────────────────────────────────────────

COUNT=$(docker ps -q 2>/dev/null | wc -l | tr -d ' ')

if [ "$COUNT" -eq 0 ]; then
    ok "No running Docker containers."
    exit 0
fi

echo ""
docker ps --format "table {{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}"
echo ""
warn "$COUNT running container(s) will be killed ($SIGNAL)."
read -rp "$(printf "${CYAN}▸${NC} Kill all? (y/N): ")" confirm
if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
    info "Aborted."
    exit 0
fi

docker kill -s "$SIGNAL" $(docker ps -q)
ok "All containers killed."

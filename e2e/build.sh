#!/usr/bin/env bash
#
# Build the JaiClaw E2E test runner Docker image.
#
# Handles the .dockerignore conflict: the project's .dockerignore is tuned for
# the CLI image (excludes .mvn, mvnw, tools/, etc.), but the e2e image needs
# the full source tree. This script swaps .dockerignore during the build.
#
# Usage:
#   ./e2e/build.sh                    # build with auto-detected version
#   ./e2e/build.sh --no-cache         # build without Docker cache
#
set -euo pipefail

# ─── Resolve project root ────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# ─── Resolve version ─────────────────────────────────────────────────────────

if [[ -n "${JAICLAW_VERSION:-}" ]]; then
    VERSION="$JAICLAW_VERSION"
else
    VERSION=$(grep '^JAICLAW_VERSION=' bin/jaiclaw 2>/dev/null | head -1 | cut -d'"' -f2)
    if [[ -z "$VERSION" ]]; then
        VERSION=$(grep '<version>' pom.xml | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
    fi
fi

IMAGE="io.jaiclaw/jaiclaw-e2e-runner:${VERSION}"
echo "Building: $IMAGE"

# ─── Swap .dockerignore ──────────────────────────────────────────────────────

DOCKERIGNORE="$PROJECT_ROOT/.dockerignore"
BACKUP=""

restore_dockerignore() {
    if [[ -n "$BACKUP" && -f "$BACKUP" ]]; then
        mv "$BACKUP" "$DOCKERIGNORE"
        echo "Restored .dockerignore"
    fi
}

trap restore_dockerignore EXIT

if [[ -f "$DOCKERIGNORE" ]]; then
    BACKUP="${DOCKERIGNORE}.e2e-backup"
    cp "$DOCKERIGNORE" "$BACKUP"
    echo "Backed up .dockerignore -> .dockerignore.e2e-backup"
fi

# Write minimal .dockerignore for e2e image (needs full source tree)
cat > "$DOCKERIGNORE" <<'EOF'
.git
.idea
.vscode
.claude
**/node_modules
**/.DS_Store
EOF

echo "Wrote e2e .dockerignore"

# ─── Build ────────────────────────────────────────────────────────────────────

DOCKER_ARGS=("$@")

docker build \
    -f e2e/Dockerfile \
    -t "$IMAGE" \
    "${DOCKER_ARGS[@]}" \
    .

echo ""
echo "Built: $IMAGE"
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" | head -1
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}" | grep jaiclaw-e2e-runner || true

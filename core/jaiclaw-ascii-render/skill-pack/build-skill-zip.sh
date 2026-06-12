#!/usr/bin/env bash
# Package the ascii-rendering skill pack as a single zip suitable for
# uploading to claude.ai/skills or Claude Desktop. Run from this
# directory; output lands at ./dist/ascii-rendering-skill-<version>.zip.
#
# Usage:
#   ./build-skill-zip.sh              # version from skill-pack/VERSION (or 0.1.0)
#   VERSION=0.2.0 ./build-skill-zip.sh

set -euo pipefail

cd "$(dirname "$0")"

VERSION="${VERSION:-$(cat VERSION 2>/dev/null || echo 0.1.0)}"
OUT_DIR="dist"
STAGE="$OUT_DIR/ascii-rendering"
ZIP="$OUT_DIR/ascii-rendering-skill-$VERSION.zip"

rm -rf "$OUT_DIR"
mkdir -p "$STAGE/scripts" "$STAGE/examples"

cp SKILL.md "$STAGE/"
cp scripts/RenderScene.java "$STAGE/scripts/"
cp scripts/RenderBox.java   "$STAGE/scripts/"
cp examples/*.json           "$STAGE/examples/"

# Ensure scripts are executable when extracted
chmod +x "$STAGE/scripts"/*.java

(cd "$OUT_DIR" && zip -r "ascii-rendering-skill-$VERSION.zip" ascii-rendering >/dev/null)

echo "Built: $(pwd)/$ZIP"
echo "Contents:"
unzip -l "$ZIP" | sed 's/^/  /'

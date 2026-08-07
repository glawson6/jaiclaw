#!/usr/bin/env bash
#
# Regenerate ASCII diagrams from JSON scene specs.
# Runs the JaiClaw ASCII renderer via JBang, writes .txt siblings for
# inline inclusion in mcp-design-patterns.md.
#
# Usage: ./render.sh                    # regenerate all
#        ./render.sh direct-api-wrapper # regenerate one

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RENDER_JBANG="$SCRIPT_DIR/../../../../core/jaiclaw-ascii-render/skill-pack/plugins/ascii-rendering/skills/ascii-rendering/scripts/RenderScene.java"

if ! command -v jbang &>/dev/null; then
    echo "ERROR: jbang not on PATH. Install from https://www.jbang.dev/download/"
    exit 1
fi

if [ ! -f "$RENDER_JBANG" ]; then
    echo "ERROR: RenderScene.java not found at expected path:"
    echo "  $RENDER_JBANG"
    exit 1
fi

# Filter to a single spec if the caller asked for one; otherwise all *.json
if [ $# -gt 0 ]; then
    specs=("$SCRIPT_DIR/$1.json")
    if [ ! -f "${specs[0]}" ]; then
        echo "ERROR: no such spec: ${specs[0]}"
        exit 1
    fi
else
    specs=("$SCRIPT_DIR"/*.json)
fi

for spec in "${specs[@]}"; do
    name=$(basename "$spec" .json)
    echo "rendering $name..."
    jbang "$RENDER_JBANG" --file "$spec" > "$SCRIPT_DIR/$name.txt"
done

echo "done"

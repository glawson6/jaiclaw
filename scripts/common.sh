#!/usr/bin/env bash
#
# JClaw shared helpers — sourced by start.sh, quickstart.sh, setup.sh
#
# Provides:
#   Colors (RED, GREEN, YELLOW, CYAN, DIM, BOLD, NC)
#   Logging (info, ok, warn, err, header)
#   resolve_api_key    — mirrors ApiKeyProvider logic; sets RESOLVED_API_KEY
#   print_api_curl_example <port>  — prints a curl snippet with the resolved key
#   print_api_httpie_example <port> — prints an httpie snippet with the resolved key
#   print_security_info            — prints current security mode + key info
#

# ─── Colors ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
DIM='\033[2m'
BOLD='\033[1m'
NC='\033[0m'

# ─── Logging ──────────────────────────────────────────────────────────────────
info()   { printf "${CYAN}▸${NC} %s\n" "$*"; }
ok()     { printf "${GREEN}✓${NC} %s\n" "$*"; }
warn()   { printf "${YELLOW}!${NC} %s\n" "$*"; }
err()    { printf "${RED}✗${NC} %s\n" "$*" >&2; }
header() { printf "\n${BOLD}${CYAN}── %s ──${NC}\n\n" "$*"; }

# ─── API Key Resolution ──────────────────────────────────────────────────────
#
# Mirrors the Java ApiKeyProvider lookup order:
#   1. JCLAW_API_KEY env var
#   2. Key file (JCLAW_API_KEY_FILE or ~/.jclaw/api-key)
#   3. Generate + write to key file
#
# After this function returns, RESOLVED_API_KEY is set.
#
resolve_api_key() {
    local key_file="${JCLAW_API_KEY_FILE:-$HOME/.jclaw/api-key}"

    # 1. Explicit env var
    if [ -n "${JCLAW_API_KEY:-}" ]; then
        RESOLVED_API_KEY="$JCLAW_API_KEY"
        return
    fi

    # 2. Read from file
    if [ -f "$key_file" ]; then
        RESOLVED_API_KEY="$(tr -d '[:space:]' < "$key_file")"
        if [ -n "$RESOLVED_API_KEY" ]; then
            return
        fi
    fi

    # 3. Generate + write
    RESOLVED_API_KEY="jclaw_ak_$(openssl rand -hex 16)"
    mkdir -p "$(dirname "$key_file")"
    printf '%s' "$RESOLVED_API_KEY" > "$key_file"
    chmod 600 "$key_file"
}

# ─── Curl Example ─────────────────────────────────────────────────────────────
#
# Prints a ready-to-paste curl command with the API key header.
# Usage: print_api_curl_example [port]
#
print_api_curl_example() {
    local port="${1:-8080}"
    local key="${RESOLVED_API_KEY:-<your-api-key>}"
    printf "  ${BOLD}curl -X POST http://localhost:${port}/api/chat \\\\${NC}\n"
    printf "  ${BOLD}  -H \"Content-Type: application/json\" \\\\${NC}\n"
    printf "  ${BOLD}  -H \"X-API-Key: ${key}\" \\\\${NC}\n"
    printf "  ${BOLD}  -d '{\"content\": \"hello\"}'${NC}\n"
}

# ─── HTTPie Example ──────────────────────────────────────────────────────────
#
# Prints a ready-to-paste httpie command with the API key header.
# Usage: print_api_httpie_example [port]
#
print_api_httpie_example() {
    local port="${1:-8080}"
    local key="${RESOLVED_API_KEY:-<your-api-key>}"
    printf "  ${BOLD}http POST http://localhost:${port}/api/chat \\\\${NC}\n"
    printf "  ${BOLD}  X-API-Key:${key} \\\\${NC}\n"
    printf "  ${BOLD}  content=hello${NC}\n"
}

# ─── Security Info ────────────────────────────────────────────────────────────
#
# Prints the active security mode and key (if applicable).
#
print_security_info() {
    local mode="${JCLAW_SECURITY_MODE:-api-key}"
    case "$mode" in
        api-key)
            ok "Security: API key mode"
            info "API Key: ${RESOLVED_API_KEY:-<not resolved>}"
            ;;
        jwt)
            ok "Security: JWT mode"
            ;;
        none)
            warn "Security: DISABLED (mode=none)"
            ;;
    esac
}

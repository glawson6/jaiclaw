#!/usr/bin/env bash
#
# op-upsert-maven-central.sh — upsert the Maven-Central item in the
# TapTech-Security 1Password vault.
#
# Holds the four secrets that .github/workflows/publish-central.yml
# needs to publish to Sonatype Central Portal:
#
#   - username        (Sonatype Central Portal token user)
#   - password        (Sonatype Central Portal token password)
#   - gpg-private-key (ASCII-armored PGP private key block)
#   - gpg-passphrase  (passphrase for the GPG key)
#
# Idempotent: detects whether the item exists and dispatches to
# `op item create` (insert) or `op item edit` (update). Either way,
# every field is set to the value you pass in.
#
# Usage:
#   1Password CLI must be installed and you must be signed in:
#       op signin     # interactive
#       # or: export OP_SERVICE_ACCOUNT_TOKEN=...
#
#   Provide the four secret values via env vars OR be prompted:
#       MAVEN_CENTRAL_USERNAME='ops-user'           \
#       MAVEN_CENTRAL_PASSWORD='ops-pass'           \
#       GPG_PRIVATE_KEY="$(cat key.asc)"            \
#       GPG_PASSPHRASE='passphrase'                 \
#         ./scripts/op-upsert-maven-central.sh
#
#   Or run interactively — the script prompts for any unset values
#   with `read -s` so they don't echo.
#
# Optional env vars:
#   OP_VAULT  — vault name (default: TapTech-Security)
#   OP_ITEM   — item title (default: Maven-Central)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./common.sh
source "$SCRIPT_DIR/common.sh"

OP_VAULT="${OP_VAULT:-TapTech-Security}"
OP_ITEM="${OP_ITEM:-Maven-Central}"

# ─── Preflight ────────────────────────────────────────────────────────────────

if ! command -v op >/dev/null 2>&1; then
    err "1Password CLI ('op') not found in PATH."
    echo "Install: https://developer.1password.com/docs/cli/get-started/"
    exit 1
fi

# Verify the session can list the target vault. This catches both
# "not signed in" and "service account doesn't have access to vault".
if ! op vault get "$OP_VAULT" >/dev/null 2>&1; then
    err "Cannot read vault '$OP_VAULT'."
    echo "If running interactively: run 'op signin' first."
    echo "If using a service account: ensure OP_SERVICE_ACCOUNT_TOKEN is set"
    echo "and the account has read access to '$OP_VAULT'."
    exit 1
fi

# ─── Collect secret values ────────────────────────────────────────────────────

prompt_secret() {
    local var_name="$1"
    local prompt_label="$2"
    local current_value="${!var_name:-}"
    if [[ -n "$current_value" ]]; then
        return 0
    fi
    printf "  %s: " "$prompt_label" >&2
    read -rs current_value
    echo >&2
    printf -v "$var_name" '%s' "$current_value"
}

prompt_multiline_secret() {
    local var_name="$1"
    local prompt_label="$2"
    local current_value="${!var_name:-}"
    if [[ -n "$current_value" ]]; then
        return 0
    fi
    echo "  $prompt_label" >&2
    echo "  (paste the full block including BEGIN/END lines, then Ctrl-D on a blank line)" >&2
    current_value="$(cat)"
    printf -v "$var_name" '%s' "$current_value"
}

header "Maven-Central credentials for $OP_VAULT"

prompt_secret           MAVEN_CENTRAL_USERNAME 'Sonatype Central Portal token user'
prompt_secret           MAVEN_CENTRAL_PASSWORD 'Sonatype Central Portal token password'
prompt_multiline_secret GPG_PRIVATE_KEY        'GPG ASCII-armored private key block'
prompt_secret           GPG_PASSPHRASE         'GPG key passphrase'

if [[ -z "$MAVEN_CENTRAL_USERNAME" || -z "$MAVEN_CENTRAL_PASSWORD" \
   || -z "$GPG_PRIVATE_KEY"        || -z "$GPG_PASSPHRASE" ]]; then
    err "All four values are required. Aborting."
    exit 1
fi

# ─── Upsert ───────────────────────────────────────────────────────────────────
#
# `op` accepts field assignments of the form 'label[type]=value'. We
# use the same assignment list for both create and edit so the
# semantics match exactly. CONCEALED is the right type for every field
# here — the standard 'password' field is also CONCEALED.

build_assignments() {
    # Print one assignment per arg on a separate line. The caller
    # captures these into an array via mapfile so values containing
    # newlines (the GPG key) survive intact.
    printf '%s\n'                                       \
        "username=$MAVEN_CENTRAL_USERNAME"              \
        "password[password]=$MAVEN_CENTRAL_PASSWORD"    \
        "gpg-private-key[password]=$GPG_PRIVATE_KEY"    \
        "gpg-passphrase[password]=$GPG_PASSPHRASE"
}

mapfile -t ASSIGNMENTS < <(build_assignments)

if op item get "$OP_ITEM" --vault "$OP_VAULT" >/dev/null 2>&1; then
    info "Item '$OP_ITEM' exists in '$OP_VAULT' — updating fields"
    op item edit "$OP_ITEM" --vault "$OP_VAULT" "${ASSIGNMENTS[@]}" >/dev/null
    ok "Updated."
else
    info "Item '$OP_ITEM' not found in '$OP_VAULT' — creating"
    op item create                          \
        --category=login                    \
        --vault="$OP_VAULT"                 \
        --title="$OP_ITEM"                  \
        "${ASSIGNMENTS[@]}"                 >/dev/null
    ok "Created."
fi

# ─── Verify ───────────────────────────────────────────────────────────────────

header "Verification"

# Print field names (NOT values) so the operator can confirm the
# expected structure was created. The op:// references below are
# what the publish-central.yml workflow will use.
info "Field structure (labels only — no values printed):"
op item get "$OP_ITEM" --vault "$OP_VAULT" --format json \
    | jq -r '.fields[] | "    \(.label) (\(.type))"'

echo
ok "GitHub Actions can now read via:"
echo "  op://$OP_VAULT/$OP_ITEM/username"
echo "  op://$OP_VAULT/$OP_ITEM/password"
echo "  op://$OP_VAULT/$OP_ITEM/gpg-private-key"
echo "  op://$OP_VAULT/$OP_ITEM/gpg-passphrase"

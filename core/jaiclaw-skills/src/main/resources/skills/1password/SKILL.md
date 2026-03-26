---
name: 1password
description: "1Password CLI (op) for reading/injecting secrets, managing vaults, and secure credential access. Use when asked to look up passwords, retrieve secrets, or manage 1Password items."
alwaysInclude: false
requiredBins: [op]
platforms: [darwin, linux]
---

# 1Password CLI

Use the `op` CLI to securely read and inject secrets from 1Password.

## Install

```bash
brew install 1password-cli
```

## Setup

1. Verify CLI: `op --version`
2. Enable desktop app integration (Settings > Developer > CLI integration)
3. Sign in: `op signin`
4. Verify: `op whoami`

## Security Rules

- Never paste secrets into logs, chat, or code
- Prefer `op run` / `op inject` over writing secrets to disk
- All `op` commands should run inside a tmux session to maintain auth state

## Common Commands

### Read Secrets

```bash
# Get a specific field
op item get "Service Name" --fields password

# Get username and password
op item get "Service Name" --fields username,password

# Get by vault
op item get "Service Name" --vault "Work" --fields password

# Secret reference syntax
op read "op://VaultName/ItemName/FieldName"
```

### List Items

```bash
# List vaults
op vault list

# List items in a vault
op item list --vault "Personal"

# Search items
op item list --vault "Personal" | grep -i "service"
```

### Inject Secrets

```bash
# Run a command with injected secrets
op run --env-file=.env.tpl -- ./my-script.sh

# Inject into a template file
op inject -i config.tpl -o config.yml
```

### Run with Environment Variables

```bash
# .env.tpl format:
# DATABASE_URL=op://Vault/Database/url
# API_KEY=op://Vault/Service/api-key

op run --env-file=.env.tpl -- java -jar app.jar
```

## tmux Session Pattern

Since `op` requires a persistent auth session:

```bash
# Create dedicated session
tmux new-session -d -s op-session

# Sign in within session
tmux send-keys -t op-session "op signin" Enter

# Run commands in session
tmux send-keys -t op-session "op vault list" Enter

# Capture output
tmux capture-pane -t op-session -p
```

## Notes

- If sign-in fails, re-run `op signin` and authorize in the 1Password app
- Use `op account add` for multiple accounts
- Use `--account` flag or `OP_ACCOUNT` env var to specify account
- Desktop app must be unlocked for CLI integration to work

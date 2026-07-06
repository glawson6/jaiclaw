# CI Secrets — 1Password vault population

**Audience:** JaiClaw maintainers who need to seed or rotate the 1Password items that the GitHub Actions workflows read at CI time. External adopters who fork the repo can use this as a template for their own vault + Service Account.

**Context:** JaiClaw's GitHub Actions workflows read secrets exclusively via `1password/load-secrets-action@v2`. The only GitHub-native secret is `OP_SERVICE_ACCOUNT_TOKEN` — the bootstrap token that grants read access to the vault. Every other secret (Sonatype Central credentials, GPG signing key, LLM API keys) lives in 1Password as `op://<vault>/<item>/<field>` references pinned in each workflow file. Two operator-side scripts populate those vault items from the local `.env` files.

The scripts themselves are gitignored (they live under `op-vault/`) because they read from `maven-central-deploy/.env` and `gpg-key-base64.txt` — files that are also gitignored to keep credential-adjacent data off `origin`. This doc explains what the scripts do, the exact vault items they create, and how to run them.

---

## Vault layout

The workflows reference `op://TapTech-Security/<item>/<field>`. `TapTech-Security` is the vault name used by the maintainers of this repo; you can override it via the `OP_VAULT` environment variable when running any of the scripts, or fork the workflows and change the paths.

Two vault items are managed by `op-vault/`:

### `Maven-Central` — Sonatype Central + GPG signing

Referenced by `.github/workflows/publish-central.yml`.

| Field name in 1Password | Source (local) | CI env var |
|---|---|---|
| `username` | `CENTRAL_TOKEN_USERNAME` in `maven-central-deploy/.env` | `MAVEN_CENTRAL_USERNAME` |
| `password` | `CENTRAL_TOKEN_PASSWORD` in `maven-central-deploy/.env` | `MAVEN_CENTRAL_PASSWORD` |
| `gpg-private-key` | `base64 -d maven-central-deploy/gpg-key-base64.txt` | `GPG_PRIVATE_KEY` |
| `gpg-passphrase` | `GPG_PASSPHRASE` in `maven-central-deploy/.env` | `GPG_PASSPHRASE` |

### `MiniMax-Anthropic-API` — e2e test LLM

Referenced by `.github/workflows/e2e-tests.yml`. The e2e job runs a real LLM call against a MiniMax model served through the Anthropic-compatible endpoint, so we need three coordinated values.

| Field name in 1Password | CI env var |
|---|---|
| `anthropic-api-key` | `ANTHROPIC_API_KEY` |
| `anthropic-base-url` | `ANTHROPIC_BASE_URL` |
| `anthropic-model` | `ANTHROPIC_MODEL` |

Rotating any of the three is a plain `op item edit` — no CI-side change needed. The workflow reads the field via `op://` at run time.

---

## Bootstrap token: `OP_SERVICE_ACCOUNT_TOKEN`

This is the only GitHub-native secret. Rules:

- **Scope:** read-only, restricted to the `TapTech-Security` vault (or your equivalent).
- **Set once:** in the GitHub repo settings under `Secrets and variables → Actions → Repository secrets`.
- **Rotate manually** whenever a maintainer with vault access leaves. There's no automated rotation — 1Password Service Account tokens are long-lived by design.
- **Never leaked into logs.** `1password/load-secrets-action@v2` masks them; downstream steps see only the resolved values as env vars.

`GITHUB_TOKEN` is auto-issued per workflow run and requires no maintenance.

---

## Populating the vault — `op-vault/` scripts

Both scripts require the [1Password CLI](https://developer.1password.com/docs/cli/) (`op`) and an interactive signin (Service Accounts are read-only; writes need a human):

```bash
op signin
```

### General populator: `op-upsert-ci-secrets.sh`

Reads `maven-central-deploy/.env` + `gpg-key-base64.txt` and upserts every managed vault item in one pass. Idempotent — detects whether each item already exists, dispatches to `op item create` (insert) or `op item edit` (update); every field is reset to the current local values.

```bash
# all managed items
./op-vault/op-upsert-ci-secrets.sh

# scoped to one item — quicker rotations
./op-vault/op-upsert-ci-secrets.sh --only Maven-Central
./op-vault/op-upsert-ci-secrets.sh --only Maven-Central,MiniMax-Anthropic-API

# dry-run — reports what would be written without touching the vault
./op-vault/op-upsert-ci-secrets.sh --dry-run
```

Environment overrides:

| Variable | Default | Purpose |
|---|---|---|
| `OP_VAULT` | `TapTech-Security` | Target vault; override if you forked the repo and use a different vault |
| `DEPLOY_DIR` | `<script dir>/../maven-central-deploy` | Location of the `.env` + GPG key file |

### Legacy single-item populator: `op-upsert-maven-central.sh`

Superseded by the general script above; kept for one-shot Maven-Central rotations when you don't want to think about the general script's flags. Same idempotent create-or-edit semantics; same env overrides.

```bash
./op-vault/op-upsert-maven-central.sh
```

---

## Adding a new secret

Two-step flow:

1. **Provision the vault field.**
   - If the new secret belongs in an existing item (e.g. adding a second LLM key alongside `MiniMax-Anthropic-API`), edit the item mapping in `op-upsert-ci-secrets.sh` and re-run.
   - If it belongs in a new item, add a new item mapping in the script + a new section in this doc.
2. **Wire it into the workflow.** In the appropriate `.github/workflows/*.yml`, add the reference under the `env` block of the `load-secrets-action` step:
   ```yaml
   - uses: 1password/load-secrets-action@v2
     with:
       export-env: true
     env:
       OP_SERVICE_ACCOUNT_TOKEN: ${{ secrets.OP_SERVICE_ACCOUNT_TOKEN }}
       NEW_SECRET: op://TapTech-Security/New-Item/new-field
   ```
   Downstream steps then reference `${NEW_SECRET}` as a plain env var — never `${{ secrets.NEW_SECRET }}`.

---

## Safety rules

These rules apply whenever you touch the `op-vault/` tree or any of the referenced files:

- **Never `cat`, `echo`, `printf`, or log the contents of `.env`, `gpg-key-base64.txt`, or any 1Password field value.** Verify presence with `[ -n "$VAR" ]`, never with `echo "$VAR"`.
- **The `op-vault/` directory is gitignored.** So is `maven-central-deploy/`. Do not `git add -f` any file in either tree — the `.env` and GPG key file are what a leak would cost you.
- **Service Account token stays in GitHub repo secrets only.** Do not paste it into 1Password, your shell history, or a `.env` file. If you accidentally do, rotate it immediately at `https://developer.1password.com/docs/service-accounts/`.
- **Rotations are additive.** When rotating a credential, upsert the new value first, verify a CI run succeeds, then revoke the old credential upstream (Sonatype, MiniMax, etc.). Order matters — revoking first breaks CI until the vault catches up.

---

## Verification

After running `op-upsert-ci-secrets.sh`:

1. **Vault check.** `op item list --vault TapTech-Security` should show every managed item. Field-level inspection: `op item get Maven-Central --vault TapTech-Security --format json | jq '.fields[].label'`.
2. **Workflow dry-run.** Trigger the affected workflow via `gh workflow run <name>.yml` on a branch — the `load-secrets-action` step fails fast with a clear message if any `op://` reference doesn't resolve.
3. **Downstream check.** For `publish-central`, the `mvn deploy` step reports authentication success against `s01.oss.sonatype.org`. For `e2e-tests`, the first LLM call succeeds.

If a workflow fails with `1Password: field not found`, the mismatch is between the vault field name and the workflow `op://` reference. Reconcile by editing the vault item (via `op-upsert-ci-secrets.sh`) or the workflow — never both simultaneously.

---

## Forking / adapting for another organization

External adopters who fork JaiClaw and want the same CI pattern:

1. Create your own 1Password Service Account with read access to your chosen vault.
2. Set `OP_SERVICE_ACCOUNT_TOKEN` as a GitHub repo secret in your fork.
3. Rewrite the `op://TapTech-Security/<item>/<field>` paths in the workflow files to point at your vault.
4. Copy `op-vault/op-upsert-ci-secrets.sh` (from the maintainer branch or an archived copy — the file is gitignored, but the pattern is what matters) and adapt the item-and-field mapping to your credentials.
5. Populate the vault the same way: `OP_VAULT=<your-vault> ./op-vault/op-upsert-ci-secrets.sh`.

The `op-vault/` tree stays gitignored in your fork too — the point is to keep the local `.env` + populator script under operator control, not under version control.

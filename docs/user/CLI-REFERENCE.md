# JaiClaw CLI Reference

Comprehensive reference for the JaiClaw command-line interface.

## Installation

The `install.sh` script supports three install modes: the default JAR download, a Docker-backed launcher, and a from-source build. All three land at `~/.jaiclaw/` and produce the same `jaiclaw` command on your PATH — the difference is what backs it and what needs to be locally installed.

| Mode | Flag | Backing artifact | Needs on target | First-install time |
|---|---|---|---|---|
| Default (JAR) | *(none)* | fat JAR from Nexus | Java 21 (offers SDKMAN install) | ~30 sec |
| Docker | `--docker` | Docker image | Docker daemon | ~1 min (image pull) |
| From source | `--from-source` | git checkout + `./mvnw` build | git + Java 21 | 5–15 min (cold cache) / ~1 min (warm) |

### Default (JAR)

```bash
# curl-installable — downloads the launcher + fat JAR to ~/.jaiclaw
curl -fsSL https://jaiclaw.io/install.sh | bash

# Or run from a local clone
./install.sh
```

If Java 21+ isn't on PATH, the installer offers to install it via SDKMAN interactively. Under `curl | bash`, the prompt reads from `/dev/tty`; if that's not available (CI, container), the installer prints manual instructions and continues in degraded mode. Set `JAICLAW_NON_INTERACTIVE=true` to skip the prompt entirely.

### Docker

For operators who prefer no local Java. The installer pulls the CLI image and writes a shim launcher at `~/.jaiclaw/bin/jaiclaw` that runs `docker run` behind the scenes, mounting `~/.jaiclaw/` for profile + session persistence.

```bash
curl -fsSL https://jaiclaw.io/install.sh | bash -s -- --docker

# Or explicit version
JAICLAW_VERSION=0.9.3-SNAPSHOT curl -fsSL https://jaiclaw.io/install.sh | bash -s -- --docker

# Or from a local clone
./install.sh --docker
```

The default image base is `tooling.taptech.net:5000/jaiclaw-cli` — override with `JAICLAW_DOCKER_IMAGE_BASE=<registry>/<repo>` if you host the image elsewhere.

The shim launcher looks like this and is safe to inspect / re-generate:

```bash
$ cat ~/.jaiclaw/bin/jaiclaw
#!/usr/bin/env bash
set -euo pipefail
IMAGE="${JAICLAW_IMAGE:-tooling.taptech.net:5000/jaiclaw-cli:latest}"
JAICLAW_HOME="${JAICLAW_HOME:-$HOME/.jaiclaw}"
exec docker run --rm -it \
    -v "$JAICLAW_HOME:/home/jaiclaw/.jaiclaw" \
    "$IMAGE" "$@"
```

### From source

For installing a specific git branch, tag, or SHA on a fresh box — useful when a snapshot hasn't been deployed yet, or when you want to test a PR head.

```bash
# Default: latest main HEAD
curl -fsSL https://jaiclaw.io/install.sh | bash -s -- --from-source

# Specific ref (branch, tag, or SHA)
JAICLAW_REF=v0.9.2 curl -fsSL https://jaiclaw.io/install.sh | bash -s -- --from-source
JAICLAW_REF=feat/my-branch curl -fsSL https://jaiclaw.io/install.sh | bash -s -- --from-source

# Use an existing local checkout (skips the clone)
JAICLAW_SOURCE_DIR=$HOME/dev/jaiclaw ./install.sh --from-source
```

**Time-honest logging.** The Maven build streams to `~/.jaiclaw/install.log` (tail it in another terminal) and the installer emits a `Building... (Nm elapsed)` heartbeat every 30 seconds so a multi-minute build doesn't look hung. First-time builds take 5–15 minutes on a cold `~/.m2` cache; subsequent installs with warm cache finish in ~60 seconds.

**Disk usage.** ~500 MB during build (Maven dep download), ~80 MB resident (the jar), plus ~500 MB in `~/.m2/repository` that stays for future builds. The source tree is deleted after install unless you pass `--keep-source` or point at your own `JAICLAW_SOURCE_DIR` (which is never touched).

**Flags:**

| Flag | Effect |
|---|---|
| `--clean-cache` | `rm -rf ~/.m2/repository/io/jaiclaw` before build. Useful when re-testing a SNAPSHOT after a code change. Only prunes JaiClaw's own artifacts; third-party deps stay warm. |
| `--keep-source` | Preserve `$JAICLAW_HOME/src` after the build. Default is delete to save ~500 MB. |

### The `~/.jaiclaw/` layout

All three install modes produce the same layout:

```
~/.jaiclaw/
  bin/
    jaiclaw              # bash launcher (or Docker shim in --docker mode)
    jaiclaw-cli.jar      # fat JAR (absent in --docker mode)
  config.yaml            # global config (active profile pointer)
  install.log            # Maven build log (only after --from-source)
  profiles/
    default/
      application-local.yml   # profile-specific config
      .env                     # profile-specific secrets
      sessions/                # session history
```

## Architecture: Two-Path Dispatch

The `bin/jaiclaw` launcher uses a two-path architecture for optimal startup time:

```
bin/jaiclaw <command> [args]
     |
     +-- Fast-path (pure bash, no JVM) -----> version, doctor, help,
     |                                        config show/edit,
     |                                        profiles list/create/switch,
     |                                        auth status
     |
     +-- JVM-path (Spring Shell via JAR) ---> chat, setup, tools, skills,
                                              status, models, gateway,
                                              login, logout, model list/show,
                                              profile list/create/switch/show,
                                              sessions, session-history, new-session
```

**Fast-path commands** execute entirely in bash and complete in under 100ms. They handle diagnostics, configuration display, profile management, and auth status checks.

**JVM-path commands** delegate to `jaiclaw-cli.jar` (a Spring Boot fat JAR) via `java -jar`. These commands start the Spring context, load tools/skills/plugins, and can interact with LLM providers.

## Fast-Path Commands

### `version`

Print the JaiClaw version.

```bash
jaiclaw version
# jaiclaw 0.7.1-SNAPSHOT
```

Aliases: `--version`, `-v`

### `doctor`

Diagnose the JaiClaw environment. Checks Java, home directory, config files, API keys, Ollama, channels, and Docker.

```bash
jaiclaw doctor
```

Alias: `doc`

### `help`

Show usage information with all available commands.

```bash
jaiclaw help
```

Aliases: `--help`, `-h`

### `config show`

Display the current profile's `application-local.yml` configuration.

```bash
jaiclaw config show
jaiclaw --profile work config show
```

### `config edit`

Open the current profile's `application-local.yml` in `$EDITOR` (defaults to `vi`).

```bash
jaiclaw config edit
```

### `profiles list`

List all profiles under `~/.jaiclaw/profiles/`, marking the active one with `*`.

```bash
jaiclaw profiles list
#  * default              [yml env]
#    work                 [yml env]
#    staging              [--- ---]
```

### `profiles create <name>`

Create a new profile with template `application-local.yml` and `.env` files.

```bash
jaiclaw profiles create staging
```

### `profiles switch <name>`

Switch the active profile by updating `~/.jaiclaw/config.yaml`.

```bash
jaiclaw profiles switch work
```

### `auth status`

Show OAuth/API key authentication status for all profiles and external CLIs. Delegates to `scripts/auth-status.sh`.

```bash
jaiclaw auth status         # colored terminal table
jaiclaw auth status json    # machine-readable JSON
jaiclaw auth status simple  # one-line: OK|EXPIRING|EXPIRED|MISSING
```

Exit codes: `0` = OK, `1` = EXPIRED, `2` = EXPIRING, `3` = MISSING.

## JVM-Path Commands

All JVM-path commands require Java 21+ and the CLI fat JAR (built via `./mvnw package -pl :jaiclaw-cli -am -DskipTests`).

### `chat <message>`

Send a message to the configured LLM agent. Without arguments, starts an interactive REPL.

```bash
jaiclaw chat "What is the capital of France?"
jaiclaw                    # interactive REPL (no subcommand)
```

### `setup` / `onboard`

Launch the interactive setup wizard. Walks through LLM provider selection, channel configuration, security settings, and more.

```bash
jaiclaw setup
jaiclaw --profile work setup
```

### `tools`

List all registered tools available to the agent.

```bash
jaiclaw tools
```

### `skills`

List all loaded skills with their metadata.

```bash
jaiclaw skills
```

### `plugins`

List all loaded plugins with their status.

```bash
jaiclaw plugins
```

### `status`

Show system status including identity, agent, tool count, plugin count, and active sessions.

```bash
jaiclaw status
```

### `models`

Show configured LLM providers and their status.

```bash
jaiclaw models
```

### `model list` / `model-list`

List all configured LLM providers with their models, fallback models, and environment variables.

```bash
jaiclaw model-list
```

### `model show` / `model-show`

Show the currently active model and provider.

```bash
jaiclaw model-show
```

### `sessions`

List all active chat sessions.

```bash
jaiclaw sessions
```

### `session-history`

Show message history for a session.

```bash
jaiclaw session-history             # current session
jaiclaw session-history my-session  # specific session
```

### `new-session`

Start a new chat session (resets conversation history).

```bash
jaiclaw new-session
```

### `login [provider]`

Authenticate with an OAuth provider. Without arguments, lists available providers.

```bash
jaiclaw login                   # list providers
jaiclaw login chutes            # OAuth login for Chutes
jaiclaw login openai-codex      # OAuth login for OpenAI Codex
```

### `logout <profileId>`

Remove stored credentials for an auth profile.

```bash
jaiclaw logout chutes-default
```

### `auth status` / `auth-status`

Show auth profile status (also available as fast-path for the basic `status` subcommand).

```bash
jaiclaw auth status
```

### `auth rotate` / `auth-rotate`

Set or view the rotation order for a provider's auth profiles.

```bash
jaiclaw auth-rotate anthropic              # view current order
jaiclaw auth-rotate anthropic "a,b,c"      # set rotation order
```

### `auth pin` / `auth-pin`

Pin an auth profile for the current session.

```bash
jaiclaw auth-pin chutes-default
```

### `auth unpin` / `auth-unpin`

Clear the session auth profile override.

```bash
jaiclaw auth-unpin
```

### `profile list` / `profile-list` / `profiles`

List profiles (JVM-path version, also available as fast-path `profiles list`).

```bash
jaiclaw profile-list
```

### `profile create` / `profile-create`

Create a new profile (JVM-path version).

```bash
jaiclaw profile-create staging
```

### `profile switch` / `profile-switch`

Switch active profile (JVM-path version).

```bash
jaiclaw profile-switch work
```

### `profile show` / `profile-show`

Show the current active profile details.

```bash
jaiclaw profile-show
```

### `gateway start` / `gateway-start`

Start the JaiClaw gateway server.

```bash
jaiclaw gateway-start                    # local mode, port 8080
jaiclaw gateway-start --port 9090        # custom port
jaiclaw gateway-start --docker true      # Docker mode
```

### `gateway stop` / `gateway-stop`

Stop the gateway server (runs `docker compose down`).

```bash
jaiclaw gateway-stop
```

### `gateway status` / `gateway-status`

Check if the gateway is running (polls the health endpoint).

```bash
jaiclaw gateway-status
```

### `config`

Show current JaiClaw configuration (JVM-path version with full Spring context).

```bash
# JVM-path (loads Spring context, shows resolved config)
jaiclaw config

# Fast-path (shows raw YAML file)
jaiclaw config show
```

### `prompt` / `prompt-set`

Inspect and customize the interactive REPL command prompt. The format string supports the placeholders `${identity}`, `${profile}`, `${agent}`, `${model}`, `${tenant}`, and `${version}` (the jaiclaw-cli Maven version, read from the jar manifest — handy on from-source installs where you want the built version visible). Placeholders the framework can't resolve render as literal `${name}` so typos are obvious. Calling `prompt-set` writes the new format to the active profile's `application-local.yml` (under `jaiclaw.shell.prompt.format`) and updates the live shell immediately — no restart needed.

```text
shell> prompt
Format:  ${identity} >
Preview: JaiClaw >
File:    /Users/tap/.jaiclaw/profiles/default/application-local.yml

shell> prompt-set '${identity}@${profile} > '
Saved.
Format:  ${identity}@${profile} >
Preview: JaiClaw@default >
```

Common formats:

| Format | Renders as |
|--------|------------|
| `${identity} > ` *(default)* | `JaiClaw > ` |
| `${identity}@${profile}> ` | `JaiClaw@prod> ` |
| `[${profile}] ${agent}: ` | `[prod] default: ` |
| `${identity}/${model}> ` | `JaiClaw/claude-sonnet-4-6> ` |
| `${identity} (${version})> ` | `JaiClaw (0.9.3-SNAPSHOT)> ` |

Embed raw ANSI escapes for color (e.g. `'[36m${identity}[0m > '`). Persistence is per-profile, so switching profiles via `profile switch <name>` swaps both the config and the prompt visual cue.

## Profile Isolation

Profiles provide isolated configuration environments. Each profile has its own `application-local.yml`, `.env`, and `sessions/` directory.

### Resolution Order

The active profile is resolved in this order:

1. `--profile <name>` flag (highest priority)
2. `JAICLAW_PROFILE` environment variable
3. `active-profile` in `~/.jaiclaw/config.yaml`
4. `default` (fallback)

### Directory Structure

```
~/.jaiclaw/
  config.yaml                        # active-profile: default
  profiles/
    default/
      application-local.yml          # Spring config
      .env                           # API keys, secrets
      sessions/                      # session history
    work/
      application-local.yml
      .env
      sessions/
```

### Usage

```bash
# Use a specific profile for one command
jaiclaw --profile work chat "hello"
jaiclaw --profile staging status

# Switch the default profile
jaiclaw profiles switch work

# Set via environment variable
export JAICLAW_PROFILE=work
jaiclaw chat "hello"
```

### Profile .env Auto-Loading

The `bin/jaiclaw` launcher automatically sources `~/.jaiclaw/profiles/<active>/.env` before dispatching commands. This means API keys set in the `.env` file are available to both fast-path and JVM-path commands.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JAICLAW_HOME` | `~/.jaiclaw` | Base directory for config, profiles, and data |
| `JAICLAW_PROFILE` | *(from config.yaml)* | Override active profile |
| `JAVA_HOME` | *(auto-detected)* | Java installation directory |
| `ANTHROPIC_API_KEY` | `not-set` | Anthropic API key |
| `OPENAI_API_KEY` | `not-set` | OpenAI API key |
| `GEMINI_API_KEY` | `not-set` | Google Gemini API key |
| `AWS_ACCESS_KEY_ID` | *(unset)* | AWS Bedrock access key |
| `AWS_REGION` | `us-east-1` | AWS region for Bedrock |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_ENABLED` | `false` | Enable Ollama provider |
| `ANTHROPIC_ENABLED` | `true` | Enable Anthropic provider |
| `OPENAI_ENABLED` | `false` | Enable OpenAI provider |
| `GEMINI_ENABLED` | `false` | Enable Gemini provider |
| `BEDROCK_ENABLED` | `false` | Enable Bedrock provider |
| `AI_PROVIDER` | `anthropic` | Default AI provider |
| `ANTHROPIC_MODEL` | `claude-sonnet-4-5` | Anthropic model name |
| `OPENAI_MODEL` | `gpt-4o` | OpenAI model name |
| `GEMINI_MODEL` | `gemini-2.0-flash` | Gemini model name |
| `BEDROCK_MODEL` | `us.anthropic.claude-3-5-sonnet-20241022-v2:0` | Bedrock model name |
| `OLLAMA_MODEL` | `llama3` | Ollama model name |
| `TELEGRAM_BOT_TOKEN` | *(unset)* | Telegram bot token |
| `SLACK_BOT_TOKEN` | *(unset)* | Slack bot token |
| `DISCORD_BOT_TOKEN` | *(unset)* | Discord bot token |
| `EDITOR` | `vi` | Editor for `config edit` |

## Docker Usage

The JaiClaw CLI is available as a Docker image at `taptechnet/jaiclaw-cli`.

### Quick Start

```bash
# Version check (fast-path, instant)
docker run --rm taptechnet/jaiclaw-cli version

# Environment diagnostics
docker run --rm taptechnet/jaiclaw-cli doctor

# List tools (JVM-path)
docker run --rm taptechnet/jaiclaw-cli tools

# Chat with API key
docker run --rm \
  -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY \
  taptechnet/jaiclaw-cli chat "hello"
```

### With Host Configuration

Mount your `~/.jaiclaw` directory to persist profiles and configuration:

```bash
docker run --rm \
  -v ~/.jaiclaw:/home/jaiclaw/.jaiclaw \
  taptechnet/jaiclaw-cli config show

docker run --rm \
  -v ~/.jaiclaw:/home/jaiclaw/.jaiclaw \
  taptechnet/jaiclaw-cli profiles list
```

### Image Details

- **Base image:** `eclipse-temurin:21-jre` (Debian)
- **Application files:** `/opt/jaiclaw/` (launcher, JAR, scripts — survives volume mounts)
- **Entrypoint:** `/opt/jaiclaw/bin/jaiclaw` bash launcher (preserves two-path dispatch)
- **User data:** `/home/jaiclaw/.jaiclaw` (profiles, config, sessions — mountable from host)
- **User:** `jaiclaw` (non-root)
- **Home:** `/home/jaiclaw`
- **Volume:** Mount `~/.jaiclaw` to `/home/jaiclaw/.jaiclaw` for config persistence

### Building the Docker Image

```bash
export JAVA_HOME=/path/to/java21

# Build the fat JAR and Docker image in one step
./mvnw package -pl :jaiclaw-cli -am -Pk8s -DskipTests

# The image is tagged as io.jaiclaw/jaiclaw-cli:<version>
docker images | grep jaiclaw-cli
```

An alternative JKube inline assembly build is available via `-Pk8s-inline` (produces `linux/amd64` images only):

```bash
./mvnw package -pl :jaiclaw-cli -am -DskipTests
./mvnw k8s:build -pl :jaiclaw-cli -Pk8s-inline -DskipTests
```

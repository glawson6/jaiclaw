# JaiClaw CLI Reference

Comprehensive reference for the JaiClaw command-line interface.

## Installation

### From Source

```bash
# Clone and build
git clone https://github.com/glawson6/jaiclaw.git
cd jaiclaw
export JAVA_HOME=/path/to/java21
./mvnw package -pl :jaiclaw-cli -am -DskipTests

# Run directly from the repo
bin/jaiclaw version
```

### Via Installer

```bash
# curl-installable (downloads launcher + JAR to ~/.jaiclaw)
curl -sSL https://raw.githubusercontent.com/glawson6/jaiclaw/main/install.sh | bash

# Or run from the repo
./install.sh
```

The installer creates the following directory structure:

```
~/.jaiclaw/
  bin/
    jaiclaw              # bash launcher
    jaiclaw-cli.jar      # fat JAR
  config.yaml            # global config (active profile)
  profiles/
    default/
      application-local.yml   # profile-specific config
      .env                     # profile-specific secrets
      sessions/                # session history
```

### Via Docker

```bash
# Fast-path commands (no JVM)
docker run --rm taptechnet/jaiclaw-cli version
docker run --rm taptechnet/jaiclaw-cli doctor

# JVM-path commands
docker run --rm taptechnet/jaiclaw-cli tools
docker run --rm taptechnet/jaiclaw-cli status

# With host config volume
docker run --rm -v ~/.jaiclaw:/home/jaiclaw/.jaiclaw taptechnet/jaiclaw-cli config show

# With API key
docker run --rm -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY taptechnet/jaiclaw-cli chat "hello"
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

Inspect and customize the interactive REPL command prompt. The format string supports the placeholders `${identity}`, `${profile}`, `${agent}`, `${model}`, and `${tenant}`. Placeholders the framework can't resolve render as literal `${name}` so typos are obvious. Calling `prompt-set` writes the new format to the active profile's `application-local.yml` (under `jaiclaw.shell.prompt.format`) and updates the live shell immediately — no restart needed.

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

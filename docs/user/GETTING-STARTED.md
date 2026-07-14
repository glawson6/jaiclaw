# Getting Started with JaiClaw

> **Goal:** from `git clone` to "the LLM responded over an HTTP API in
> my terminal" in under 15 minutes.

This guide is intentionally short. For deep configuration go to
[CONFIGURATION.md](CONFIGURATION.md); for production deployment go to
[OPERATIONS.md](OPERATIONS.md).

## What JaiClaw is, in two sentences

JaiClaw is a Java/Spring runtime for chat-driven AI assistants —
channels (Telegram, Slack, Discord, etc.), tools (file editing,
browser automation, K8s, ~38 built-in), skills (Markdown-defined
behaviors), and MCP server hosting all in one cleanly-layered Maven
project.

It is **not** a development assistant like Claude Code or Cursor;
those help you write code. JaiClaw is the runtime you embed *in your
own application* to give it agent capabilities. (See
[../dev/JAICLAW-VS-CLAUDE-CODE wishes](../INDEX.md) — that
positioning doc lives in the private hub.)

## Pick your path

| If you... | Use | Time to first response |
|---|---|---|
| Just want to see it work | `./quickstart.sh` (Docker) | 10–20 min first build, ~2 min after |
| Want to embed it in a Spring Boot app | Maven Central import | 30 min |
| Want to script against it from another Java tool | `./start.sh cli` (Docker CLI, no Java needed locally) | 5 min |
| Want to run on bare metal (no Docker) | `./start.sh local` | 5 min once `JAVA_HOME=21` is set |

Path 1 (Docker quickstart) and Path 4 (local) cover 90% of first-time
users. Pick Docker if you'd rather not install Java 21 globally;
local if you're already a Java developer with `sdkman` or similar.

## Prerequisites

Run these and confirm each line returns a sane answer before going
further.

```bash
docker --version          # paths 1, 3 — any version that supports compose v2
java -version             # paths 2, 4 — must be 21.x.x
echo $JAVA_HOME           # paths 2, 4 — should point at a JDK 21 install
echo $ANTHROPIC_API_KEY   # any path that uses Anthropic
```

If you don't have an Anthropic API key handy, JaiClaw also runs
against OpenAI, Gemini, Ollama (local), or MiniMax — see the
provider matrix in [CONFIGURATION.md](CONFIGURATION.md).

## The first 10 minutes

### Path 1 — Docker quickstart

```bash
git clone https://github.com/glawson6/jaiclaw.git
cd jaiclaw

ANTHROPIC_API_KEY=sk-ant-... ./quickstart.sh
```

The **first build will take 10–20 minutes** because Maven downloads
~2 GB of dependencies and builds the Docker image from scratch.
Subsequent runs start in ~30 s. The script writes the auto-generated
API key to `~/.jaiclaw/api-key`.

Once the gateway is up:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "hello"}'
```

### Path 4 — Local Java

```bash
git clone https://github.com/glawson6/jaiclaw.git
cd jaiclaw

# JAVA_HOME must point at JDK 21
export JAVA_HOME=/path/to/jdk-21

ANTHROPIC_API_KEY=sk-ant-... ./start.sh local
```

This boots the gateway in your shell — first run takes ~5 minutes
(dependency download), subsequent runs ~30 s. Same curl test as above
works (default `mode=none` here, no API key header needed unless
you've set one).

### Path 2 — Embed in your own Spring Boot app

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.jaiclaw</groupId>
      <artifactId>jaiclaw-bom</artifactId>
      <version>0.7.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-spring-boot-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
  </dependency>
</dependencies>
```

Then write a minimal application. The
[hello-world example](../../jaiclaw-examples/hello-world/) shows the
~30-line shape end-to-end (Application class + one tool +
`application.yml`).

#### Piloting Spring Boot 4 — `1.0.0-SNAPSHOT`

A Boot 4.1 preview line is published as `1.0.0-SNAPSHOT` to TapTech's
internal Nexus for early adopters. Stack: Spring Boot 4.1.0, Spring AI
2.0.0, Spring Shell 4.0.2, Embabel Agent 2.0.0-SNAPSHOT, Apache Camel
4.21.0, Jackson 3 (`tools.jackson.*`), Groovy 5, Spock 2.4-groovy-5.0.

Add the snapshot repos alongside the BOM import:

```xml
<repositories>
  <repository>
    <id>taptech-snapshots</id>
    <url>https://tooling.taptech.net/repository/maven-snapshots/</url>
    <snapshots><enabled>true</enabled></snapshots>
    <releases><enabled>false</enabled></releases>
  </repository>
  <repository>
    <id>embabel-snapshots</id>
    <url>https://repo.embabel.com/artifactory/libs-snapshot</url>
    <snapshots><enabled>true</enabled></snapshots>
    <releases><enabled>false</enabled></releases>
  </repository>
</repositories>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.jaiclaw</groupId>
      <artifactId>jaiclaw-bom</artifactId>
      <version>1.0.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

`1.0.0-SNAPSHOT` is **pilot-only** — GA is gated on Embabel cutting a
non-SNAPSHOT Boot-4 release (see
[docs/spring-boot-4-upgrade/02-embabel-gate.md](../spring-boot-4-upgrade/02-embabel-gate.md)).
Production users stay on the latest Maven Central release
(`0.9.x`).

## What to do once it's up

Three concrete next steps, pick the one that matches your interest:

1. **Talk to it via Telegram** (~10 min): create a bot with
   `@BotFather`, set `TELEGRAM_BOT_TOKEN`, and rerun. Full walk-through
   in [TELEGRAM-SETUP.md](TELEGRAM-SETUP.md).
2. **Add a custom tool** (~15 min): the smallest example is in
   [jaiclaw-examples/hello-world/](../../jaiclaw-examples/hello-world/);
   the tool authoring tutorial (when PR7 lands) will live at
   `docs/user/AUTHORING-TOOLS.md`.
3. **Run a pipeline example** (~10 min): pick one of the seven
   pipeline examples in [EXAMPLES.md](EXAMPLES.md) § Pipelines and
   follow its README.

## Top 5 first-time failures

In order of how often they hit people:

### 1. "Cannot connect to Docker daemon"

Docker Desktop isn't running. Start it from your applications menu
and wait for the whale icon to settle.

### 2. "Cannot find Java 21" (paths 2 & 4)

`JAVA_HOME` is either unset or points at a different JDK version.
Fix:

```bash
# macOS with SDKMAN
sdk install java 21.0.9-oracle
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
export PATH="$JAVA_HOME/bin:$PATH"
```

### 3. Ollama OOM on Gemma 4

Default Ollama config tries to load a model that needs more RAM than
you have. See [GEMMA4-HARDWARE-GUIDE.md](GEMMA4-HARDWARE-GUIDE.md)
for the VRAM table; pick a smaller model or switch to a cloud
provider for first-time setup.

### 4. "Authentication failed" from Anthropic

Either `ANTHROPIC_API_KEY` is unset, or you're behind a corporate
proxy that strips the `Authorization` header. Verify the key
directly:

```bash
curl https://api.anthropic.com/v1/models \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01"
```

If that fails, the key is the problem; if it succeeds but JaiClaw
still fails, check `JAVA_TOOL_OPTIONS` for proxy settings.

### 5. Port 8080 in use

Something else is already listening. Set
`GATEWAY_PORT=8081` in your environment (or `SERVER_PORT=8081` for the
embed path) and try again.

## Where to next

- **Deep operations:** [OPERATIONS.md](OPERATIONS.md) — security
  modes, profiles, persistence backends, environment-variable
  reference.
- **Configuration recipes:** [CONFIGURATION.md](CONFIGURATION.md) —
  minimal-viable config and grouped property reference.
- **Skills + cost:** [SKILLS.md](SKILLS.md) — what skills are, the
  ~26K token default cost, how to slim it.
- **All examples:** [EXAMPLES.md](EXAMPLES.md) — the 40-example
  catalog.
- **Architecture:** [../dev/ARCHITECTURE.md](../dev/ARCHITECTURE.md)
  — module DAG and message flow (for contributors and
  framework-curious users).

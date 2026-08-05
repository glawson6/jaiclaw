# JaiClaw CLI :: GitHub

Runnable slash-command dispatcher for GitHub PR/issue/commit comments.

## Problem

Modern engineering workflows live inside GitHub — reviewers leave
comments on PRs, engineers ask questions on issues, and CI writes
findings on commits. Getting an LLM into that flow usually means
running a keep-alive webhook receiver behind ngrok or a hosted service,
managing a public URL, signing HMAC requests, and dealing with process
supervision. Overkill for teams that just want `/chat`, `/summarize`,
or `/faq` to work in a repo without operating infrastructure.

## Solution

A single self-contained fat jar invoked by a **GitHub Actions
workflow** on every `/`-prefixed comment. The Action provides the
runtime (ephemeral Ubuntu VM), the auth (workflow `GITHUB_TOKEN`),
and the trigger. The jar parses the comment body, dispatches to the
matching `SlashCommand` bean, calls the LLM through JaiClaw's
`AgentRuntime`, and posts the reply back as a comment on the same
thread. When the process exits, it exits — the "session" is the
PR/issue thread itself, re-fetched via the GitHub API on the next
call.

Ships with four commands:

| Command | Purpose |
|---|---|
| `/chat <text>` | LLM Q&A with prior thread comments as context |
| `/faq [topic]` | Answer a project FAQ from JaiClaw's `jaiclaw-docs` MCP resources |
| `/summarize` | PR-only: summarise title, body, and diff |
| `/help` | List available commands |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Comment arrives on PR/issue/commit                             │
│  (issue_comment | pull_request_review_comment | commit_comment) │
└──────────────────────────────┬──────────────────────────────────┘
                               │  Workflow fires (if body starts with /)
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  GitHub Actions runner (ubuntu-latest)                          │
│  - actions/checkout@v4 (head SHA)                               │
│  - setup-java@v4 (temurin 21)                                   │
│  - curl -o jaiclaw-cli-github.jar                               │
│  - java -jar jaiclaw-cli-github.jar handle-comment              │
│      --repo owner/name --issue N --body "$BODY" ...             │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│  HandleCommentCommand (@Command in Spring Shell)                │
│    1. SlashCommandRegistry.parse(body)                          │
│         → resolves /chat|/faq|/summarize|/help to handler       │
│    2. Handler.handle(SlashContext)                              │
│         ChatSlashHandler                                        │
│           - fetches prior thread via github_pr_thread tool      │
│           - injects as history                                  │
│           - AgentInvoker.invoke(sessionKey, prompt)             │
│               → AgentRuntime.run() → LLM                        │
│         SummarizeSlashHandler                                   │
│           - fetches PR diff via github_pr_diff tool             │
│           - AgentInvoker.invoke(...) → LLM                      │
│         FaqSlashHandler                                         │
│           - DocsRepository.search(topic) or findAll()           │
│           - AgentInvoker.invoke(...) → LLM                      │
│         HelpSlashHandler                                        │
│           - non-LLM: enumerate registered SlashCommand beans    │
│    3. GithubClientProvider → issue.comment(reply)               │
│         → posts reply back to the same thread                   │
└─────────────────────────────────────────────────────────────────┘
```

**Key beans:**

- `HandleCommentCommand` — the workflow's entry point. `@Command(name="handle-comment")`.
- `SlashCommandRegistry` — discovers `SlashCommand` beans, indexes by name, exposes `parse(body)`.
- `AgentInvoker` — thin wrapper over `AgentRuntime.run()` for consistent
  session-key naming across handlers.
- `SystemPromptLoader` — reads the configured prompt file once
  (classpath: or file:) and caches it.
- 12 GitHub tools from `jaiclaw-tools-github` — auto-registered into
  `ToolRegistry`; the LLM can call them from inside `/chat`.
- All bundled JaiClaw built-in tools (`file_read`, `ascii_render`,
  `web_fetch`, etc.) — available to the LLM via `jaiclaw-spring-boot-starter`
  → `ToolBeanDiscovery`.

## Design

**Why GitHub Actions vs a webhook receiver?** No infrastructure. No
public URL. No process supervision. `GITHUB_TOKEN` is minted per-run
by GitHub, so the auth story is dead simple. Trade-off is ~4s JVM
cold-start per invocation, well under the Actions billing floor.

**Why is the PR thread the session?** In-memory sessions would be lost
between invocations. Redis would work but adds infrastructure. The
thread already IS a chronological transcript of the conversation, and
the GitHub API returns it in <200ms — so `/chat` re-hydrates the
context on every call and gets multi-turn behaviour for free. Bonus:
history survives restarts, deploys, and thread archival.

**Why `SlashCommand` as an SPI?** Anyone can drop a new
`@Component` implementing `SlashCommand` into an adopter fork and the
registry picks it up. No fork of the dispatcher needed. Same
`@ConditionalOnMissingBean` override story as JaiClaw's tools.

**Why 12 GitHub tools instead of hand-rolled REST calls?** Because the
tools are also `ToolCallback`s — the LLM can invoke them directly.
`/chat what does this PR change?` doesn't need special-casing in the
`ChatSlashHandler`; the LLM decides to call `github_pr_diff` +
`github_pr_files` itself. The dispatcher provides thread history as
default context; everything else the LLM asks for on demand.

**Why bundled default system prompt?** So the tool works with zero
config. Adopters override via `--jaiclaw.cli-github.system-prompt-file`
or `JAICLAW_CLI_GITHUB_SYSTEM_PROMPT_FILE` env var when they want a
persona of their own.

## Build & Run

### Prerequisites

- **Java 21+**
  ```bash
  export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
  ```
- A GitHub PAT with `issues:write` on a scratch repo, exported as
  `GITHUB_TOKEN`
- An LLM provider key — `ANTHROPIC_API_KEY` for the default provider

### Build the fat jar

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw package -pl :jaiclaw-cli-github -am -DskipTests -o

ls -la apps/jaiclaw-cli-github/target/jaiclaw-cli-github-*-exec.jar
# ~80–120 MB fat jar
```

### Run once — simulate a GitHub Action invocation

```bash
export GITHUB_TOKEN=ghp_your_pat
export ANTHROPIC_API_KEY=sk-ant-your-key
export AI_PROVIDER=anthropic

java -jar apps/jaiclaw-cli-github/target/jaiclaw-cli-github-*-exec.jar \
    handle-comment \
    --repo glawson6/scratch \
    --issue 1 \
    --body "/chat what does the README say?"
```

Expected log lines:

```
INFO  handle-comment: repo=glawson6/scratch issue=1 sha= commentId=-1 bodyLen=32
INFO  Dispatching /chat to ChatSlashHandler
DEBUG Fetched 0 prior comments from thread
DEBUG Dispatching /chat for github:glawson6/scratch#1 ...
INFO  Posted reply to glawson6/scratch #1 (nnn chars)
```

Verify the reply comment appears on GitHub.

### Run interactively (local REPL)

Invoke with no args to boot the Spring Shell:

```bash
java -jar apps/jaiclaw-cli-github/target/jaiclaw-cli-github-*-exec.jar
```

You can call `handle-comment --repo ... --issue ... --body "/chat foo"`
repeatedly without paying JVM cold-start each time. Useful for
iterating on prompts and command implementations locally.

### Ship the workflow into a real repo

Copy `.github/workflows/jaiclaw-slash-commands.yml` into the adopter's
`.github/workflows/` directory. Add the required secret:

- `ANTHROPIC_API_KEY` — set in repo Settings → Secrets → Actions

That's it. Open an issue, comment `/help`, watch Actions run.

## Configuration

All settings live in `src/main/resources/application.yml` and are
overridable via env vars (Spring's relaxed binding) or CLI args.

| Property | Default | Purpose |
|---|---|---|
| `jaiclaw.github.enabled` | `true` | Turn the 12 GitHub tools on |
| `jaiclaw.github.token` | `${GITHUB_TOKEN}` | PAT for the GitHub client |
| `jaiclaw.cli-github.system-prompt-file` | `classpath:prompts/default-system-prompt.md` | The bot's persona |
| `jaiclaw.cli-github.max-thread-history` | `20` | Max prior comments injected into `/chat` |
| `spring.ai.model.chat` | `anthropic` | LLM provider (see JaiClaw docs for options) |
| `spring.ai.anthropic.chat.model` | `claude-sonnet-4-5` | Anthropic model name |

## Extending

Add a new slash command:

```java
@Component
public class MyCommand implements SlashCommand {
    @Override public String name() { return "my-command"; }
    @Override public String description() { return "..."; }
    @Override public CommandResult handle(SlashContext ctx) {
        return CommandResult.ok("hello from /my-command");
    }
}
```

Rebuild, ship the jar, done — `SlashCommandRegistry` discovers it
automatically.

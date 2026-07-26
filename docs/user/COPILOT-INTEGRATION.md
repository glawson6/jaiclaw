# GitHub Copilot Integration

JaiClaw exposes GitHub Copilot's multi-model backend as a Spring AI `ChatModel`
through the `jaiclaw-github-copilot` module. Copilot's session-based Java SDK
is wrapped into the standard `ChatModel.call(Prompt) → ChatResponse` +
`stream(Prompt) → Flux<ChatResponse>` contracts so any code written against
Spring AI's model abstraction can target Copilot without change.

## When to use this

- You want single-auth access to GPT (via OpenAI), Claude (via Anthropic), and
  Gemini (via Google) — all under one GitHub Copilot entitlement rather than
  three separate API-key contracts.
- You already run `gh copilot` interactively and want the same auth to power a
  programmatic agent.
- You're building a local-development agent that shouldn't require the operator
  to provision LLM API keys separately.

## When **not** to use this

- **Multi-tenant deployments.** Copilot's auth is a single-user, CLI-hosted
  personal token. Every tenant on a multi-tenant JaiClaw gateway would share
  the same GitHub identity. The starter logs a WARN if you enable it with
  `jaiclaw.tenant.mode=multi`; do not ignore it.
- **Server-side production** where you can't (or don't want to) install the
  `gh` CLI and keep it authenticated. Route via `jaiclaw-starter-anthropic` or
  `jaiclaw-starter-openai` with a direct API key instead.

## Prerequisites

Install the GitHub CLI + Copilot extension on the machine that runs JaiClaw:

```bash
# macOS
brew install gh
gh auth login
gh extension install github/gh-copilot
```

Confirm the CLI can talk to Copilot before enabling the starter:

```bash
gh copilot --version           # 1.0.55 or later
gh copilot suggest "list files"  # prompts + returns a command
```

If either fails, fix that first — the JaiClaw starter is a thin bridge over
the CLI's own auth path.

## Add the dependency

Add the starter to your project (BOM already manages the version):

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-starter-github-copilot</artifactId>
    <type>pom</type>
</dependency>
```

## Enable it in application.yml

The starter is **opt-in** — no beans load until you flip `enabled: true`.
The minimum viable configuration:

```yaml
jaiclaw:
  copilot:
    enabled: true
    chat:
      options:
        model: claude-3.5-sonnet
```

Full configuration reference:

```yaml
jaiclaw:
  copilot:
    enabled: false                    # master switch (default false)
    auth:                             # see § Authentication below
      # Optional PAT. When set, bypasses `gh auth login` and hands the token
      # directly to the SDK via CopilotClientOptions.setGithubToken(...).
      # Env-friendly: usually GITHUB_TOKEN or a Copilot-specific PAT with
      # the copilot scope. Leave null to use the CLI's stored credential.
      github-token: ${GITHUB_TOKEN:}
      # Optional GitHub host — for GitHub Enterprise Server. Defaults to
      # github.com. Set to your GHE hostname (e.g. github.mycorp.example)
      # when using a GHE-hosted Copilot subscription. Wired via GH_HOST in
      # the CLI's environment.
      github-host: github.com
    session:
      strategy: per-call              # per-call (default) | pooled (not yet implemented)
      max-idle-seconds: 300           # pooled only — evict after N seconds of inactivity
      max-sessions: 32                # pooled only — cap on live sessions
    cli:
      binary: gh                      # override for non-standard installs; null → PATH
      url:                            # SDK CLI-download URL; null → GitHub-hosted default.
                                      # Only used when the SDK bootstraps the CLI itself
                                      # (i.e. `binary` unset AND no gh on PATH). Set for
                                      # air-gapped installs pointing at an internal mirror.
      check-on-startup: true          # probe `gh copilot` at boot; log entitled models
                                      # + resolved GitHub identity
    provider:                         # optional endpoint override (see § Provider override)
      type:                           # openai | anthropic | azure | google (free-form string)
      base-url:                       # e.g. https://api.minimax.io/anthropic
      wire-api:                       # optional wire protocol override
      transport:                      # optional transport override (e.g. http)
    chat:
      options:                        # global defaults; per-call Prompt options override
        model: claude-3.5-sonnet      # any string; CopilotModel enum has convenience constants
        temperature: 0.7
        max-tokens: 4096
```

## Authentication

The Copilot Java SDK does **not** use a traditional API key. Two supported
credential-resolution paths, in precedence order:

### Path 1 — Delegated to the `gh copilot` CLI (default)

Leave `jaiclaw.copilot.auth.github-token` unset. On every session creation,
the SDK spawns the `gh copilot` child process, and `gh` hands it a
short-lived OAuth token from its own credential store — the same token
`gh auth token` prints. This is the recommended path for interactive
developer machines where you've already run `gh auth login`.

The SDK does not read a `COPILOT_API_KEY` environment variable — there is
no such variable in its contract. All credentials flow through the CLI.

### Path 2 — Direct Personal Access Token (headless / CI)

For headless containers or CI runners where an interactive `gh auth login`
isn't practical, set the PAT directly:

```yaml
jaiclaw:
  copilot:
    enabled: true
    auth:
      github-token: ${GITHUB_TOKEN:}
```

`GITHUB_TOKEN` is a Spring property-placeholder pull from the process
environment (the trailing `:` gives it a blank default so an unset env var
doesn't fail binding). The PAT must have the **`copilot` scope** on an
account with a Copilot subscription — a regular repo-scoped PAT will
authenticate but produce zero entitled models.

When a PAT is provided, the auto-config logs at INFO:
```
Copilot auth: PAT provided (github-token) — bypassing gh CLI credential store
```

The CLI startup probe then calls `client.getAuthStatus()` and logs the
resolved identity + auth type:
```
Copilot auth resolved: user=octocat host=github.com type=pat
```

### GitHub Enterprise Server

For GHE-hosted Copilot subscriptions, set `auth.github-host` to your GHE
hostname. The value is injected as the `GH_HOST` environment variable in
the CLI's spawn environment; `gh` then targets that instance's API instead
of `github.com`.

```yaml
jaiclaw:
  copilot:
    enabled: true
    auth:
      github-token: ${GITHUB_TOKEN:}
      github-host: github.mycorp.example
```

Startup log confirms:
```
Copilot auth: GH_HOST=github.mycorp.example — GitHub Enterprise Server routing enabled
```

### Why there is no `auth.github-user` field

A GitHub PAT identifies its owner server-side. The SDK does not accept a
separate `user` identifier — supplying one on the client would be
misleading. The `Copilot auth resolved: user=...` INFO line at startup
tells you which identity the CLI/PAT resolved to at runtime.

### Two URLs, two purposes

There are two independent URL knobs in the config; don't confuse them:

| Knob | What it controls | Default |
|---|---|---|
| `jaiclaw.copilot.auth.github-host` | Which **GitHub instance** the CLI authenticates against (github.com or a GHE server). Governs where the PAT is validated. | `github.com` |
| `jaiclaw.copilot.provider.base-url` | Which **model backend** endpoint serves the chat request (Copilot proxy, MiniMax, Azure OpenAI, an internal reverse-proxy). | not attached — Copilot's server-side routing picks canonical endpoints |

Both are optional; both may be set independently. See § Provider override
below for `provider.base-url` usage.

### URL knobs — what defaults to what

Three independent URL / host knobs. Each answers a different question.

| Knob | What it controls | Default | When to override |
|---|---|---|---|
| `jaiclaw.copilot.auth.github-host` | Which **GitHub instance** the CLI authenticates the PAT against | `github.com` | GitHub Enterprise Server — set to your GHE hostname (e.g. `github.mycorp.example`). Injected as `GH_HOST` in the CLI's spawn environment. |
| `jaiclaw.copilot.cli.url` | Where the **SDK downloads the `gh copilot` CLI** from if it needs to bootstrap one | GitHub-hosted CLI download URL (SDK internal) | Air-gapped deployments pointing at an internal mirror. Ignored entirely when `cli.binary` is set to an existing installation. |
| `jaiclaw.copilot.provider.base-url` | Which **model backend** endpoint serves the chat request | not attached — Copilot's server-side routing picks canonical endpoints | Pointing Copilot at an alternative backend: an internal reverse-proxy, an Azure OpenAI deployment, a MiniMax Anthropic-compatible endpoint at `api.minimax.io/anthropic`, etc. When all `provider.*` fields are null, the module attaches **no** `ProviderConfig` and Copilot behaves normally. |

### Provider override

When set, `jaiclaw.copilot.provider.*` binds to a Copilot SDK
{@code ProviderConfig} that gets attached to every session via
{@code SessionConfig.setProvider(...)}. This is the escape hatch for
consumers who need to route through an internal proxy or an
OpenAI/Anthropic-compatible portal instead of hitting Copilot's canonical
endpoints.

**API keys and bearer tokens are deliberately NOT exposed on this block.**
The whole point of using Copilot is delegating auth to the `gh copilot`
CLI's OAuth token — a second auth path would defeat that design. If you
need API-keyed access to an OpenAI/Anthropic endpoint, prefer
`jaiclaw-starter-openai` or `jaiclaw-starter-anthropic` with a direct API
key over this block.

Example — routing Copilot's Anthropic provider through MiniMax's
Anthropic-compatible endpoint:

```yaml
jaiclaw:
  copilot:
    enabled: true
    provider:
      type: anthropic
      base-url: https://api.minimax.io/anthropic
    chat:
      options:
        model: claude-3.5-sonnet
```

## Using it from code

The `CopilotChatModel` bean is a standard Spring AI `ChatModel`. Inject it and
call it — no Copilot-specific code required at the call site:

```java
@Component
class Greeter {
    private final ChatModel chat;

    Greeter(ChatModel chat) { this.chat = chat; }

    String hello() {
        return chat.call(new Prompt("Reply with exactly: hello")).getResult()
                .getOutput().getText();
    }
}
```

Per-call model override, using the `CopilotModel` enum for autocomplete:

```java
import io.jaiclaw.copilot.CopilotChatOptions;
import io.jaiclaw.copilot.CopilotModel;

CopilotChatOptions perCall = CopilotChatOptions.builder()
        .model(CopilotModel.GPT_5)
        .temperature(0.2)
        .build();
ChatResponse response = chat.call(new Prompt("summarize this...", perCall));
```

Any model string works — the enum is just a convenience for the documented
set. Pass `.model("some-new-model-id")` directly if Copilot adds a model after
this module ships.

## Model selection precedence

At call time, the effective model is resolved in this order:

1. **Per-call**: `prompt.getOptions().getModel()` on the incoming Prompt.
2. **Global default**: `jaiclaw.copilot.chat.options.model` from
   `application.yml`, bound to the `CopilotChatOptions` bean passed to the
   `CopilotChatModel` constructor.
3. **SDK default**: when neither is set, the model field is passed as `null`
   to `SessionConfig.setModel(...)` and Copilot's server picks.

If `jaiclaw.copilot.cli.check-on-startup` is `true` (default), the auto-config
probes `client.listModels()` at boot and logs the entitled matrix at INFO. If
your configured default isn't in the entitled list, you get a WARN — the
request will still be sent (entitlements can change out of band), but the
warning gives you an early signal.

## Tool calling

JaiClaw tools registered via the standard `ToolCallback` SPI just work. The
adapter's `CopilotToolMapper` wraps each Spring AI callback as a Copilot
`ToolDefinition` whose handler delegates back into the callback. Because
Copilot's SDK runs the handler **in-process** when the assistant invokes the
tool, the round-trip completes inside `chat.call(...)` — you don't need to
implement a separate tool-execution loop.

The `AssistantMessage` returned on `chat.call(...)` still surfaces any tool
requests via `AssistantMessage#getToolCalls()`, so code that expects the
Spring AI tool-loop shape (i.e. seeing `hasToolCalls() == true` and then
inspecting the tool-call list) works unchanged.

## Streaming

`chat.stream(Prompt)` returns a `Flux<ChatResponse>` that emits a single
element — the completed response. A true token-by-token streaming
implementation via the SDK's `AssistantMessageDeltaEvent` is a follow-up;
the initial cut prioritizes the request/response path where the SDK's
`sendAndWait` contract makes single-shot behavior the safe default.

## Troubleshooting

**"No entitled models" WARN at startup.** Run `gh auth status` — if it says
"not logged in", run `gh auth login`. Then confirm `gh extension list`
includes `github/gh-copilot`; if not, `gh extension install github/gh-copilot`.

**"Configured default model 'X' is not in the entitled list" WARN.** Your
GitHub Copilot subscription tier may not include the model you configured.
Log the entitled list from the same startup message and pick from there, or
set `jaiclaw.copilot.chat.options.model` to `null` and let the SDK's server
pick.

**Session creation hangs.** The SDK spawns the `gh copilot` CLI as a child
process; check the process is running and responsive with `gh copilot suggest
"echo hi"` from the same shell. On non-standard installs, set
`jaiclaw.copilot.cli.binary` to the absolute path of `gh`.

**"jaiclaw.copilot.enabled=true detected with jaiclaw.tenant.mode=multi"
WARN.** Read the "When not to use this" section above. If you understand the
implications and still want it (e.g. a bounded internal tool where every
tenant *should* share the operator's identity), the warning is informational
— it doesn't stop the app.

## Reference

- **Copilot Java SDK**: [github/copilot-sdk `java/`](https://github.com/github/copilot-sdk/tree/main/java)
- **Maven Central artifact**: `com.github:copilot-sdk-java` (pinned via
  `<copilot-sdk.version>` in the JaiClaw root pom).
- **JaiClaw module**: `extensions/jaiclaw-github-copilot/`
- **Starter meta-pom**: `jaiclaw-starters/jaiclaw-starter-github-copilot/`

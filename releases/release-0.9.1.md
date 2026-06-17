# JaiClaw 0.9.1 Release Notes

**Release Date:** TBD

> 0.9.1 is a maintenance + small-feature release on top of 0.9.0. Seven
> framework-level improvements ship together: **Spring bean auto-discovery
> for `ToolCallback`** (drop the manual `toolRegistry.registerAll(...)`
> boilerplate), **vision-attachment auto-injection** (image and PDF
> attachments now reach the LLM as native Spring AI `Media` content blocks),
> a **fix for per-agent `tools.allow` / `tools.deny` being silently
> dropped** under partial record-binding (functional + security fix),
> **channel-aware rendering profiles** for `ascii_box` / `ascii_render`
> so agents can pick width + padding appropriate for the target client,
> **pluggable chat memory** — `SessionManager` is now an SPI so
> downstream apps can swap the in-memory default for a durable backend
> (Redis, Postgres, JCache), a **fix for the Camel + Telegram filter
> bean ambiguity** that surfaced when apps adopted
> `jaiclaw-starter-pipeline` alongside a rate-limited Telegram channel,
> and **deterministic pipeline AGENT stages via Embabel** — pipeline
> authors can now opt individual AGENT stages into Embabel's GOAP
> planner with `runtime: embabel` for reproducible action chains.
>
> The auto-discovery, vision-attachment, ASCII-profile, and
> pluggable-chat-memory features are opt-out-by-default. The
> `tools.allow/deny` fix is purely a bug fix that makes existing YAML
> work as written. One of them (the `AttachmentRouter` SPI change) is a
> source-level break — see Breaking Changes below.

---

## Highlights

- **`@Bean ToolCallback` auto-discovery** — any Spring-managed
  `ToolCallback` bean is now auto-registered into `ToolRegistry` via a new
  `ToolBeanDiscovery` class. Collisions fail fast with a clear diagnostic.
  The previous Sentinel-style boilerplate (60 lines whose only job was to
  call `toolRegistry.registerAll(...)`) collapses to one-liner `@Bean`
  factories.

- **7 extension modules migrated to bean-based tool registration** —
  jaiclaw-code, jaiclaw-tasks, jaiclaw-memory-wiki, jaiclaw-rules,
  jaiclaw-tools-k8s, jaiclaw-tools-security, and the canvas-dashboard
  example all now contribute their tools as `@Bean ToolCallback`
  factories. The static `*Tools.registerAll(...)` helpers remain for
  out-of-tree callers but are no longer used by the framework.

- **Vision-attachment auto-injection** — image and PDF attachments on
  inbound `ChannelMessage`s now flow to the agent as Spring AI `Media`
  content blocks when `jaiclaw.gateway.auto-vision: true` (default).
  Telegram photos, Slack file uploads, Discord attachments, and email
  attachments all reach the LLM when wired to a vision-capable provider
  (Claude Sonnet, GPT-4o, Gemini). The end-user repro from
  `docs/issues/attachment-injection-gap.md` ("send a photo to a Telegram
  bot with the caption *'what is this?'*") now works out of the box.

- **`AttachmentRouter` SPI now returns `RouterResult`** — routers can
  optionally annotate the prompt the agent will see (e.g. a PDF
  extractor surfacing a one-line summary) or signal that they've fully
  handled an attachment. The minimal port for an existing implementation
  is two lines (change the return type and `return RouterResult.none();`).

- **`tools.allow` / `tools.deny` honored under record-binding fallback**
  — when Spring's `@ConfigurationProperties` binding for the
  `jaiclaw.agent.agents.*` map only partially succeeds (a real-world
  case observed on `jaiclaw-event-agent`), the auto-config now reads
  the indexed `tools.allow[N]` and `tools.deny[N]` list properties from
  `Environment` instead of hard-coding empty lists. This is both a
  functional fix (operator-declared allow/deny now applies) and a
  security fix (public Telegram bots no longer get unintended access
  to `shell_exec`, `claude_cli`, `file_*`, etc.). See
  `docs/issues/tool-allow-deny-env-fallback.md` for the original
  diagnosis. No migration required — YAML that was already legal now
  works as written.

- **Deterministic pipeline AGENT stages via Embabel.** A new optional
  `runtime` field on pipeline `AGENT` stages selects between the default
  `NATIVE` runtime (today's `GatewayServiceAccessor` → LLM+tool loop)
  and `EMBABEL`, which routes execution through Embabel's GOAP planner
  via the existing `AgentOrchestrationPort` SPI. Given the same input +
  `@Agent`-annotated class, Embabel produces a deterministic action
  sequence — useful when a pipeline does classify → extract → validate
  → approve and you can't afford the LLM picking a different tool
  ordering on each replay. Stages opt in per-stage:
  ```yaml
  stages:
    - name: classify
      type: AGENT
      runtime: embabel
      embabelWorkflow: invoice-classifier
  ```
  or via DSL:
  ```java
  .stage("classify").embabelAgent("invoice-classifier")
  ```
  `PipelineValidator` fails fast at startup if `runtime: embabel` is
  selected without `jaiclaw-starter-embabel` on the classpath, or if
  the requested workflow name isn't registered with `AgentPlatform`
  (with Levenshtein "did you mean?" suggestions for typos). The existing
  per-tenant `loopDelegate: embabel` path continues to work — this adds
  an additive per-stage opt-in. See
  `extensions/jaiclaw-pipeline/src/main/java/io/jaiclaw/pipeline/StageRuntime.java`
  and the new `EmbabelAgentOrchestrationPort`.

- **Camel pipeline + Telegram filter coexistence.**
  `JaiClawChannelAutoConfiguration$TelegramAutoConfiguration.telegramUserIdFilter`
  is now `@Primary`, resolving the `NoUniqueBeanDefinitionException` that
  surfaced when an app depended on both `jaiclaw-starter-pipeline` (which
  pulls in `jaiclaw-camel` and its `ObjectProvider<ChannelMessageHandler>.
  getIfAvailable()` call) and a Telegram channel with rate-limiting
  enabled. The filter wraps `GatewayService` and IS the canonical handler
  from the channel-adapter perspective — `@Primary` aligns Spring's
  autowire default with the runtime semantics. Apps that were applying
  the `BeanFactoryPostProcessor` workaround (e.g. `jaiclaw-event-agent`)
  can drop it. See `docs/issues/camel-channel-handler-disambiguation.md`.

- **Pluggable chat memory (SPI extraction).** `SessionManager` is now an
  interface; the default in-memory implementation moves to
  `io.jaiclaw.agent.session.InMemorySessionManager`. The auto-config
  registers the default via `@ConditionalOnMissingBean(SessionManager.class)`,
  so downstream apps wanting a durable backend (Redis, Postgres, JCache,
  encrypted-at-rest) declare their own `@Bean SessionManager` and the
  framework's default steps aside. All existing consumers
  (`AgentRuntime`, `GatewayService`, `JaiClawMessagingAutoConfiguration`,
  admin/status commands) already programmed to the public surface, so
  no caller changes are required. A new per-turn INFO log line
  `Session turn — session={} history={} msgs, prompt={} tokens (cached={}),
  response={} tokens` lands in `AgentRuntime` so operators can diagnose
  history depth + cache utilization at a glance. See
  `docs/issues/no-pluggable-chat-memory.md`. A Redis-backed
  implementation (`jaiclaw-session-redis`) will follow as a separate
  extension module.

- **Channel-aware rendering profiles for `ascii_box` / `ascii_render`**
  — both built-in tools gain a new optional `profile` parameter that
  selects a named width + padding bundle curated for common channels:
  `shell_80`, `shell_120`, `telegram_desktop`, `telegram_mobile`,
  `slack_desktop`, `slack_mobile`, `discord_desktop`, `discord_mobile`,
  `email`. Agents pick the profile that matches their target client and
  the renderer sizes the canvas accordingly. Operators add or override
  profiles via `jaiclaw.ascii.profiles.<name>.{width,padding}` in
  `application.yml`; the deployment default is settable via
  `jaiclaw.ascii.default-profile` (defaults to `shell_80`). LLM-supplied
  `profile` always beats the deployment default; explicit `width` /
  `padding` parameters always beat the profile. Both tools also accept
  a new optional `padding` parameter for inner-margin control. Existing
  callers continue to work — `width` is now optional on `ascii_render`
  (the profile supplies it when omitted), but all previous valid
  arguments produce identical output.

---

## Breaking Changes

**`AttachmentRouter.route(...)` signature** — return type changed from
`void` to
[`io.jaiclaw.gateway.attachment.RouterResult`](../core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/attachment/RouterResult.java).
Two known external consumers (Sentinel-style apps, `jaiclaw-event-agent`)
will need to update. The minimal port:

```java
// Before (0.9.0)
public void route(AttachmentPayload a, ChannelMessage c, TenantContext t) {
    // ... process ...
}

// After (0.9.1)
public RouterResult route(AttachmentPayload a, ChannelMessage c, TenantContext t) {
    // ... process ...
    return RouterResult.none();   // or RouterResult.annotated("[doc summary]")
}
```

**No other source-level breaks.** The pipeline-sync API from 0.9.0,
`ToolRegistry`, `AgentRuntime.run(String, AgentRuntimeContext)`, and every
public agent-runtime entry point continue to compile unchanged.

---

## New API surface

`io.jaiclaw.core.model.MediaAttachment` (new) — pure-Java carrier
(`mimeType`, `bytes`, `filename`). Lives in `jaiclaw-core`, no Spring
dependency. Conversion to Spring AI `Media` happens inside
`AgentRuntime.toSpringAiMessage()` where Spring AI is already a dependency.

`io.jaiclaw.core.model.UserMessage` (modified) — gains a sixth record
component `List<MediaAttachment> media`. Existing 3-arg and 5-arg
constructors continue to work (compact constructor defaults `media` to
`List.of()`). New builder method `media(List<MediaAttachment>)`.

`AgentRuntime.run(...)` (new overload) —
`CompletableFuture<AssistantMessage> run(String userInput, List<MediaAttachment> media, AgentRuntimeContext context)`.
The existing two-arg overload delegates with an empty list, so no
existing caller breaks. Same shape for `runStreaming(...)`.

`io.jaiclaw.gateway.GatewayProperties` (new) — `@ConfigurationProperties("jaiclaw.gateway")`.
Currently one field: `boolean autoVision` (default `true`).

`io.jaiclaw.gateway.attachment.RouterResult` (new) — record with
factories `none()`, `annotated(String)`, `fullyHandled()`,
`annotatedAndHandled(String)`.

`io.jaiclaw.tools.discovery.ToolBeanDiscovery` (new in 0.9.1; sketched
above) — the bean-discovery class.

`io.jaiclaw.agent.session.SessionManager` (modified) — now an
interface. 14 method signatures unchanged from the concrete-class
predecessor; all existing consumers compile without changes.

`io.jaiclaw.agent.session.InMemorySessionManager` (new) — the
process-local default. Replaces the previous concrete `SessionManager`
class verbatim; same three constructors, same `ConcurrentHashMap`
backing store, same hook-firing semantics.

`io.jaiclaw.pipeline.StageRuntime` (new enum) — `NATIVE | EMBABEL`,
referenced from the new `StageDefinition.runtime()` component. Defaults
to `NATIVE` via the record's compact constructor; the prior 9-arg
`StageDefinition` constructor remains available as a backward-compat
delegating overload.

`io.jaiclaw.pipeline.StageDefinition` (modified) — gains two new record
components (`runtime`, `embabelWorkflow`) on the canonical 11-arg form;
the 9-arg constructor is preserved for backward compatibility.

`io.jaiclaw.embabel.delegate.EmbabelAgentOrchestrationPort` (new) — the
first `AgentOrchestrationPort` implementation. Wraps Embabel's
`AgentPlatform` and shares lookup + execution + result-extraction with
`EmbabelAgentLoopDelegate` via a new package-private `EmbabelInvocations`
helper. Auto-registered when `AgentPlatform` is a bean (i.e. with
`jaiclaw-starter-embabel`).

`io.jaiclaw.pipeline.dsl.StageBuilder#embabelAgent(String workflowName)`
(new) — fluent DSL alias for `type=AGENT, runtime=EMBABEL,
embabelWorkflow=<name>`.

---

## Configuration Surface

New section (all optional):

```yaml
jaiclaw:
  gateway:
    auto-vision: true   # default — flip to false if your chat model is non-vision
```

Set `auto-vision: false` to fall back to 0.9.0 behaviour (router still
runs; no `Media` injection).

---

## Migration Notes

**Existing apps with a custom `AttachmentRouter`** (Sentinel-style,
jaiclaw-event-agent, etc.):

1. Change the method signature:
   `void route(...)` → `RouterResult route(...)`.
2. Add `return RouterResult.none();` at the end of every code path.
3. (Optional) For PDF extractors / image classifiers that produce a
   summary, use `return RouterResult.annotated(text);` instead — the
   framework will prepend `text` to the user input that reaches the agent.

**Apps that want vision but use a non-vision model:**

Add `jaiclaw.gateway.auto-vision: false` to `application.yml`. Most
providers ignore unsupported media content blocks silently; a handful
hard-error. The flag is the safe escape hatch.

**Apps that have been declaring `@Bean ToolCallback`** beans alongside
manual `toolRegistry.registerAll(...)` calls in the same configuration
class: **delete the manual registration** — auto-discovery now handles
it, and leaving both will produce a fail-fast collision at startup.

---

## Dependency Updates

None in 0.9.1. Spring Boot stays at 3.5.15, Spring AI at 1.1.7, Netty
at 4.1.135.Final, Tomcat at 10.1.55.

---

## Bug Fixes

- **Image attachments no longer dropped at the gateway** — see
  `docs/issues/attachment-injection-gap.md` for the original diagnosis.

- **Per-agent `tools.allow` / `tools.deny` no longer silently dropped**
  under Spring's record-binding fallback — see
  `docs/issues/tool-allow-deny-env-fallback.md`.

---

## Security Fixes

- **Tool allow/deny lists now honored under record-binding fallback** —
  previously, when Spring's `@ConfigurationProperties` partially failed
  to bind the `jaiclaw.agent.agents.*` map, the
  `JaiClawAgentAutoConfiguration` fallback hard-coded
  `allow=[]` / `deny=[]`, exposing the full tool surface (including
  `shell_exec`, `claude_cli`, `file_read`, `file_write`) to a chat agent
  whose YAML intended a narrower allow-list. 0.9.1 reads both lists
  from `Environment` so the operator-declared policy is enforced. Apps
  whose YAML already declared `tools.allow:` or `tools.deny:` should
  re-verify their resolved tool set after the upgrade — see the
  `Tool policy — profile: X, allow: [...], deny: [...]` startup log.

The dependency CVE remediation pass from 0.9.0 still applies.

---

## Acknowledgements

Issue documentation that drove the vision-attachment fix:
[`docs/issues/attachment-injection-gap.md`](../docs/issues/attachment-injection-gap.md).

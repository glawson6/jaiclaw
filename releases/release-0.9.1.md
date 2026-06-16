# JaiClaw 0.9.1 Release Notes

**Release Date:** TBD

> 0.9.1 is a maintenance + small-feature release on top of 0.9.0. Four
> framework-level improvements ship together: **Spring bean auto-discovery
> for `ToolCallback`** (drop the manual `toolRegistry.registerAll(...)`
> boilerplate), **vision-attachment auto-injection** (image and PDF
> attachments now reach the LLM as native Spring AI `Media` content blocks),
> a **fix for per-agent `tools.allow` / `tools.deny` being silently
> dropped** under partial record-binding (functional + security fix), and
> **channel-aware rendering profiles** for `ascii_box` / `ascii_render`
> so agents can pick width + padding appropriate for the target client.
>
> The auto-discovery, vision-attachment, and ASCII-profile features are
> opt-out-by-default. The `tools.allow/deny` fix is purely a bug fix that
> makes existing YAML work as written. One of them (the
> `AttachmentRouter` SPI change) is a source-level break — see Breaking
> Changes below.

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

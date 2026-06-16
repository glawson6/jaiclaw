# JaiClaw 0.9.1 Release Notes

**Release Date:** TBD

> 0.9.1 is a maintenance + small-feature release on top of 0.9.0. Two
> framework-level improvements ship together: **Spring bean auto-discovery
> for `ToolCallback`** (drop the manual `toolRegistry.registerAll(...)`
> boilerplate) and **vision-attachment auto-injection** (image and PDF
> attachments now reach the LLM as native Spring AI `Media` content blocks).
>
> Both are opt-out-by-default, but one of them (the `AttachmentRouter` SPI
> change) is a source-level break — see Breaking Changes below.

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

---

## Security Fixes

None in 0.9.1. The dependency CVE remediation pass from 0.9.0 still
applies.

---

## Acknowledgements

Issue documentation that drove the vision-attachment fix:
[`docs/issues/attachment-injection-gap.md`](../docs/issues/attachment-injection-gap.md).

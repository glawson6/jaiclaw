# Migrating to JaiClaw 0.8.0

> **Status:** in-flight. The 0.8.0 release is the **hard-break** release in
> which Phase 3 (`docs/CODEBASE-ANALYSIS-2026-06-10.md` §3, §4.5) lands. This
> guide is written incrementally as each Phase 3 PR ships; expect more
> sections to appear as 0.8.0 takes shape on the `main` branch.

## Why 0.8.0 is a hard break

JaiClaw 0.7.x deferred several API ergonomics decisions
(`CODEBASE-ANALYSIS-2026-06-10.md` Phase 3). 0.8.0 lands them all in a
single release rather than deprecating-then-removing across two minors:

- Faster path to a 1.0 stability story (`docs/user/ROAD-TO-1.0.md`).
- Each Phase 3 change individually is small, but a deprecation window for
  each would mean a six-month tail of double-coded APIs.
- The early-adopter audience for 0.7.x is small enough that one
  migration pass is less painful than living with deprecations.

You will need to make code changes against this document when upgrading
from 0.7.x. The sections below are organized by Phase 3 PR; each names
the diff shape for the breaking change.

---

## P3.2 — Typed tool parameters (LANDED)

**What changed.** Three new types under `io.jaiclaw.core.tool.schema`
and `io.jaiclaw.core.tool.param`:

- `SchemaBuilder` + `FieldSpec` — fluent JSON Schema construction
  (replaces hand-written text blocks).
- `@ToolParameter` — annotation that marks a record component as a
  tool parameter and carries its description + required flag.
- `ParameterBinder` — binds an LLM-supplied `Map<String, Object>` to a
  parameter record (handles numeric coercion + string-as-number
  fallback for LLMs that emit everything as text).
- `SchemaInferrer` — walks a record's components and produces the
  JSON Schema string from the annotations.
- `TypedToolCallback<P>` — typed SPI variant of `ToolCallback`. Tool
  authors implement `execute(P, ToolContext)` with a typed record
  instead of `execute(Map, ToolContext)`. The `definition()` and the
  `Map`-bound `execute(...)` are default-method bridges.

A new built-in base class `io.jaiclaw.tools.builtin.AbstractTypedBuiltinTool`
mirrors `AbstractBuiltinTool` for the typed path.

**Who's affected.** Nobody who writes tools today is **forced** to
migrate — `ToolCallback` keeps working exactly as before. The typed
SPI is purely additive. Tool authors who want the boilerplate
reduction migrate one tool at a time.

**Optional migration:**

```diff
- public class WebFetchTool extends AbstractBuiltinTool {
-     private static final String INPUT_SCHEMA = """
-         {
-           "type": "object",
-           "properties": {
-             "url": {"type": "string", "description": "..."},
-             ...
-           },
-           "required": ["url"]
-         }""";
-
-     @Override
-     protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) {
-         String url = requireParam(parameters, "url");
-         int timeout = parameters.containsKey("timeout")
-                 ? ((Number) parameters.get("timeout")).intValue() : 30;
-         // ... pull every field via requireParam / cast / default chain
-     }
- }
+ public record WebFetchParams(
+     @ToolParameter(description = "The URL to fetch content from")
+     String url,
+     @ToolParameter(description = "Timeout in seconds (default 30)", required = false)
+     Integer timeout
+ ) {}
+
+ public class WebFetchTool extends AbstractTypedBuiltinTool<WebFetchParams> {
+     public WebFetchTool() {
+         super(WebFetchParams.class, "web_fetch", "...",
+                 ToolCatalog.SECTION_WEB,
+                 Set.of(ToolProfile.CODING, ToolProfile.FULL));
+     }
+
+     @Override
+     protected ToolResult doExecute(WebFetchParams params, ToolContext context) {
+         int timeout = params.timeout() != null ? params.timeout() : 30;
+         // ... no requireParam, no casts, no `parameters.containsKey` chains
+     }
+ }
```

**Built-in tools migrated in-repo:** `WebFetchTool` (the canonical
example). The other built-ins keep the legacy SPI in 0.8.0; they can
migrate one PR at a time in 0.8.x point releases.

**Limitations.** 0.8.0's `@ToolParameter` supports scalar types
(`String`, integer/long/short, double/float, `Boolean`) and `List`.
Nested records and `Map` parameters are out of scope — use the
legacy `ToolCallback` SPI with a hand-written schema for those.

**Detection guard.** New specs: `SchemaBuilderSpec` (13 cases),
`ParameterBinderSpec` (9), `SchemaInferrerSpec` (4),
`TypedToolCallbackSpec` (4).

---

## P3.3 — AbstractChannelAdapter (LANDED)

**What changed.** The 10 in-repo channel adapters (Telegram, Slack,
Discord, Email, SMS, Signal, Teams, Google Chat, LINE, Matrix) now
extend a new common base class
`io.jaiclaw.channel.AbstractChannelAdapter`. The base class final-
implements lifecycle (`start`/`stop`/`isRunning`), identity
(`channelId`/`displayName`/`platformLimits`), and outbound chunking
against the platform limit. Subclasses implement three hooks:
`doStart()`, `doStop()`, `doSend(ChannelMessage)`. Inbound dispatch
goes through the protected `dispatchInbound(...)` helper, which
no-ops cleanly before `start()` and after `stop()`.

A new `io.jaiclaw.channel.util.WebhookSignatureUtil` consolidates
HMAC-SHA256 signature verification (the per-channel duplicated code
that the audit flagged). Slack now delegates to
`WebhookSignatureUtil.verifySlackSignature(...)` and Telegram to
`WebhookSignatureUtil.constantTimeEquals(...)`; LINE keeps its
Base64 variant locally (format differs from the shared util).

**Who's affected.** Adopters who:

1. **Write a custom `ChannelAdapter`** — must extend
   `AbstractChannelAdapter` instead of implementing
   `ChannelAdapter` directly. The interface is unchanged but the
   recommended (and now-only) shape is to inherit from the base.
2. **Override `isRunning()` for compound semantics** — no longer
   possible because the base class final-implements it. The base's
   `isRunning()` reports the adapter's lifecycle state only.

**Required diff:**

```diff
- public class FooAdapter implements ChannelAdapter {
-     private final AtomicBoolean running = new AtomicBoolean(false);
-     private ChannelMessageHandler handler;
-
-     @Override public String channelId() { return "foo"; }
-     @Override public String displayName() { return "Foo"; }
-     @Override public PlatformLimits platformLimits() { return PlatformLimits.FOO; }
-     @Override public boolean isRunning() { return running.get(); }
-
-     @Override public void start(ChannelMessageHandler handler) {
-         this.handler = handler;
-         // ... platform-specific bootstrap
-         running.set(true);
-     }
-
-     @Override public void stop() {
-         running.set(false);
-         // ... platform-specific cleanup
-     }
-
-     @Override public DeliveryResult sendMessage(ChannelMessage message) {
-         // ... call platform API, possibly chunking manually
-     }
- }
+ public class FooAdapter extends AbstractChannelAdapter {
+     public FooAdapter(FooConfig config) {
+         super("foo", "Foo", PlatformLimits.FOO);
+         this.config = config;
+     }
+
+     @Override protected void doStart() {
+         // ... platform-specific bootstrap
+     }
+
+     @Override protected void doStop() {
+         // ... platform-specific cleanup
+     }
+
+     @Override protected DeliveryResult doSend(ChannelMessage chunk) {
+         // ... call platform API; chunking is already done
+     }
+ }
```

```diff
- // Inbound:
- if (handler != null) {
-     handler.onMessage(channelMessage);
- }
+ // Inbound:
+ dispatchInbound(channelMessage);
```

**Detection guard.** New `AbstractChannelAdapterSpec` (18 cases)
locks the base class behavior. `WebhookSignatureUtilSpec` (14 cases)
locks the signature helpers.

---

## P3.1 — Typed hook events (LANDED)

**What changed.** The plugin hook system migrated from a `HookName`
enum + `HookHandler<E, C>` pair to a sealed `HookEvent` hierarchy +
`HookHandler<E extends HookEvent>`. Dispatch is keyed by the event's
runtime class rather than the enum discriminator.

The 16 events (`AgentStartedEvent`, `AgentEndedEvent`, `LlmInputEvent`,
`LlmOutputEvent`, `BeforePromptBuildEvent`, `ToolCallStartedEvent`,
`ToolCallEndedEvent`, `BeforeCompactionEvent`, `AfterCompactionEvent`,
`SessionStartedEvent`, `SessionEndedEvent`, `BeforeResetEvent`,
`MessageReceivedEvent`, `MessageSendingEvent`, `MessageSentEvent`,
`BeforeModelResolveEvent`) live under
`io.jaiclaw.core.hook.event`. Each carries `agentId`, `sessionKey`, and
`timestamp` by contract, plus hook-specific fields.

**Required diff (every plugin):**

```diff
- api.on(HookName.BEFORE_AGENT_START, (event, ctx) -> {
-     String agentId = extractString(event, "agentId", "unknown");
-     String channelId = extractString(event, "channelId", null);
-     metrics.recordAgentInvocation(agentId, channelId);
-     return null;
- });
+ api.on(AgentStartedEvent.class, event -> {
+     metrics.recordAgentInvocation(event.agentId(), null);
+     return null;
+ });
```

```diff
- api.on(HookName.BEFORE_PROMPT_BUILD, (event, ctx) -> {
-     if (event instanceof String sp) return sp + EXTRA;
-     return event;
- });
+ api.on(BeforePromptBuildEvent.class, event ->
+     event.withSystemPrompt(event.systemPrompt() + EXTRA));
```

**Removed:**

- `io.jaiclaw.core.hook.HookName` — the enum is gone. Plugins that
  imported it must switch to the event-class API.
- The `Object context` argument from `HookHandler` — context fields
  (agentId, sessionKey, timestamp) now live on the event itself.
- The `extractString` / `extractBoolean` casting helpers the audit
  called out — typed accessors replace them.

**Migrations in-repo (PR-bundled):** `ObservabilityPlugin`,
`TelegramDocStorePlugin`, `TelegramSubscriptionPlugin`,
`ResearchAssistantPlugin`, `CodeScaffolderPlugin`,
`DataPipelinePlugin`, `IncidentResponderPlugin` — every plugin that
shipped with JaiClaw is on the typed API. External plugin authors get
the diff above as their migration template.

**Detection guard.** The new `HookEventTypesSpec` locks the 16-permit
sealed hierarchy and contract fields. A PR that drops or renames a
subtype fails this spec at build time.

---

## P3.4 — Auto-configuration split (LANDED)

**What changed.** The 782-LOC monolithic
`io.jaiclaw.autoconfigure.JaiClawAutoConfiguration` is replaced by seven
domain-scoped auto-configs:

```
JaiClawHttpAutoConfiguration         (HTTP proxy)
JaiClawTenantAutoConfiguration       (TenantProperties, TenantGuard)
JaiClawToolsAutoConfiguration        (ToolRegistry, exec policies, image/voice/video tools)
JaiClawPluginAutoConfiguration       (PluginRegistry, PluginDiscovery)
JaiClawMemoryAutoConfiguration       (memory search, circuit breaker, hook dispatcher)
JaiClawSkillsAutoConfiguration       (SkillLoader)
JaiClawAgentAutoConfiguration        (SessionManager, AgentRuntime, ChannelRegistry, tenant agent factories)
```

The DAG runs Http → Tenant → Tools → Memory → Skills → Plugin → Agent →
Gateway → Channels. `JaiClawAgentAutoConfiguration` is the **last** entry
in the chain — it has the same "after the whole framework" semantics that
the old monolith had at boot time.

**Who's affected.** Adopters whose code does any of:

1. `@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")`
2. `spring.autoconfigure.exclude=io.jaiclaw.autoconfigure.JaiClawAutoConfiguration`
3. `@Import(JaiClawAutoConfiguration.class)` (rare)
4. Imports the class directly anywhere in their codebase

**Required diff:**

```diff
- @AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")
+ @AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration")
```

```diff
- spring.autoconfigure.exclude=io.jaiclaw.autoconfigure.JaiClawAutoConfiguration
+ # Exclude the specific domain auto-config you want to suppress.
+ # See docs/INDEX.md for the full DAG.
+ spring.autoconfigure.exclude=io.jaiclaw.autoconfigure.JaiClawToolsAutoConfiguration,io.jaiclaw.autoconfigure.JaiClawMemoryAutoConfiguration
```

**Bean override migration.** Most adopters override individual beans (e.g., a
custom `SessionManager`); the bean names and types are unchanged so those
overrides keep working. Adopters who use `@AutoConfiguration(before = X)`
or `@AutoConfigureBefore(X)` against the monolith need to pick the
right downstream auto-config — usually `JaiClawAgentAutoConfiguration` if
they need the override to land before any framework-wired agent beans
are created.

**Detection guard.** The new `JaiClawAutoConfigurationOrderSpec` runs in
CI; a future PR that re-introduces the monolith or removes a domain
auto-config from the imports file fails fast there.

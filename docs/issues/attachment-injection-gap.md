> **Status:** Fixed in 0.9.1 (see `releases/release-0.9.1.md`).
> The framework now auto-injects image and PDF attachments as Spring AI
> `Media` content blocks on the agent's user message when
> `jaiclaw.gateway.auto-vision: true` (default). The `AttachmentRouter`
> SPI also evolved — its `route(...)` method now returns
> `RouterResult` so a custom router can optionally annotate the prompt
> the agent sees. This document is kept as historical context.

# Image attachments are lost before reaching the chat agent

**Module:** `jaiclaw-gateway`, `jaiclaw-agent`
**Severity:** functional gap (no error; data silently dropped)
**Affects:** any channel that delivers image attachments (Telegram photos/documents, Slack file uploads, Discord, email attachments) when the chat model is vision-capable.

## Summary

When a channel message arrives with one or more image attachments, the gateway hands the bytes to the configured `AttachmentRouter` (fire-and-forget) and then invokes the agent runtime with **only the text body** of the message. The attachment bytes never become a Spring AI `Media` on the agent's `UserMessage`, so the chat model never sees the image — even when the wired chat model fully supports vision (Claude Sonnet, GPT-4o, Gemini, etc.).

End user experience: send a photo to a Telegram bot with the caption *"what is this?"*, the bot replies as if it received only the caption. The image is dropped on the floor with an INFO log line.

## Reproduction

Trigger: any inbound `ChannelMessage` where `hasAttachments()` is true.

```
LoggingAttachmentRouter - Attachment received but no processing pipeline configured:
  file=cha-cha-jones-jun-16.jpg, type=IMAGE, size=205478 bytes, channel=telegram, tenant=none
```

The call path:

1. **`GatewayService.onMessage()`** — `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/GatewayService.java`

   ```java
   // line 172 — fire-and-forget routing
   attachmentRouter.route(payload, message, tc);
   // line 212 — only the text content reaches the agent
   agentRuntime.run(message.content(), context)
   ```

   Between lines 172 and 212 the `ChannelMessage` is **not** mutated. There is no path through which the attachment payload reaches the agent.

2. **`AttachmentRouter`** — `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/attachment/AttachmentRouter.java`

   ```java
   public interface AttachmentRouter {
       void route(AttachmentPayload attachment, ChannelMessage context, TenantContext tenant);
   }
   ```

   `void` return; no hook for influencing the message that will be sent to the agent.

3. **`LoggingAttachmentRouter`** — `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/attachment/LoggingAttachmentRouter.java`

   Default no-op implementation. Logs and discards. Selected automatically when no other `AttachmentRouter` bean is provided.

4. **`AgentRuntime.toSpringAiMessage()`** — `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/AgentRuntime.java` line 626

   ```java
   case io.jaiclaw.core.model.UserMessage u ->
       new org.springframework.ai.chat.messages.UserMessage(u.content());
   ```

   Constructs Spring AI's `UserMessage` with `content()` (text) only. Never sets `.media(...)`.

5. **`ExplicitToolLoop`** — `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/loop/ExplicitToolLoop.java` line 60

   ```java
   messages.add(new UserMessage(userInput));
   ```

   Same pattern.

A repo-wide search for `Media.builder(` or `import org.springframework.ai.content.Media` inside the agent and gateway modules returns zero hits.

## Why the "paste the URL in chat" workaround does not work

A natural workaround would be: have the user paste a public image URL in the chat, and let the LLM fetch it itself. This **does not work** today for these reasons:

1. **Anthropic Claude** does not auto-fetch HTTP image URLs that appear as plain text in the prompt. Claude (Sonnet, Opus, Haiku) only "sees" an image when it arrives as a `Media`-typed content block on a `UserMessage`. A URL in prose is just text. This is true for the official Anthropic API and for compatible proxies such as MiniMax's Anthropic endpoint.

2. **OpenAI GPT-4o / Vision** does support image URLs in the API, but only via `image_url` content parts on the request — Spring AI's `Media` builder still has to construct those parts from bytes or a URL it can resolve. A URL pasted into prompt prose is again just text from the model's perspective.

3. **Spring AI 1.1.7 `Media`** expects bytes or a Spring `Resource`. There is no public `Media.builder().url(String)` convenience that turns a remote URL into bytes server-side. The developer is expected to fetch the bytes themselves.

4. **The framework's `WebFetchTool`** (`core/jaiclaw-tools/src/main/java/io/jaiclaw/tools/builtin/WebFetchTool.java`) returns text/HTML. It does not surface image bytes to the agent's vision input, so even an agent that tool-called it could not turn the result into a `Media` attachment.

**Conclusion:** there is no useful URL-only workaround. The bytes must be fetched server-side and converted to `Media` for the model to ever see an image. Documenting this here so future readers don't burn time on it.

## Possible fixes

### (a) Auto-inject image attachments as `Media` on the `UserMessage` (recommended)

Path: in `GatewayService.onMessage()` between the routing call and the agent invocation, detect attachments whose MIME type starts with `image/` and convert each into a Spring AI `Media`. Pass them to the runtime as part of the message content rather than discarding them.

Pieces this needs:

- Way to know whether the active `ChatModel` is vision-capable. Today there is no `ChatModel` capability flag in Spring AI; a config flag (`jaiclaw.gateway.auto-vision: true`) is the pragmatic stand-in.
- `AgentRuntime.run(...)` signature that accepts media alongside the text body, or a new `agentRuntime.run(content, mediaList, context)` overload. The Spring AI builder pattern is `UserMessage.builder().text(text).media(media1, media2, ...).build()` — internal conversion can stay inside `AgentRuntime.toSpringAiMessage()`.
- PDFs and other non-image attachments continue down the existing `AttachmentRouter` path (text extraction first).

Pros: makes the gateway feel native for vision use cases — no per-app boilerplate; matches user expectation when a Telegram bot gets a photo.
Cons: provider-specific behavior on multimodal payloads (Anthropic vs OpenAI vs Gemini) needs a small compatibility test matrix.

### (b) Make `AttachmentRouter` able to enrich the prompt

Path: change the SPI from `void route(...)` to `Optional<String> annotate(...)` (or a richer `RouterResult`), and have `GatewayService` prepend any returned string to `message.content()` before calling the agent.

A custom router could then return something like:

```
[attachment:att-7c91 mime=image/jpeg name=cha-cha-jones.jpg]
```

…and the app's system prompt teaches the agent to call an `extract_image(attachment_id)` tool that fetches the cached bytes and runs them through a vision model under the app's control.

Pros: low blast radius — one SPI signature change, no agent-runtime surgery, app authors keep full control over what happens with image bytes. Works for non-vision chat models too.
Cons: still indirect (the agent is the orchestrator). Apps that want "just see the image" still need to assemble plumbing.

### (c) Wire `MediaAnalysisProvider` into the gateway

`extensions/jaiclaw-media/src/main/java/io/jaiclaw/media/MediaAnalysisProvider.java` already defines an async image/audio analysis SPI. Today nothing in `GatewayService` calls it. A real fix could: hand image attachments to the provider, await its text description, and inject that into the prompt.

Pros: clean separation — heavy work off the chat path, provider can be swapped (Claude vision, GPT-4o, a local VLM).
Cons: more new infra than the other two. Sync-ish await vs async-mailbox shape is its own design call.

## Recommended fix

Combine **(a)** and **(b)**:

- (a) by default for image attachments when `jaiclaw.gateway.auto-vision: true` AND the chat model is configured for a vision-capable provider. Users who chose a vision model get the obvious behavior with zero extra code.
- (b) as the lower-level SPI for everything else (PDFs, audio, app-specific routing, non-vision models). Apps that need explicit control opt in by providing an `AttachmentRouter` bean.

Defer (c) until there's a real second consumer of `MediaAnalysisProvider`; for v1 it adds infra weight without solving a problem (a) doesn't already address.

## Workarounds available today (no framework change)

Apps can ship a custom `AttachmentRouter` that:

1. Stashes the bytes in an in-process cache (e.g. `AttachmentCache` keyed by a generated id).
2. Triggers extraction directly — call the vision model from inside the router using Spring AI `Media` and the existing `ChatModel` bean.
3. Sends the result back through the channel using `channelRegistry.get(channelId).ifPresent(adapter -> adapter.sendMessage(outbound))` — same pattern used by `GatewayService.deliverErrorResponse()`.

This bypasses the chat agent entirely for the image-upload case. It works because the gateway *does* call the router with the full byte payload — only the connection back into the agent's prompt is missing. The `jaiclaw-event-agent` app is taking this route until (a)/(b) ship.

## Related code

- `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/GatewayService.java` — the bottleneck
- `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/attachment/AttachmentRouter.java` — the SPI to extend
- `core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/attachment/LoggingAttachmentRouter.java` — the current no-op default
- `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/AgentRuntime.java` (line 626 `toSpringAiMessage`) — where `Media` should be added
- `core/jaiclaw-agent/src/main/java/io/jaiclaw/agent/loop/ExplicitToolLoop.java` (line 60) — second place that needs the same change
- `extensions/jaiclaw-media/src/main/java/io/jaiclaw/media/MediaAnalysisProvider.java` — pre-existing SPI option (c) would build on
- `extensions/jaiclaw-media/src/main/java/io/jaiclaw/media/MediaInput.java` — already knows `isImage()` from MIME
- Spring AI: `org.springframework.ai.content.Media`, `org.springframework.ai.chat.messages.UserMessage.builder()`

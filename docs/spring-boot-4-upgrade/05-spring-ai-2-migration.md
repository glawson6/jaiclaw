# 05 — Spring AI 1.1.7 → 2.0.0 Migration

> Spring AI 2.0.0 GA (2026-06-12) is the only Boot-4-compatible line. It requires Boot 4 + Framework 7 + Jackson 3 — so this phase is inseparable from Phases 1–2.
> Repo surface: **37 files** import `org.springframework.ai.*` (chat.model 30, chat.messages 13, vectorstore 8, chat.prompt 8, chat.client 6, tool 5+3, document 4, image 3, openai 3); **46 ymls** carry `spring.ai.*` properties.
> Sources: [Official upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html) · [2.0 GA announcement](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/) · [Embabel's migration wiki](https://github.com/embabel/embabel-agent/wiki/Spring-Boot-4---Spring-AI-2.0-Migration) (battle-tested consumer notes)

## 1. Property renames — 46 yml files

- **The `.options` segment is removed everywhere**: `spring.ai.anthropic.chat.options.model` → `spring.ai.anthropic.chat.model`; `spring.ai.openai.chat.options.temperature` → `spring.ai.openai.chat.temperature`. This hits nearly every example + app yml, CLAUDE.md's documented three-layer Embabel config, `start.sh` env plumbing, and OPERATIONS/OLLAMA-TUNING docs.
- `spring.ai.<provider>.chat.internal-tool-execution-enabled` **removed** → global `spring.ai.chat.client.tool-calling.enabled`.
- `spring.ai.ollama.chat.think-option` → `spring.ai.ollama.chat.think` (OLLAMA-TUNING-GUIDE + gemma4-local example).
- **Default temperature (0.7) removed** — provider-native defaults now apply. If deterministic-ish behavior was assumed anywhere (tests, pipelines), set temperature explicitly.
- Anthropic `maxTokens` default changed **500 → 4096** — review cost implications on default paths.

Mechanical approach: `git grep -n "options:" -- '*application*.yml'` under `ai:` blocks; Spring AI ships OpenRewrite migration recipes; the properties-migrator does NOT cover `spring.ai.*` (not Boot properties) — this rename is on us.

## 2. Provider starters (model-catalog, 20+ jaiclaw-starter-* poms)

| Provider | Spring AI 2.0 status | JaiClaw action |
|---|---|---|
| Anthropic | Rebuilt on official `com.anthropic:anthropic-java` SDK. `AnthropicApi` + nested types **removed**; `AnthropicChatModel` constructor removed → `AnthropicChatModel.builder()`; cache types moved out of `.api`; new `thinkingDisplay`, `service-tier`, built-in web-search tool | `jaiclaw-starter-anthropic`; **re-verify MiniMax-via-`api.minimax.io/anthropic` base-url routing through the OkHttp SDK stack** (different HTTP client, header handling `httpHeaders(Map)` → `customHeaders(Map)`) |
| OpenAI | Rebuilt on official `openai-java`. `completionsPath`/`embeddingsPath` **ignored** — bake into `baseUrl`; eager URL validation; may call `/v1/responses` | `jaiclaw-starter-openai` + every OpenAI-compatible portal profile (chutes, qwen-portal, minimax-portal OAuth logins in `start.sh`) — audit base-url construction |
| **Azure OpenAI** | `spring-ai-azure-openai` module **removed** (consolidated into `spring-ai-openai`) | rework `jaiclaw-starter-azure-openai` deps + config |
| **MiniMax** | Dedicated support **removed** ("use Anthropic support instead") | validates our existing Anthropic-endpoint routing; delete any `spring-ai-minimax`/`jaiclaw-starter-minimax` reliance on the dedicated starter — point it at the Anthropic path |
| **OCI GenAI** | **removed** (→ Oracle's spring-cloud-oracle) | `jaiclaw-starter-oci-genai`: switch to Oracle's module or drop for 1.0.0 (decision at execution) |
| Bedrock | Converse API remains; **requires explicit `ToolCallingManager` bean** in 2.0 (per Embabel notes) | `jaiclaw-starter-bedrock` auto-config: provide the bean |
| Vertex → Google GenAI | Google GenAI is the successor; embedding class package moved | `jaiclaw-starter-vertex-ai` / `jaiclaw-starter-gemini`: re-map artifacts |
| Ollama, Mistral, DeepSeek | remain | routine recompile + property rename |

Also removed: `spring-ai-spring-cloud-bindings`, Azure Cosmos + HanaDB vector stores. Module renames: `spring-ai-advisors-vector-store` → `spring-ai-vector-store-advisor`.

## 3. Tool calling — `SpringAiToolBridge` (core/jaiclaw-tools)

This is our dual-bridge (JaiClaw `ToolCallback` ↔ Spring AI `ToolCallback`) and the most load-bearing integration:

- **Tool execution moved out of chat models into the advisor chain**: `ToolCallingAdvisor` auto-registers on `ChatClient`; `internalToolExecutionEnabled` removed from all `ChatOptions`. If `AgentRuntime` drives tool loops itself (it does — JaiClaw runs its own agent loop), we must confirm whether we call tools via ChatModel (then options-level callbacks still pass through but execution routing changed) or ChatClient (advisor chain applies). Disable auto-advisor if we need manual control: `AdvisorParams.toolCallingAdvisorAutoRegister(false)`.
- `ToolExecutionEligibilityPredicate` → `ToolExecutionEligibilityChecker` (on the advisor).
- `toolNames()` / `SpringBeanToolCallbackResolver` (bare `Function` bean resolution) **removed** — explicit `ToolCallback` beans only. Fine for us — the bridge builds explicit callbacks — but audit examples using `.toolNames`.
- `ToolSpec` consumer API removed: `.tools(t -> t.callbacks(...))` → `.tools(cb).toolContext(Map.of(...))`.
- `ToolContext.TOOL_CALL_HISTORY` removed — grep; the trajectory recorder must not rely on it.
- `MethodToolCallbackProvider` now throws `IllegalArgumentException` (was `IllegalStateException`) — spec assertions may reference the old type.
- Tool schemas: `inputSchema()` returns `Map<String,Object>` (MCP side); verify `SchemaBuilder`/`TypedToolCallback` output feeds correctly.

## 4. ChatModel / ChatClient — decorators and BeanPostProcessors

We wrap and decorate `ChatModel` in several places; all touch changed API:

| Component | Change to absorb |
|---|---|
| `AuditingChatModelBeanPostProcessor` + `AuditingChatModel` (jaiclaw-compliance) | `ChatModel` options now immutable builders (`copy()`/`fromOptions()` → `mutate()`); `getDefaultOptions()` → `getOptions()`; `internalCall()/internalStream()` private. Re-verify idempotent wrapping + that `model.inference.request` audit events still see final options. **Also note tool execution may no longer pass through `ChatModel.call()` the same way (advisor routing)** — spec the audit event coverage for tool-loop calls. |
| `BaaWarningChatModelDecorator` (compliance) | same recompile surface |
| `MiniMaxThinkingFilterAutoConfiguration` (starter) | wraps all ChatModels via BeanPostProcessor to strip thinking blocks. Spring AI 2.0 Anthropic has first-class thinking config (`thinkingEnabled(budget)`/`thinkingDisabled()`, `thinkingDisplay`) — **evaluate deleting/simplifying the filter**; keep the documented `SmartInitializingSingleton` note for Embabel-internal models in sync ([02 §4](02-embabel-gate.md)) |
| `AgentRuntime` / `SystemPromptBuilder` (jaiclaw-agent, 9 files) | `Generation.getResult()` / `Message.getText()` now **nullable**; `Usage` non-null. Add null-handling; token-usage logging (the INFO log the CLAUDE.md token discipline relies on) must keep working |
| Memory (`VectorStoreSearchManager`) | `AbstractFilterExpressionConverter.doSingleValue()` now abstract — custom converters need the override; advisor precedence reordered (memory advisors now outside tool-calling); `PromptChatMemoryAdvisor` removed, conversation id per-call via `ChatMemory.CONVERSATION_ID` — we manage sessions ourselves, audit `jaiclaw-memory` + compaction for advisor usage |

## 5. MCP — hosting AND client sides

MCP moves into Spring AI core; **MCP Java SDK jumps to 2.x**; Streamable HTTP becomes the default transport.

- Artifact moves: `io.modelcontextprotocol.sdk:mcp-spring-webmvc` → `org.springframework.ai:mcp-spring-webmvc` (same for webflux); transports relocate to `org.springframework.ai.mcp.server.webmvc.transport.*`; annotations absorbed as `spring-ai-mcp-annotations` (`org.springframework.ai.mcp.annotation.*`).
- JaiClaw's own MCP hosting (`McpToolProvider`/`McpResourceProvider` → `McpServerRegistry` → `/mcp/*` REST + SSE + stdio): if implemented on the MCP Java SDK directly, we ride SDK 1.x→2.x (schema types: `McpSchema` interfaces **unsealed** — add `default` branches to exhaustive switches; `McpSchema.Resource` ctor gained `title`/`size`/`meta`; WebMvc transport headers now lowercase). If hand-rolled JSON-RPC, verify protocol-version compatibility with Streamable-HTTP-default clients — Claude Code's `.mcp.json` docs-server flow (CLAUDE.md § MCP Docs Server) must keep working.
- **Server-side tool input schema validation is ON by default** in 2.0 (`validateToolInputs(false)` to disable) — our messaging/calendar/kanban/docs MCP tools will now get strict-validated inputs; fix any loose schemas.
- Client customizers: `McpSyncClientCustomizer`/`McpAsyncClientCustomizer` → generic `McpClientCustomizer<Spec>`.
- Affected modules: `jaiclaw-gateway` (hosting), `jaiclaw-messaging`, `jaiclaw-docs`, `jaiclaw-calendar`, `jaiclaw-kanban` (tool provider), `jaiclaw-agentmind-memory` (MCP server), examples `mcp-docs-server`.

## 6. Misc API

- `JsonHelper` replaces `JsonParser`/`ModelOptionsUtils` JSON utilities (Jackson 3 under the hood); `BeanOutputConverter.postProcessSchema()` removed — grep `ModelOptionsUtils|BeanOutputConverter`.
- ChatClient is now the primary API, ChatModel a building block — no forced change, but new code (and docs) should prefer ChatClient.
- JDBC chat memory needs a `sequence_id` column migration — only if we use Spring AI's JDBC memory (we don't; we have our own SessionManager — verify).
- OpenRewrite: Spring AI publishes 2.0 migration recipes — run before hand-fixing.

## 7. Verification

```bash
./mvnw compile -pl :jaiclaw-tools,:jaiclaw-agent,:jaiclaw-memory,:jaiclaw-compliance,:jaiclaw-gateway -am
./mvnw test -pl :jaiclaw-tools -Dtest=ToolRegistrySpec -o          # bridge specs
./mvnw test -pl :jaiclaw-agent,:jaiclaw-memory,:jaiclaw-compliance -o
git grep -n "chat.options\|\.options\." -- '*application*.yml'      # 0 spring.ai .options remnants
# live smoke (needs keys): shell chat round-trip + one tool call + token INFO log sanity
./start.sh shell
```

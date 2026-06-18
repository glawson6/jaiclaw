# Issue: multiple Spring AI starters on the classpath → zero ChatModel beans built

## Summary

When an app ships **more than one** Spring AI provider starter
(e.g. `spring-ai-starter-model-anthropic` AND
`spring-ai-starter-model-openai`) so it can flip providers at runtime
without rebuilding, Spring Boot fails at startup with:

```
No qualifying bean of type 'org.springframework.ai.chat.model.ChatModel'
available: expected at least 1 bean which qualifies as autowire candidate.
Dependency annotations: {@org.springframework.beans.factory.annotation.Qualifier("…")}
```

…unless the operator knows to set `spring.ai.model.chat=<provider>`
explicitly. JaiClaw doesn't document or enforce this, and several
JaiClaw examples (`camel-pdf-filler-telegram`,
`camel-html-summarizer`, `canvas-dashboard`, etc.) read
`spring.ai.model.chat` themselves but never tell the operator to set
it.

## Root cause

Spring AI 1.1.x gates each provider's chat-model auto-config with:

```java
@ConditionalOnProperty(
    name = "spring.ai.model.chat",
    havingValue = "<provider>",          // "openai", "anthropic", "vertexai", …
    matchIfMissing = true                 // ← this is the trap
)
```

Verified against Spring AI 1.1.1:

- `OpenAiChatAutoConfiguration` → `havingValue="openai"`, `matchIfMissing=true`
- `AnthropicChatAutoConfiguration` → `havingValue="anthropic"`, `matchIfMissing=true`

The `matchIfMissing=true` setting was designed so single-starter apps
"just work" without configuration. But when both starters are
present:

- Spring evaluates BOTH conditions.
- With the property unset, BOTH `matchIfMissing` clauses fire and you
  get a `NoUniqueBeanDefinitionException` on the SAM `ChatModel`
  injection.
- With the property set to one provider, only THAT auto-config fires
  — but Spring's `@ConditionalOnProperty` does NOT honor relaxed
  binding the same way `@Value` does in every case, so
  `EVENT_AGENT_LLM_PROVIDER=openai` mapped through
  `application.yml` (`spring.ai.model.chat: ${EVENT_AGENT_LLM_PROVIDER}`)
  doesn't always reach the condition predicate cleanly.

Apps that thought they were following the JaiClaw multi-provider
pattern hit `No qualifying bean of type ChatModel` and have no signal
in the failure-analyzer report pointing them at `spring.ai.model.chat`.

## Repro

Any JaiClaw app with both `spring-ai-starter-model-anthropic` and
`spring-ai-starter-model-openai` on the classpath. We hit it in
`jaiclaw-event-agent` while wiring an OpenAI/Anthropic toggle: both
starters bundled in the image, `EVENT_AGENT_LLM_PROVIDER` env var
chosen at deploy time, `application.yml` chains it into
`spring.ai.model.chat: ${EVENT_AGENT_LLM_PROVIDER:anthropic}` and the
chat agent's `provider:` field. With `EVENT_AGENT_LLM_PROVIDER=openai`
set on the deployment, boot still failed with:

```
Error creating bean with name 'imperativeFlyerExtractionService' …:
Unsatisfied dependency expressed through method
'imperativeFlyerExtractionService' parameter 0:
No qualifying bean of type 'org.springframework.ai.chat.model.ChatModel'
available … qualifier "chatChatModel"
```

`spring.ai.model.chat=openai` was set on the deployment env, and a
local `@Bean(name="chatChatModel") @Primary
@ConditionalOnProperty(name = "event-agent.llm.provider",
havingValue = "openai") ChatModel chatChatModelOpenAi(...)` was
defined. Both starters resolved cleanly individually — the failure
only appears when both are wired together.

## Why this is JaiClaw's problem (not Spring AI's)

JaiClaw's positioning includes "swap providers at deploy time without
rebuilding" — the `agents.*.llm.provider` field on the agent config
encourages exactly this. But the framework:

1. doesn't ship a Spring AI provider-disambiguation helper,
2. doesn't document the `spring.ai.model.chat` requirement when more
   than one starter is on the classpath,
3. doesn't fail-fast with a clear message — apps hit `No qualifying
   bean` deep inside their own `@Configuration` and chase ghosts.

## Proposed fixes

Pick one — none invalidate the existing API.

### A. Auto-bridge `jaiclaw.agent.agents.<id>.llm.provider` → `spring.ai.model.chat`

In `JaiClawAgentAutoConfiguration` (or a sibling
`JaiClawAiProviderBridgeAutoConfiguration`): read the
default-agent's `llm.provider` at startup and inject
`spring.ai.model.chat` into the `Environment` as the
highest-priority `PropertySource`. Single source of truth — apps set
`EVENT_AGENT_LLM_PROVIDER=openai` (or whatever drives the agent
provider field) and Spring AI's auto-configs read it automatically.

Drawbacks: multi-agent apps (chat agent on openai, vision agent on
anthropic) can't be expressed this way; the bridge would have to pick
one. For v1 the default-agent's provider is correct.

### B. Fail-fast with a targeted bootstrap error

Add a `BeanFactoryPostProcessor` (or `EnvironmentPostProcessor`) in
`jaiclaw-spring-boot-starter` that runs early in the boot cycle:

```java
boolean hasOpenAi = ClassUtils.isPresent(
    "org.springframework.ai.openai.OpenAiChatModel", cl);
boolean hasAnthropic = ClassUtils.isPresent(
    "org.springframework.ai.anthropic.AnthropicChatModel", cl);
String selector = env.getProperty("spring.ai.model.chat");
if ((hasOpenAi && hasAnthropic) && selector == null) {
    throw new IllegalStateException(
        "Multiple Spring AI chat starters detected (openai, anthropic). "
      + "Set spring.ai.model.chat=<provider> or the jaiclaw agent will "
      + "fail to wire its ChatModel beans. See "
      + "jaiclaw/docs/issues/multi-provider-chatmodel-selection.md");
}
```

Drawbacks: only one of the helpful provider classes might be
deliberate. Better as a `WARN` with a clear pointer.

### C. Document the pattern + provide an example

Add to `jaiclaw-examples/` a `multi-provider-toggle` sample showing
exactly:

- both starters in `pom.xml`,
- `application.yml` chain
  (`spring.ai.model.chat: ${MY_APP_PROVIDER:anthropic}`),
- per-provider `api-key` blocks,
- a `@Configuration` exposing `chatChatModel` / `visionChatModel`
  qualifiers gated by the same property.

Lowest-effort framework change, highest education impact for the next
operator to hit this.

## Recommendation

**Ship A and C together.** A makes the common case (single-default-agent)
just work; C gives multi-agent users a worked example.

## Workaround used today

In `jaiclaw-event-agent`:

```yaml
spring:
  ai:
    model:
      chat: ${EVENT_AGENT_LLM_PROVIDER:anthropic}
    openai:
      api-key: ${OPENAI_API_KEY:}
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
```

```java
@Bean(name = "chatChatModel")
@Primary
@ConditionalOnProperty(
    name = "event-agent.llm.provider",
    havingValue = "anthropic", matchIfMissing = true)
public ChatModel chatChatModelAnthropic(
        @Qualifier("anthropicChatModel") ChatModel anthropic) {
    return anthropic;
}

@Bean(name = "chatChatModel")
@Primary
@ConditionalOnProperty(name = "event-agent.llm.provider", havingValue = "openai")
public ChatModel chatChatModelOpenAi(
        @Qualifier("openAiChatModel") ChatModel openAi) {
    return openAi;
}
```

Critical detail: `@Bean(name = "chatChatModel")` is mandatory — Spring
defaults the bean name to the method name, so without an explicit
name both factories produce beans named after their methods
(`chatChatModelAnthropic` / `chatChatModelOpenAi`), and the
`@Qualifier("chatChatModel")` injection elsewhere in the app finds
nothing. This is the subtle gotcha that cost the most time.

## Tracking

- Discovered: 2026-06-17 during `jaiclaw-event-agent` multi-provider toggle
- Workaround in: `jaiclaw-event-agent/event-agent-app/src/main/java/net/taptech/eventagent/app/config/MultiModelConfig.java`
  (`@Bean(name = "chatChatModel")` + `@ConditionalOnProperty`)
- Once a framework bridge ships, simplify `MultiModelConfig` accordingly.

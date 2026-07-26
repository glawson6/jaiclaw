# multi-provider-toggle

Boot-and-log fixture that exercises `LlmProviderBridgeEnvironmentPostProcessor`
end-to-end. Ships **both** `spring-ai-starter-model-openai` AND
`spring-ai-starter-model-anthropic` on the classpath — the exact
scenario that used to crash Spring Boot 4 apps with
`No qualifying bean of type ChatModel`.

Not intended for adopters. Consumed by the e2e-test skill's
Scenario 7.

## Problem

Before this fix, any Spring Boot 4 app that shipped two Spring AI
provider starters (so the operator could flip providers at deploy
time) crashed at startup because Spring AI's provider auto-configs
are gated with `matchIfMissing=true`. When `spring.ai.model.chat`
was unset, both providers' auto-configs fired and the resulting
`ChatModel` autowire was ambiguous. Adopters had to know to set
`spring.ai.model.chat=<provider>` — a discoverability problem
JaiClaw's own docs didn't call out.

## Solution

`jaiclaw-spring-boot-starter` now includes
`LlmProviderBridgeEnvironmentPostProcessor`, registered via
`META-INF/spring.factories`. It runs at env-post-process time
(before any autoconfig fires) and:

1. Skips if `spring.ai.model.chat` is already set explicitly.
2. Bridges `jaiclaw.agent.agents.<default>.llm.provider` (or the
   first non-null `agents.*.llm.provider` if the default has none)
   into `spring.ai.model.chat`.
3. Otherwise, if two or more Spring AI provider starters are on
   the classpath, throws `IllegalStateException` with a targeted
   error message naming both properties the operator can set.

## Build

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw package -pl :jaiclaw-example-multi-provider-toggle -am -DskipTests -o
```

## Run — four sub-scenarios

### 7a — Bridge fires (default-agent path)

```bash
JAICLAW_AGENT_AGENTS_DEFAULT_LLM_PROVIDER=openai \
    java -jar jaiclaw-examples/multi-provider-toggle/target/jaiclaw-example-multi-provider-toggle-*.jar
```

Expected boot log line:

```
MPT-BOOT-OK selector=openai chat-model-class=org.springframework.ai.openai.OpenAiChatModel chat-model-count=1
```

### 7b — Fail-fast fires (no bridge source, two starters)

```bash
java -jar jaiclaw-examples/multi-provider-toggle/target/jaiclaw-example-multi-provider-toggle-*.jar
```

Expected non-zero exit + stderr contains:

```
Multiple Spring AI chat provider starters detected on the classpath
(openai, anthropic) but no selector is set. Either:
  - Set `spring.ai.model.chat=<provider>` in application.yml or as an env var, OR
  - Configure `jaiclaw.agent.agents.<agentId>.llm.provider=<provider>` on any
    JaiClaw agent so the framework auto-bridges it.
```

### 7c — Explicit override wins

```bash
SPRING_AI_MODEL_CHAT=anthropic \
    JAICLAW_AGENT_AGENTS_DEFAULT_LLM_PROVIDER=openai \
    java -jar jaiclaw-examples/multi-provider-toggle/target/jaiclaw-example-multi-provider-toggle-*.jar
```

Expected:

```
MPT-BOOT-OK selector=anthropic chat-model-class=org.springframework.ai.anthropic.AnthropicChatModel chat-model-count=1
```

### 7d — Multi-agent fallback-scan

```bash
JAICLAW_AGENT_AGENTS_VISION_LLM_PROVIDER=anthropic \
    java -jar jaiclaw-examples/multi-provider-toggle/target/jaiclaw-example-multi-provider-toggle-*.jar
```

The default agent has no provider; the fallback-scan picks up
`vision`'s provider. Expected:

```
Bridged spring.ai.model.chat=anthropic from jaiclaw.agent.agents.vision.llm.provider (source: fallback-scan)
MPT-BOOT-OK selector=anthropic chat-model-class=org.springframework.ai.anthropic.AnthropicChatModel chat-model-count=1
```

## References

- `LlmProviderBridgeEnvironmentPostProcessor` in `jaiclaw-spring-boot-starter`
- `.claude/skills/e2e-test/SKILL.md` § Scenario 7

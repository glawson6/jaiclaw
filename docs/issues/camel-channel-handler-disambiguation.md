# Issue: jaiclaw-camel ObjectProvider<ChannelMessageHandler> ambiguous when filter present

## Summary

When `jaiclaw-camel` is on the classpath (e.g. via `jaiclaw-starter-pipeline`) **and** the application uses a gateway filter (e.g. `TelegramUserIdFilter` for per-user rate limiting and authorization), Spring boot fails at startup with:

```
APPLICATION FAILED TO START

Description:

A component required a single bean, but 2 were found:
    - gatewayService: defined by method 'gatewayService' in class path resource [io/jaiclaw/autoconfigure/JaiClawGatewayAutoConfiguration.class]
    - telegramUserIdFilter: defined by method 'telegramUserIdFilter' in class path resource [io/jaiclaw/autoconfigure/JaiClawChannelAutoConfiguration$TelegramAutoConfiguration.class]
```

## Root cause

`JaiClawCamelAutoConfiguration.gatewayLifecycleAdvisor(...)` (extensions/jaiclaw-camel/src/main/java/io/jaiclaw/camel/JaiClawCamelAutoConfiguration.java line 54) declares:

```java
ObjectProvider<ChannelMessageHandler> messageHandlerProvider
```

and calls `messageHandlerProvider.getIfAvailable()` at line 147. When both candidates exist (the gateway impl and the filter that wraps it), `ObjectProvider.getIfAvailable()` throws `NoUniqueBeanDefinitionException` because neither candidate is marked `@Primary`.

The design intent is clear from `FilteredGatewayLifecycle` (core/jaiclaw-gateway/src/main/java/io/jaiclaw/gateway/FilteredGatewayLifecycle.java line 51): channel adapters should receive **the filter** (which then delegates to the gateway). But the Camel autoconfig doesn't know that — it just asks Spring for "a ChannelMessageHandler" and trips on the multiplicity.

## Repro

Any Spring Boot app that:
1. Depends on `jaiclaw-spring-boot-starter` (provides `gatewayService` as a `ChannelMessageHandler`)
2. Has a Telegram channel configured (provides `telegramUserIdFilter` as a `GatewayMessageFilter extends ChannelMessageHandler`)
3. Depends on `jaiclaw-starter-pipeline` (transitively pulls in `jaiclaw-camel`)

This is the configuration `jaiclaw-event-agent` hit when adopting the pipeline framework.

## Workarounds (app-side)

Mark `telegramUserIdFilter` as `@Primary` via a `BeanFactoryPostProcessor`:

```java
@Bean
public static org.springframework.beans.factory.config.BeanFactoryPostProcessor
        telegramFilterAsPrimaryMessageHandler() {
    return beanFactory -> {
        String beanName = "telegramUserIdFilter";
        if (beanFactory.containsBeanDefinition(beanName)) {
            beanFactory.getBeanDefinition(beanName).setPrimary(true);
        }
    };
}
```

(Used today in `jaiclaw-event-agent`.)

## Proposed framework fixes

Pick one — both leave the API stable.

### A. Mark filters as `@Primary` in their auto-configurations

In `JaiClawChannelAutoConfiguration$TelegramAutoConfiguration`:

```java
@Bean
@Primary  // filter wraps the gateway; consumers wanting "the" handler want this one
public TelegramUserIdFilter telegramUserIdFilter(...) { ... }
```

Same for any other channel filter bean. Minimal change.

### B. Use a qualifier in the Camel autoconfig

Change `JaiClawCamelAutoConfiguration.gatewayLifecycleAdvisor` to inject the wrapped handler explicitly. Camel adapters care about the **start-of-chain** handler, which is what `FilteredGatewayLifecycle.messageFilter` already represents — so inject `FilteredGatewayLifecycle` (which has a getter for that handler) instead of asking the bean factory blindly. Keeps the Camel module ignorant of which filters exist.

```java
ObjectProvider<FilteredGatewayLifecycle> filteredGatewayProvider
// then:
ChannelMessageHandler handler = filteredGatewayProvider
        .map(FilteredGatewayLifecycle::messageFilter)
        .orElseGet(() -> rawGatewayServiceProvider.getIfAvailable());
```

### C. Defer to ObjectProvider.getIfUnique()

Replace `.getIfAvailable()` with `.getIfUnique()` and fall through cleanly when multiple beans exist — but this hides the intent. Probably less good than A or B.

## Recommendation

**Option A.** Smallest diff, most discoverable. Filters wrapping the gateway *are* the canonical handler from the perspective of any inbound channel — that's literally why `FilteredGatewayLifecycle` exists. Marking them primary aligns Spring's autowire defaults with the runtime semantics.

## Tracking

- Discovered: 2026-06-16 during `jaiclaw-event-agent` pipeline adoption
- Workaround documented in: `jaiclaw-event-agent/event-agent-app/src/main/java/net/taptech/eventagent/app/config/EventAgentBeans.java` (`telegramFilterAsPrimaryMessageHandler` bean)
- Once a framework fix ships, remove the BFPP from `EventAgentBeans`.

---

## Resolution

**Shipped in 0.9.1.** Option A landed: the `telegramUserIdFilter` `@Bean`
factory in `JaiClawChannelAutoConfiguration$TelegramAutoConfiguration`
now carries `@Primary`. Spring's autowire defaults now align with the
runtime semantics — channel adapters receive the filter (start-of-chain
handler), which delegates to `GatewayService` downstream.

`CamelChannelHandlerDisambiguationSpec` in `jaiclaw-spring-boot-starter`
locks the annotation via reflection so a future refactor can't silently
drop it.

`jaiclaw-event-agent` can remove the `telegramFilterAsPrimaryMessageHandler`
`BeanFactoryPostProcessor` from `EventAgentBeans` after upgrading.

# Apache Camel Integration — Design Document

**Status:** Design (no implementation yet)
**Date:** 2026-04-01
**Module:** `extensions/jaiclaw-camel`

---

## 1. Motivation

JaiClaw handles messaging via custom `ChannelAdapter` implementations — one per platform (Telegram, Slack, Discord, Email, SMS, etc.). Each adapter is hand-written. This works well for messaging platforms but leaves a gap for enterprise integration scenarios:

- **Data sources as triggers** — S3 file drops, Kafka topics, database change events, SAP IDocs, MQTT streams
- **Multi-agent pipelines** — chaining multiple JaiClaw agents where output from one feeds input to another, connected via configurable transport

Apache Camel provides 390+ pre-built connectors and a mature routing DSL. Rather than reimplementing connectors, JaiClaw can leverage Camel as a transport layer.

### Decision: Camel as a Channel

JaiClaw remains the orchestrator. Camel implements the `ChannelAdapter` SPI and acts as a transport layer. Existing channel adapters are unchanged.

```
                    ┌─────────────────────────────────────────┐
                    │              JaiClaw Gateway             │
                    │                                         │
  Telegram ────────►│  TelegramAdapter ──┐                    │
  Slack ───────────►│  SlackAdapter ─────┤                    │
  S3 (via Camel) ──►│  CamelChannelAdapter ──► GatewayService │
  Kafka (via Camel)►│  CamelChannelAdapter ──┘     │          │
                    │                         AgentRuntime    │
                    └─────────────────────────────────────────┘
```

---

## 2. Module Structure

```
extensions/jaiclaw-camel/
├── pom.xml
└── src/main/java/io/jaiclaw/camel/
    ├── CamelChannelAdapter.java            # Implements ChannelAdapter SPI
    ├── CamelChannelConfig.java             # Config record per channel instance
    ├── CamelChannelProperties.java         # @ConfigurationProperties(prefix = "jaiclaw.camel")
    ├── CamelMessageConverter.java          # Camel Exchange ↔ ChannelMessage conversion
    ├── PipelineEnvelope.java               # Pipeline metadata for multi-stage flows
    └── JaiClawCamelAutoConfiguration.java  # Auto-config, gated on CamelContext
```

**Starter:**

```
jaiclaw-starters/jaiclaw-starter-camel/
└── pom.xml    # Pulls jaiclaw-camel + camel-spring-boot-starter
```

### Dependencies

```xml
<camel.version>4.18.0</camel.version>

<!-- In jaiclaw-camel/pom.xml -->
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-channel-api</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.camel.springboot</groupId>
    <artifactId>camel-spring-boot-starter</artifactId>
    <version>${camel.version}</version>
</dependency>
```

Users add per-route component starters as needed (e.g., `camel-kafka-starter`, `camel-aws2-s3-starter`). The `jaiclaw-camel` module does not transitively pull any specific Camel component.

---

## 3. CamelChannelConfig

One config record per Camel-backed channel instance.

```java
package io.jaiclaw.camel;

/**
 * Configuration for a single Camel-backed channel adapter instance.
 *
 * @param channelId   unique channel identifier (e.g., "camel-s3", "camel-kafka-orders")
 * @param displayName human-readable label
 * @param accountId   account identifier for session key computation
 * @param outboundUri Camel endpoint URI for outbound messages (e.g., "kafka:results-topic")
 */
public record CamelChannelConfig(
        String channelId,
        String displayName,
        String accountId,
        String outboundUri
) {
    public CamelChannelConfig {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = "Camel: " + channelId;
        }
        if (accountId == null || accountId.isBlank()) {
            accountId = "default";
        }
    }
}
```

### YAML Configuration

```yaml
jaiclaw:
  camel:
    channels:
      - channel-id: camel-s3-inbox
        display-name: "S3 Document Inbox"
        account-id: doc-pipeline
        outbound-uri: "kafka:extracted-documents"
      - channel-id: camel-kafka-analysis
        display-name: "Kafka Analysis Consumer"
        account-id: doc-pipeline
        outbound-uri: "slack:#analysis-results"
```

### ConfigurationProperties

```java
package io.jaiclaw.camel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "jaiclaw.camel")
public record CamelChannelProperties(
        List<CamelChannelConfig> channels
) {
    public CamelChannelProperties {
        if (channels == null) channels = List.of();
    }
}
```

---

## 4. CamelChannelAdapter

Implements JaiClaw's `ChannelAdapter` SPI. Each YAML entry produces one adapter instance.

### Inbound Flow (Camel Route → JaiClaw)

```
[External System]                         [JaiClaw]

S3 bucket event ──► Camel Route           CamelChannelAdapter.start()
                         │                         │
                    from("aws2-s3:...")        handler.onMessage(ChannelMessage)
                         │                         │
                    .to("direct:jaiclaw-        GatewayService.onMessage()
                         inbound-camel-s3")        │
                                              AgentRuntime.run()
```

The adapter registers a `direct:jaiclaw-inbound-{channelId}` endpoint during `start()`. User-defined Camel routes send messages to this endpoint.

### Outbound Flow (JaiClaw → Camel Route)

```
AgentRuntime response
        │
GatewayService.deliverResponse()
        │
CamelChannelAdapter.sendMessage(ChannelMessage)
        │
CamelMessageConverter.toExchange()
        │
producerTemplate.send(outboundUri)    // e.g., "kafka:results-topic"
```

### Implementation

```java
package io.jaiclaw.camel;

import io.jaiclaw.channel.ChannelAdapter;
import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.channel.ChannelMessageHandler;
import io.jaiclaw.channel.DeliveryResult;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class CamelChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(CamelChannelAdapter.class);
    private static final String INBOUND_PREFIX = "direct:jaiclaw-inbound-";

    private final CamelChannelConfig config;
    private final ProducerTemplate producerTemplate;
    private final CamelContext camelContext;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ChannelMessageHandler handler;

    public CamelChannelAdapter(CamelChannelConfig config,
                               ProducerTemplate producerTemplate,
                               CamelContext camelContext) {
        this.config = config;
        this.producerTemplate = producerTemplate;
        this.camelContext = camelContext;
    }

    @Override
    public String channelId() {
        return config.channelId();
    }

    @Override
    public String displayName() {
        return config.displayName();
    }

    @Override
    public void start(ChannelMessageHandler handler) {
        this.handler = handler;
        try {
            String inboundEndpoint = INBOUND_PREFIX + config.channelId();
            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(inboundEndpoint)
                        .routeId("jaiclaw-" + config.channelId())
                        .process(exchange -> {
                            ChannelMessage msg = CamelMessageConverter.toChannelMessage(
                                    exchange, config.channelId(), config.accountId());
                            CamelChannelAdapter.this.handler.onMessage(msg);
                        });
                }
            });
            running.set(true);
            log.info("Started Camel channel adapter: {} (inbound: {})",
                    config.channelId(), inboundEndpoint);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to start Camel channel adapter: " + config.channelId(), e);
        }
    }

    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        if (config.outboundUri() == null || config.outboundUri().isBlank()) {
            return new DeliveryResult.Failure(
                    "NO_OUTBOUND_URI",
                    "No outbound URI configured for channel: " + config.channelId(),
                    false);
        }
        try {
            Exchange exchange = CamelMessageConverter.toExchange(message, camelContext);
            producerTemplate.send(config.outboundUri(), exchange);
            return new DeliveryResult.Success(message.id());
        } catch (Exception e) {
            log.error("Failed to send message via Camel channel {}: {}",
                    config.channelId(), e.getMessage(), e);
            return new DeliveryResult.Failure("CAMEL_SEND_ERROR", e.getMessage(), true);
        }
    }

    @Override
    public void stop() {
        try {
            String routeId = "jaiclaw-" + config.channelId();
            camelContext.getRouteController().stopRoute(routeId);
            camelContext.removeRoute(routeId);
            running.set(false);
            log.info("Stopped Camel channel adapter: {}", config.channelId());
        } catch (Exception e) {
            log.error("Failed to stop Camel channel adapter: {}",
                    config.channelId(), e);
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /** Returns the inbound endpoint URI for use in user-defined Camel routes. */
    public String inboundEndpoint() {
        return INBOUND_PREFIX + config.channelId();
    }
}
```

### How Users Wire Camel Routes

Users define standard Camel `RouteBuilder` beans that route external sources to the adapter's inbound endpoint:

```java
@Configuration
public class MyRoutes {

    @Bean
    RouteBuilder s3InboxRoute() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("aws2-s3://docs-inbox?deleteAfterRead=true")
                    .setHeader("JaiClawPeerId", simple("${header.CamelAwsS3Key}"))
                    .to("direct:jaiclaw-inbound-camel-s3-inbox");
            }
        };
    }
}
```

The `direct:jaiclaw-inbound-{channelId}` endpoint is the contract between user routes and JaiClaw.

---

## 5. CamelMessageConverter

Bidirectional conversion between Camel `Exchange` and JaiClaw `ChannelMessage`.

```java
package io.jaiclaw.camel;

import io.jaiclaw.channel.ChannelMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

import java.util.HashMap;
import java.util.Map;

public final class CamelMessageConverter {

    /** Header name for the peer ID (sender identifier). */
    public static final String HEADER_PEER_ID = "JaiClawPeerId";

    /** Header name for pipeline envelope metadata. */
    public static final String HEADER_PIPELINE = "JaiClawPipeline";

    /** Header prefix for JaiClaw-specific headers on outbound exchanges. */
    public static final String HEADER_CHANNEL_ID = "JaiClawChannelId";
    public static final String HEADER_SESSION_KEY = "JaiClawSessionKey";

    private CamelMessageConverter() {}

    /**
     * Convert a Camel Exchange (inbound) into a JaiClaw ChannelMessage.
     */
    public static ChannelMessage toChannelMessage(Exchange exchange,
                                                   String channelId,
                                                   String accountId) {
        Message camelMsg = exchange.getIn();
        String content = camelMsg.getBody(String.class);
        String peerId = camelMsg.getHeader(HEADER_PEER_ID, "anonymous", String.class);

        // Preserve all Camel headers as platformData for downstream access
        Map<String, Object> platformData = new HashMap<>(camelMsg.getHeaders());

        // Carry pipeline envelope if present
        PipelineEnvelope envelope = camelMsg.getHeader(HEADER_PIPELINE, PipelineEnvelope.class);
        if (envelope != null) {
            platformData.put("pipeline", envelope);
        }

        return ChannelMessage.inbound(
                exchange.getExchangeId(),
                channelId,
                accountId,
                peerId,
                content,
                platformData);
    }

    /**
     * Convert a JaiClaw ChannelMessage (outbound) into a Camel Exchange.
     */
    public static Exchange toExchange(ChannelMessage message, CamelContext context) {
        Exchange exchange = context.getEndpoint("direct:temp").createExchange();
        exchange.getIn().setBody(message.content());
        exchange.getIn().setHeader(HEADER_CHANNEL_ID, message.channelId());
        exchange.getIn().setHeader(HEADER_PEER_ID, message.peerId());
        exchange.getIn().setHeader(HEADER_SESSION_KEY, message.sessionKey("default"));

        // Forward pipeline envelope for multi-stage flows
        Object envelope = message.platformData().get("pipeline");
        if (envelope instanceof PipelineEnvelope pe) {
            exchange.getIn().setHeader(HEADER_PIPELINE, pe);
        }

        return exchange;
    }
}
```

### Attachment Handling

Binary attachments (e.g., S3 file content, email attachments) can be mapped to `ChannelMessage.Attachment` records. This is deferred to implementation — the initial version handles text content only. Attachment support will require:

- Extracting `byte[]` from `Exchange.getIn().getBody(byte[].class)` or Camel `DataHandler` attachments
- Detecting MIME type from Camel `Content-Type` header or file extension
- Populating `ChannelMessage.Attachment(name, mimeType, url, data)`

---

## 6. PipelineEnvelope

For multi-agent pipelines, a `PipelineEnvelope` record carries metadata through stages. This record lives in `jaiclaw-camel` (not `jaiclaw-core`) since it is Camel-specific.

```java
package io.jaiclaw.camel;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata envelope for multi-stage agent pipelines. Carried as a Camel
 * header ({@code JaiClawPipeline}) through each stage.
 *
 * @param pipelineId     unique pipeline execution ID
 * @param correlationId  ties back to the original triggering event
 * @param stageIndex     current stage (0-based)
 * @param totalStages    total stages in the pipeline
 * @param replyChannelId channel to deliver the final result to
 * @param replyPeerId    user to deliver the final result to
 * @param stageOutputs   accumulated outputs from completed stages
 */
public record PipelineEnvelope(
        String pipelineId,
        String correlationId,
        int stageIndex,
        int totalStages,
        String replyChannelId,
        String replyPeerId,
        List<String> stageOutputs
) {
    public PipelineEnvelope {
        if (stageOutputs == null) stageOutputs = List.of();
    }

    /** Create a new envelope for the next stage, appending current output. */
    public PipelineEnvelope nextStage(String currentOutput) {
        List<String> outputs = new ArrayList<>(stageOutputs);
        outputs.add(currentOutput);
        return new PipelineEnvelope(
                pipelineId, correlationId, stageIndex + 1, totalStages,
                replyChannelId, replyPeerId, List.copyOf(outputs));
    }

    /** Whether this is the final stage in the pipeline. */
    public boolean isLastStage() {
        return stageIndex >= totalStages - 1;
    }
}
```

---

## 7. AgentProcessor (Pipeline Glue)

For pipelines where stages are connected via Camel routes (not just `direct:`), an `AgentProcessor` provides a Camel-native `Processor` that invokes the JaiClaw agent synchronously within a route.

```java
package io.jaiclaw.camel;

import io.jaiclaw.channel.ChannelMessage;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Camel Processor that invokes a JaiClaw agent inline within a route.
 * Useful for pipelines where messages flow through Camel transport
 * between agent stages.
 *
 * <p>Usage in a RouteBuilder:
 * <pre>
 * from("kafka:stage-1-output")
 *     .process(agentProcessor)
 *     .to("kafka:stage-2-output");
 * </pre>
 */
public class AgentProcessor implements Processor {

    private final GatewayServiceAccessor gateway;
    private final String channelId;
    private final String accountId;

    public AgentProcessor(GatewayServiceAccessor gateway,
                          String channelId,
                          String accountId) {
        this.gateway = gateway;
        this.channelId = channelId;
        this.accountId = accountId;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        ChannelMessage inbound = CamelMessageConverter.toChannelMessage(
                exchange, channelId, accountId);
        String peerId = inbound.peerId();
        String content = inbound.content();

        // Synchronous agent invocation
        String response = gateway.handleSync(channelId, accountId, peerId, content);

        // Replace exchange body with agent response
        exchange.getIn().setBody(response);

        // Advance pipeline envelope if present
        PipelineEnvelope envelope = exchange.getIn().getHeader(
                CamelMessageConverter.HEADER_PIPELINE, PipelineEnvelope.class);
        if (envelope != null) {
            exchange.getIn().setHeader(
                    CamelMessageConverter.HEADER_PIPELINE,
                    envelope.nextStage(response));
        }
    }
}
```

`GatewayServiceAccessor` is a thin interface wrapping `GatewayService.handleSync()` to avoid a hard dependency on the gateway module:

```java
@FunctionalInterface
public interface GatewayServiceAccessor {
    String handleSync(String channelId, String accountId, String peerId, String content);
}
```

---

## 8. Auto-Configuration

```java
package io.jaiclaw.camel;

import io.jaiclaw.channel.ChannelRegistry;
import org.apache.camel.CamelContext;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "org.apache.camel.CamelContext")
@EnableConfigurationProperties(CamelChannelProperties.class)
public class JaiClawCamelAutoConfiguration {

    @Bean
    @ConditionalOnBean({CamelContext.class, ChannelRegistry.class})
    ApplicationRunner registerCamelChannels(
            CamelChannelProperties properties,
            CamelContext camelContext,
            ChannelRegistry channelRegistry) {
        return args -> {
            for (CamelChannelConfig config : properties.channels()) {
                CamelChannelAdapter adapter = new CamelChannelAdapter(
                        config,
                        camelContext.createProducerTemplate(),
                        camelContext);
                channelRegistry.register(adapter);
            }
        };
    }
}
```

Registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### Auto-Config Ordering

`JaiClawCamelAutoConfiguration` does NOT use `@AutoConfigureAfter(JaiClawGatewayAutoConfiguration.class)` because it depends on `ChannelRegistry` (created in `JaiClawAutoConfiguration`, phase 1), not on gateway-specific beans. The `@ConditionalOnBean(ChannelRegistry.class)` is sufficient. However, registration via `ApplicationRunner` means adapters are registered after all auto-configuration completes, so ordering is not a concern.

---

## 9. Example: S3 Document Pipeline

A concrete multi-agent pipeline: S3 file drop → extraction agent → Kafka → analysis agent → Slack notification.

### Pipeline Diagram

```
┌─────────────┐    ┌──────────────────┐    ┌───────────────────┐
│  S3 Bucket  │───►│  Camel Route     │───►│ CamelChannelAdapter│
│  (docs-in)  │    │  from("aws2-s3") │    │ "camel-extract"    │
└─────────────┘    └──────────────────┘    └────────┬──────────┘
                                                    │ handler.onMessage()
                                                    ▼
                                           ┌────────────────┐
                                           │ GatewayService  │
                                           │ → AgentRuntime  │
                                           │ (extraction     │
                                           │  agent)         │
                                           └────────┬───────┘
                                                    │ deliverResponse()
                                                    ▼
                                           ┌────────────────────┐
                                           │ CamelChannelAdapter │
                                           │ .sendMessage()      │
                                           │ outbound: kafka:    │
                                           │  extracted-docs     │
                                           └────────┬───────────┘
                                                    │
                                                    ▼
┌──────────────────┐    ┌───────────────────┐    ┌──────────────┐
│  Kafka Topic     │───►│ CamelChannelAdapter│───►│ GatewayService│
│ extracted-docs   │    │ "camel-analyze"    │    │ → AgentRuntime│
└──────────────────┘    └───────────────────┘    │ (analysis    │
                                                 │  agent)      │
                                                 └──────┬──────┘
                                                        │
                                                        ▼
                                                 ┌──────────────┐
                                                 │ Slack Channel │
                                                 │ #analysis    │
                                                 └──────────────┘
```

### Configuration

```yaml
jaiclaw:
  camel:
    channels:
      - channel-id: camel-extract
        display-name: "Document Extraction"
        account-id: doc-pipeline
        outbound-uri: "kafka:extracted-documents"
      - channel-id: camel-analyze
        display-name: "Document Analysis"
        account-id: doc-pipeline
        outbound-uri: "slack:#analysis-results"
```

### Route Definition

```java
@Configuration
public class DocumentPipelineRoutes {

    @Bean
    RouteBuilder documentPipelineRoutes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Stage 1: S3 file drops trigger the extraction agent
                from("aws2-s3://docs-inbox?deleteAfterRead=true")
                    .setHeader("JaiClawPeerId", simple("${header.CamelAwsS3Key}"))
                    .setBody(simple("Extract key information from: ${body}"))
                    .to("direct:jaiclaw-inbound-camel-extract");

                // Stage 2: Kafka consumer feeds the analysis agent
                from("kafka:extracted-documents?groupId=analysis")
                    .to("direct:jaiclaw-inbound-camel-analyze");
            }
        };
    }
}
```

### Agent Configuration

Each channel-id maps to an agent via session key routing. The extraction agent and analysis agent are separate JaiClaw agents with different system prompts and tool profiles, selected by the `account-id` + `channel-id` combination in the session key.

---

## 10. Multi-Tenancy Conformance

Camel channels follow the same tenant isolation rules as all other JaiClaw channels:

1. **Tenant resolution** — `GatewayService.onMessage()` calls `TenantResolver.resolve()` as usual. Camel headers can carry tenant identifiers (e.g., `JaiClawTenantId` header) for a future `CamelTenantResolver`.
2. **Session isolation** — Session keys include `channelId` and `accountId`, providing per-pipeline isolation.
3. **No shared mutable state** — Each `CamelChannelAdapter` instance is stateless beyond the Camel route registration. Pipeline state flows through `PipelineEnvelope` in message headers (not shared memory).
4. **Single-tenant compatibility** — Works identically in single-tenant mode (no tenant context, no filtering).

---

## 11. Testing Strategy

### Unit Tests (Spock)

```groovy
class CamelChannelAdapterSpec extends Specification {

    def "should convert Camel exchange to ChannelMessage"() {
        given:
        def exchange = // mock Exchange with body and headers

        when:
        def msg = CamelMessageConverter.toChannelMessage(exchange, "camel-test", "acct-1")

        then:
        msg.channelId() == "camel-test"
        msg.accountId() == "acct-1"
        msg.content() == "test content"
    }

    def "should send outbound message via ProducerTemplate"() {
        given:
        def producerTemplate = Mock(ProducerTemplate)
        def adapter = new CamelChannelAdapter(config, producerTemplate, camelContext)

        when:
        def result = adapter.sendMessage(outboundMessage)

        then:
        1 * producerTemplate.send("kafka:output-topic", _)
        result instanceof DeliveryResult.Success
    }

    def "should return Failure when no outbound URI configured"() {
        given:
        def config = new CamelChannelConfig("test", "Test", "acct", null)
        def adapter = new CamelChannelAdapter(config, producerTemplate, camelContext)

        when:
        def result = adapter.sendMessage(message)

        then:
        result instanceof DeliveryResult.Failure
        (result as DeliveryResult.Failure).errorCode() == "NO_OUTBOUND_URI"
    }
}
```

### Integration Tests

Camel provides `CamelTestSupport` and `@CamelSpringBootTest` for route testing. Since JaiClaw uses Spock, integration tests will use `@SpringBootTest` with an embedded Camel context:

```groovy
@SpringBootTest
class CamelPipelineIntegrationSpec extends Specification {

    @Autowired
    ProducerTemplate producerTemplate

    @Autowired
    ChannelRegistry channelRegistry

    def "should route message from direct endpoint through GatewayService"() {
        when:
        producerTemplate.sendBodyAndHeader(
            "direct:jaiclaw-inbound-camel-test",
            "Analyze this document",
            "JaiClawPeerId", "user-123")

        then:
        // verify GatewayService received the message
        // verify outbound was sent to configured URI
    }
}
```

### What Needs `CamelTestSupport`

- Route-level tests verifying EIP patterns (split, aggregate, error handling)
- Mock endpoint assertions (`MockEndpoint.expectedMessageCount()`)
- These can coexist with Spock via `@DelegateTo` or by calling Camel test utilities from within Spock specs

---

## 12. Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Camel dependency tree is large** | Each component starter brings transitive deps | Only `camel-spring-boot-starter` is required. Component starters are user-added per use case. The module itself stays thin. |
| **Camel 4.18 is last for Spring Boot 3.5** | Version bump needed when JaiClaw moves to Spring Boot 4 | Camel 4.19+ supports Spring Boot 4. Track in BOM. |
| **Pipeline complexity** | Fan-out, branching, retries need Camel EIPs (Saga, Aggregator) | Initial scope is linear pipelines only. Document Camel EIP escape hatches for advanced users. |
| **Testing paradigm mismatch** | Camel test utilities are JUnit-oriented | Use `@SpringBootTest` with Spock. Camel `MockEndpoint` works fine outside JUnit. |
| **Exchange body type conversion** | S3 returns `InputStream`, Kafka returns `byte[]` | `CamelMessageConverter` calls `getBody(String.class)` which uses Camel's built-in type converters. Document that custom converters may be needed for binary payloads. |
| **Error handling across stages** | Mid-pipeline failures lose context | `PipelineEnvelope.stageOutputs` preserves prior work. Dead letter channel (`direct:jaiclaw-error`) can capture failures with full envelope for retry/inspection. |

---

## 13. Verdict

**Worth it as an optional extension module.** The key insight is that Camel-as-a-channel is a thin integration layer (~6 classes) that unlocks Camel's entire connector ecosystem without JaiClaw taking on Camel as a core dependency.

- Additive, not invasive — existing adapters unchanged
- Optional — gated on `@ConditionalOnClass`, zero impact if not on classpath
- JaiClaw stays the brain — session management, tenant resolution, tool execution, prompt building all stay in JaiClaw
- Unlocks enterprise use cases — S3, Kafka, databases, SAP, MQTT all become AI agent triggers

---

## 14. Files to Create (When Implemented)

| File | Location | Purpose |
|------|----------|---------|
| `pom.xml` | `extensions/jaiclaw-camel/` | Module with `camel-spring-boot-starter` dependency |
| `CamelChannelAdapter.java` | `io.jaiclaw.camel` | Implements `ChannelAdapter` SPI |
| `CamelChannelConfig.java` | `io.jaiclaw.camel` | Config record per channel instance |
| `CamelChannelProperties.java` | `io.jaiclaw.camel` | `@ConfigurationProperties(prefix = "jaiclaw.camel")` |
| `CamelMessageConverter.java` | `io.jaiclaw.camel` | Exchange ↔ ChannelMessage conversion |
| `PipelineEnvelope.java` | `io.jaiclaw.camel` | Pipeline metadata record |
| `AgentProcessor.java` | `io.jaiclaw.camel` | Camel Processor wrapping GatewayService |
| `GatewayServiceAccessor.java` | `io.jaiclaw.camel` | Functional interface for sync agent calls |
| `JaiClawCamelAutoConfiguration.java` | `io.jaiclaw.camel` | Auto-config gated on CamelContext |
| `AutoConfiguration.imports` | `META-INF/spring/` | Registers auto-config class |
| `pom.xml` | `jaiclaw-starters/jaiclaw-starter-camel/` | Convenience starter |
| `CamelChannelAdapterSpec.groovy` | `src/test/groovy/` | Spock unit tests |

## 15. Verification Checklist (When Implemented)

- [ ] `./mvnw compile -pl :jaiclaw-camel -am` succeeds
- [ ] Auto-config loads only when `camel-spring-boot-starter` is on classpath
- [ ] `direct:jaiclaw-inbound-{channelId}` endpoint is created on `start()`
- [ ] Inbound Camel exchange converts to `ChannelMessage` and reaches `GatewayService`
- [ ] Outbound `ChannelMessage` converts to Camel exchange and sends to `outboundUri`
- [ ] `PipelineEnvelope` propagates through multi-stage flow
- [ ] `stop()` removes the Camel route cleanly
- [ ] Multiple `CamelChannelAdapter` instances coexist (one per YAML entry)
- [ ] Zero impact on builds/tests when `jaiclaw-camel` is not on classpath
- [ ] Multi-tenancy: tenant context propagated through pipeline stages

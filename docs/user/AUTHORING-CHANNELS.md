# Authoring Channels

> **Audience:** anyone wanting to connect JaiClaw to a messaging
> platform that isn't yet covered by the bundled 11 channel adapters.
> Plus framework contributors curious about how the existing adapters
> are shaped.

A channel is a two-way bridge between an external messaging platform
(Telegram, Slack, etc.) and the JaiClaw agent runtime. Inbound: text
or attachments arrive → the channel translates them into
`ChannelMessage` → the gateway invokes the agent. Outbound: the agent
returns a response → the channel translates it back into the
platform's native message format → the platform delivers it.

> **Coming soon:** an `AbstractChannelAdapter` base class that
> consolidates the lifecycle, webhook registration, message chunking,
> and signature verification that every existing adapter re-implements.
> Tracked in the audit roadmap
> (`CODEBASE-ANALYSIS-2026-06-10.md` §3.3 / Phase 3 #5). Until it
> lands, this guide reflects the SPI as-is — a working channel runs
> 250–400 LOC.

## The SPI

```java
public interface ChannelAdapter {
    String channelId();
    void start();
    void stop();

    void send(ChannelMessage outbound) throws DeliveryException;

    boolean supports(Capability capability);
    Set<MessageType> supportedMessageTypes();

    void registerInboundHandler(Consumer<ChannelMessage> handler);
}
```

Seven methods, sealed message types. The shape is small; the work is
in the platform-specific translation.

## Anatomy of an existing adapter

Reading [`jaiclaw-channel-telegram`](../../channels/jaiclaw-channel-telegram/)
gives you the pattern. The pieces:

```
channels/jaiclaw-channel-telegram/
├── pom.xml
└── src/main/java/io/jaiclaw/channel/telegram/
    ├── TelegramChannelAdapter.java       (the ChannelAdapter implementation)
    ├── TelegramAutoConfiguration.java    (Spring auto-config)
    ├── TelegramProperties.java           (@ConfigurationProperties)
    ├── api/                              (REST client to Bot API)
    ├── webhook/                          (inbound webhook controller)
    ├── polling/                          (alternative: long-polling client)
    └── convert/                          (Telegram Update ↔ ChannelMessage)
```

Each existing adapter has roughly the same skeleton. The 250–400 LOC
range is roughly: 50 LOC adapter + 80 LOC config + 50 LOC HTTP
client + 80 LOC inbound webhook + 80 LOC message conversion.

## Building a new channel — step by step

Say you want to add a hypothetical "ZeroChat" platform.

### 1. Create the module

```bash
mkdir -p channels/jaiclaw-channel-zerochat/src/main/java/io/jaiclaw/channel/zerochat
mkdir -p channels/jaiclaw-channel-zerochat/src/test/groovy/io/jaiclaw/channel/zerochat
```

POM template (model from an existing channel):

```xml
<parent>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-channels</artifactId>
    <version>0.7.1-SNAPSHOT</version>
</parent>

<artifactId>jaiclaw-channel-zerochat</artifactId>
<name>JaiClaw :: Channel :: ZeroChat</name>

<dependencies>
    <dependency>
        <groupId>io.jaiclaw</groupId>
        <artifactId>jaiclaw-channel-api</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- platform-specific SDK (or just use Java's HttpClient) -->
</dependencies>
```

Register the module in `channels/pom.xml` so the reactor picks it up.

### 2. Define properties

```java
@ConfigurationProperties(prefix = "jaiclaw.channels.zerochat")
public record ZeroChatProperties(
        boolean enabled,
        String apiKey,
        String apiBaseUrl,
        Mode mode,                // POLLING or WEBHOOK
        String webhookPath,
        boolean verifySignature
) {
    public enum Mode { POLLING, WEBHOOK }
}
```

The convention is `jaiclaw.channels.<name>.*`. Every existing channel
follows this; consistent property names matter for the docs.

### 3. Implement the adapter

```java
public class ZeroChatChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(ZeroChatChannelAdapter.class);

    private final ZeroChatProperties properties;
    private final ZeroChatApiClient apiClient;
    private final MessageConverter converter;
    private final List<Consumer<ChannelMessage>> handlers = new CopyOnWriteArrayList<>();

    public ZeroChatChannelAdapter(ZeroChatProperties properties, ZeroChatApiClient apiClient) {
        this.properties = properties;
        this.apiClient = apiClient;
        this.converter = new MessageConverter();
    }

    @Override public String channelId() { return "zerochat"; }

    @Override public void start() {
        if (properties.mode() == ZeroChatProperties.Mode.POLLING) {
            startPollingLoop();
        }
        // WEBHOOK mode is handled by ZeroChatWebhookController inbound;
        // no work required here.
    }

    @Override public void stop() { /* shutdown polling thread, etc */ }

    @Override
    public void send(ChannelMessage outbound) throws DeliveryException {
        try {
            apiClient.sendMessage(outbound.peerId(),
                    converter.toZeroChatPayload(outbound));
        } catch (IOException e) {
            throw new DeliveryException(outbound, e);
        }
    }

    @Override
    public boolean supports(Capability capability) {
        return switch (capability) {
            case TEXT, ATTACHMENT_IMAGE -> true;
            case VOICE -> false;
            default -> false;
        };
    }

    @Override
    public Set<MessageType> supportedMessageTypes() {
        return Set.of(MessageType.TEXT, MessageType.IMAGE);
    }

    @Override
    public void registerInboundHandler(Consumer<ChannelMessage> handler) {
        handlers.add(handler);
    }

    /** Called by ZeroChatWebhookController on every inbound webhook. */
    public void dispatchInbound(ChannelMessage message) {
        handlers.forEach(h -> h.accept(message));
    }
}
```

### 4. Auto-configuration

```java
@AutoConfiguration
@ConditionalOnClass(ZeroChatChannelAdapter.class)
@ConditionalOnProperty(name = "jaiclaw.channels.zerochat.enabled", havingValue = "true")
@EnableConfigurationProperties(ZeroChatProperties.class)
public class ZeroChatAutoConfiguration {

    @Bean
    public ZeroChatApiClient zeroChatApiClient(ZeroChatProperties properties) {
        return new ZeroChatApiClient(properties);
    }

    @Bean
    public ZeroChatChannelAdapter zeroChatChannelAdapter(
            ZeroChatProperties properties,
            ZeroChatApiClient apiClient,
            ChannelRegistry registry) {
        ZeroChatChannelAdapter adapter = new ZeroChatChannelAdapter(properties, apiClient);
        registry.register(adapter);
        return adapter;
    }

    @Bean
    @ConditionalOnProperty(name = "jaiclaw.channels.zerochat.mode", havingValue = "WEBHOOK")
    public ZeroChatWebhookController zeroChatWebhookController(
            ZeroChatChannelAdapter adapter,
            ZeroChatProperties properties) {
        return new ZeroChatWebhookController(adapter, properties);
    }
}
```

Register the auto-config in
`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
io.jaiclaw.channel.zerochat.ZeroChatAutoConfiguration
```

### 5. Inbound webhook controller (when in WEBHOOK mode)

```java
@RestController
public class ZeroChatWebhookController {

    private final ZeroChatChannelAdapter adapter;
    private final ZeroChatProperties properties;
    private final MessageConverter converter = new MessageConverter();

    @PostMapping("${jaiclaw.channels.zerochat.webhook-path:/webhook/zerochat}")
    public ResponseEntity<Void> handle(@RequestBody ZeroChatUpdate update,
                                        @RequestHeader("X-ZeroChat-Signature") String signature,
                                        HttpServletRequest request) {
        if (properties.verifySignature() && !signatureValid(request, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        ChannelMessage inbound = converter.fromZeroChatUpdate(update);
        adapter.dispatchInbound(inbound);
        return ResponseEntity.ok().build();
    }

    private boolean signatureValid(HttpServletRequest request, String signature) {
        // HMAC-SHA256 over the request body using properties.apiKey()
        // Use constant-time compare (MessageDigest.isEqual).
        // See SlackWebhookController for the canonical pattern.
        ...
    }
}
```

`verifySignature` defaults to **off**; production deployments turn it
on via `SPRING_PROFILES_ACTIVE=security-hardened`. Cribbing the
`SlackWebhookController` signature-verification flow is fine — its
spec covers eight HMAC cases.

### 6. Message conversion

```java
public class MessageConverter {

    public ChannelMessage fromZeroChatUpdate(ZeroChatUpdate update) {
        return new ChannelMessage(
                "zerochat",                                     // channel
                update.account().id(),                           // accountId
                update.from().userId(),                          // peerId
                update.message().text(),                         // content
                List.of(),                                       // attachments
                MessageType.TEXT,
                Instant.ofEpochSecond(update.timestamp())
        );
    }

    public ZeroChatPayload toZeroChatPayload(ChannelMessage outbound) {
        return new ZeroChatPayload(
                outbound.peerId(),
                chunkIfNeeded(outbound.content(), 4096)         // platform-specific cap
        );
    }
}
```

Each platform has its own message-size limit and chunking rules — the
audit's `AbstractChannelAdapter` Phase 3 work will pull this into the
base class so adapters declare the cap and inherit the chunking.

### 7. Tests

```groovy
class ZeroChatChannelAdapterSpec extends Specification {

    def "send delivers via API client"() {
        given:
        def apiClient = Mock(ZeroChatApiClient)
        def props = new ZeroChatProperties(true, "k", "https://api.zero", POLLING, "/wh", false)
        def adapter = new ZeroChatChannelAdapter(props, apiClient)

        when:
        adapter.send(new ChannelMessage("zerochat", "acct", "peer", "hi", List.of(),
                MessageType.TEXT, Instant.now()))

        then:
        1 * apiClient.sendMessage("peer", _)
    }

    def "dispatchInbound notifies all registered handlers"() {
        given:
        def adapter = makeAdapter()
        def received = []
        adapter.registerInboundHandler(received::add)

        when:
        adapter.dispatchInbound(new ChannelMessage("zerochat", "a", "p", "hello", List.of(),
                MessageType.TEXT, Instant.now()))

        then:
        received.size() == 1
        received[0].content() == "hello"
    }
}
```

Existing channel specs in `channels/jaiclaw-channel-*/src/test/groovy/`
are good reference patterns.

### 8. Documentation

Add a `docs/user/<NAME>-SETUP.md` next to
[`TELEGRAM-SETUP.md`](TELEGRAM-SETUP.md). Same shape:

- Prerequisites (where to register the bot, what credentials you need)
- Application config snippet
- How to verify (curl, sample message)
- Production hardening (signature verification on, rate limits)

Update [`INDEX.md`](../INDEX.md) so the new doc appears in the user
list.

## Multi-tenancy considerations

Inbound channel messages don't carry tenant context — the channel
adapter has no way to know who the caller's tenant is. Two patterns:

1. **Channel-as-tenant.** All messages on this adapter belong to one
   tenant. Configure the tenant explicitly in the adapter properties
   and set `TenantContextHolder` before dispatching the inbound
   message. Simple; appropriate for single-tenant deployments.
2. **Resolve from peer identity.** A `TenantResolver` SPI maps
   `peerId` → tenantId via a lookup (DB, config map, JWT in a custom
   header). Set the tenant context per message based on the resolver's
   answer. Required for multi-tenant deployments where one channel
   serves many tenants.

`CONTRIBUTING.md` § Multi-tenancy conformance check is the canonical
checklist. Any new channel adapter that holds shared state must scope
keys via `TenantGuard.resolveStorageKey(...)` or annotate it
`@TenantAgnostic` with rationale.

## What's coming

The `AbstractChannelAdapter` base class (`CODEBASE-ANALYSIS-2026-06-10.md` §3.3)
will reduce the 250–400 LOC overhead by providing default
implementations of:

- Lifecycle (`start`/`stop`)
- Inbound handler list (`registerInboundHandler`)
- Outbound chunking (platform-specific cap declared once)
- HMAC signature verification with constant-time compare

When that lands, existing adapters will be migrated to it as part of
the same PR, and this guide will get a simpler shape. Until then, this
is what new channels look like.

## Where to next

- [TELEGRAM-SETUP.md](TELEGRAM-SETUP.md) — operator-facing setup
  walkthrough for the most-used channel; the shape applies broadly
- [AUTHORING-TOOLS.md](AUTHORING-TOOLS.md) — how to add actions the
  agent can take
- [AUTHORING-SKILLS.md](AUTHORING-SKILLS.md) — how to teach the agent
  conventions
- Existing adapter source — reading `jaiclaw-channel-slack` and
  `jaiclaw-channel-discord` together is a good way to internalize the
  variation across platforms

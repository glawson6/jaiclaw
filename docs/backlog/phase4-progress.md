# Phase 4 — P2 Channels — Progress Tracker

**Started:** —
**Status:** Not Started
**Depends on:** Phase 1 complete (1.1 Auto-Reply Chunking for PlatformLimits)

## Work Items

**Revised approach:** Use existing SDKs and Camel components where available. Prefer official platform SDKs with Spring Boot starters over raw HTTP implementations. No Camel components exist for Line, Matrix, or Google Chat — so native adapters are needed, but official SDKs do most of the work.

### 4.1 Line Channel
- **Location:** New module `channels/jaiclaw-channel-line/`
- **Status:** NOT STARTED
- **Revised estimate:** 3-5 days (down from 1-2 weeks)
- **Approach:** Use **LINE Bot SDK for Java** (`com.linecorp.bot:line-bot-spring-boot-webmvc:7.5.0`) — official, actively maintained, Java 17+, with a Spring Boot starter that auto-configures webhook handling.
- **Library:** `com.linecorp.bot:line-bot-spring-boot-webmvc:7.5.0`
- **What the SDK provides (~85% coverage):**
  - Auto-configured webhook handling via Spring MVC
  - `MessagingApiClient` for outbound messages (reply, push)
  - Webhook signature verification (`X-Line-Signature` HMAC-SHA256)
  - Message type builders (text, image, video, audio, sticker, location, flex)
  - Event parsing (message, follow, unfollow, join, leave, postback)
- **What to build:**
  - [ ] `channels/jaiclaw-channel-line/pom.xml`
  - [ ] `LineAdapter.java` — `ChannelAdapter` wrapping `MessagingApiClient` (~150-200 lines)
  - [ ] `LineMessageMapper.java` — Map LINE events/messages to `ChannelMessage` (~100 lines)
  - [ ] `LineAutoConfiguration.java`
  - [ ] `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - [ ] Spock specs
  - [ ] Starter POM: `jaiclaw-starters/jaiclaw-starter-line/pom.xml`
  - [ ] Register module in `channels/pom.xml`
- **No need to build:** `LineApiClient`, `LineWebhookController`, signature verification — SDK handles all of this.
- **Configuration:**
  ```yaml
  line:
    bot:
      channel-token: ${LINE_CHANNEL_ACCESS_TOKEN}
      channel-secret: ${LINE_CHANNEL_SECRET}
  ```
- **Notes:** ~200-300 lines total. SDK does the heavy lifting.

### 4.2 Matrix Channel
- **Location:** New module `channels/jaiclaw-channel-matrix/`
- **Status:** NOT STARTED
- **Revised estimate:** 1-2 weeks (unchanged — weak SDK ecosystem)
- **Approach:** Native HTTP implementation using `java.net.http.HttpClient`. Java Matrix SDKs (Kamax, Cosium) are alpha-quality and unreliable. The Matrix Client-Server API is straightforward REST — long-poll sync + PUT to send messages.
- **No usable library.** Build directly against the Matrix Client-Server API.
- **Files to create:**
  - [ ] `channels/jaiclaw-channel-matrix/pom.xml`
  - [ ] `MatrixAdapter.java` — `ChannelAdapter` with long-poll sync loop
  - [ ] `MatrixApiClient.java` — REST client via `HttpClient` (~200 lines)
  - [ ] `MatrixConfig.java`
  - [ ] `MatrixMessageMapper.java`
  - [ ] `MatrixAutoConfiguration.java`
  - [ ] `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - [ ] Spock specs
  - [ ] Starter POM: `jaiclaw-starters/jaiclaw-starter-matrix/pom.xml`
  - [ ] Register module in `channels/pom.xml`
- **API details:**
  - Long-poll sync: `GET /_matrix/client/v3/sync?timeout=30000`
  - Send message: `PUT /_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}`
  - Message types: `m.text`, `m.image`, `m.file`, `m.notice`
- **Configuration:**
  ```yaml
  jaiclaw:
    channels:
      matrix:
        homeserver-url: ${MATRIX_HOMESERVER_URL}
        access-token: ${MATRIX_ACCESS_TOKEN}
        user-id: ${MATRIX_USER_ID}
        sync-timeout-ms: 30000
  ```
- **Notes:** ~400-500 lines. Most effort of the three channels.

### 4.3 Google Chat Channel
- **Location:** New module `channels/jaiclaw-channel-googlechat/`
- **Status:** NOT STARTED
- **Revised estimate:** 1 week (down from 1-2 weeks)
- **Approach:** Use **Google Cloud Chat client library** (`com.google.cloud:google-cloud-chat:0.55.0`). Handles auth and API calls via gRPC or REST.
- **Library:** `com.google.cloud:google-cloud-chat:0.55.0`
- **What the library provides (~70% coverage):**
  - Service account authentication (JSON key file)
  - Message CRUD operations
  - Space/room management
  - gRPC and REST transport options
- **What to build:**
  - [ ] `channels/jaiclaw-channel-googlechat/pom.xml`
  - [ ] `GoogleChatAdapter.java` — `ChannelAdapter` using Pub/Sub for inbound, Chat API for outbound (~200-250 lines)
  - [ ] `GoogleChatMessageMapper.java` (~100 lines)
  - [ ] `GoogleChatWebhookController.java` — HTTP endpoint for Pub/Sub push
  - [ ] `GoogleChatConfig.java`
  - [ ] `GoogleChatAutoConfiguration.java`
  - [ ] `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - [ ] Spock specs
  - [ ] Starter POM: `jaiclaw-starters/jaiclaw-starter-googlechat/pom.xml`
  - [ ] Register module in `channels/pom.xml`
- **Configuration:**
  ```yaml
  jaiclaw:
    channels:
      googlechat:
        project-id: ${GOOGLE_CHAT_PROJECT_ID}
        service-account-key-path: ${GOOGLE_SA_KEY_PATH}
        webhook-path: /webhook/googlechat
  ```
- **Notes:** ~250-350 lines. Google Cloud library handles auth complexity.

## Session Log

### Session 1 — 2026-05-28
- Revised all items to leverage existing libraries
  - 4.1 LINE: Official SDK with Spring Boot starter → 3-5 days (was 1-2 weeks)
  - 4.3 Google Chat: Google Cloud client library → 1 week (was 1-2 weeks)
  - 4.2 Matrix: Unchanged — no usable SDK, native HTTP required

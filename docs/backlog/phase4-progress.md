# Phase 4 — P2 Channels — Progress Tracker

**Started:** 2026-05-28
**Completed:** 2026-05-29
**Status:** COMPLETE
**Depends on:** Phase 1 complete (1.1 Auto-Reply Chunking for PlatformLimits)

## Work Items

**Revised approach:** Use existing SDKs and Camel components where available. Prefer official platform SDKs with Spring Boot starters over raw HTTP implementations. No Camel components exist for Line, Matrix, or Google Chat — so native adapters are needed, but official SDKs do most of the work.

### 4.1 Line Channel — COMPLETE
- **Location:** `channels/jaiclaw-channel-line/`
- **Status:** COMPLETE (16 tests)
- **Library:** `com.linecorp.bot:line-bot-messaging-api-client:9.15.0`, `line-bot-webhook:9.15.0`
- **Files created:**
  - [x] `channels/jaiclaw-channel-line/pom.xml`
  - [x] `LineConfig.java` — config record with builder, defaults, sender filter
  - [x] `LineAdapter.java` — `ChannelAdapter` with webhook processing, reply/push API, HMAC-SHA256 signature verification
  - [x] `LineMessageMapper.java` — maps LINE webhook events to `ChannelMessage`
  - [x] `LineAdapterSpec.groovy` — 16 tests
- **Auto-config:** `LineAutoConfiguration` in `JaiClawChannelAutoConfiguration`
- **Configuration:**
  ```yaml
  jaiclaw:
    channels:
      line:
        enabled: true
        channel-access-token: ${LINE_CHANNEL_ACCESS_TOKEN}
        channel-secret: ${LINE_CHANNEL_SECRET}
        allowed-senders: ""  # comma-separated user IDs, empty = allow all
  ```

### 4.2 Matrix Channel — COMPLETE
- **Location:** `channels/jaiclaw-channel-matrix/`
- **Status:** COMPLETE (20 tests)
- **Library:** None — native `java.net.http.HttpClient` implementation
- **Files created:**
  - [x] `channels/jaiclaw-channel-matrix/pom.xml`
  - [x] `MatrixConfig.java` — config record with builder, defaults, sender filter
  - [x] `MatrixApiClient.java` — REST client for Matrix C-S API (sync + sendMessage)
  - [x] `MatrixAdapter.java` — `ChannelAdapter` with virtual thread long-poll sync loop
  - [x] `MatrixMessageMapper.java` — extracts `m.room.message` events from sync response
  - [x] `MatrixAdapterSpec.groovy` — 14 tests
  - [x] `MatrixApiClientSpec.groovy` — 6 tests
- **Auto-config:** `MatrixAutoConfiguration` in `JaiClawChannelAutoConfiguration`
- **Configuration:**
  ```yaml
  jaiclaw:
    channels:
      matrix:
        enabled: true
        homeserver-url: ${MATRIX_HOMESERVER_URL}
        access-token: ${MATRIX_ACCESS_TOKEN}
        user-id: ${MATRIX_USER_ID}
        sync-timeout-ms: 30000
        allowed-senders: ""  # comma-separated user IDs, empty = allow all
  ```

### 4.3 Google Chat Channel — COMPLETE
- **Location:** `channels/jaiclaw-channel-googlechat/`
- **Status:** COMPLETE (15 tests)
- **Library:** Native HTTP — uses `java.net.http.HttpClient` for Google Chat REST API
- **Files created:**
  - [x] `channels/jaiclaw-channel-googlechat/pom.xml`
  - [x] `GoogleChatConfig.java` — config record with builder, defaults, sender filter
  - [x] `GoogleChatAdapter.java` — `ChannelAdapter` with webhook processing, Chat API outbound
  - [x] `GoogleChatMessageMapper.java` — maps Google Chat events to `ChannelMessage`
  - [x] `GoogleChatAdapterSpec.groovy` — 15 tests
- **Auto-config:** `GoogleChatAutoConfiguration` in `JaiClawChannelAutoConfiguration`
- **Configuration:**
  ```yaml
  jaiclaw:
    channels:
      google-chat:
        enabled: true
        project-id: ${GOOGLE_CHAT_PROJECT_ID}
        service-account-key-path: ${GOOGLE_SA_KEY_PATH}
        webhook-path: /webhooks/googlechat
        allowed-senders: ""  # comma-separated user IDs, empty = allow all
  ```

## Cross-Cutting Changes

- **PlatformLimits:** Added `LINE` (5000), `MATRIX` (65536), `GOOGLE_CHAT` (4096)
- **ChannelsProperties:** Added `LineProperties`, `MatrixProperties`, `GoogleChatProperties` records with builders
- **TenantChannelsConfig:** Added `LineChannelConfig`, `MatrixChannelConfig`, `GoogleChatChannelConfig` records
- **TenantAgentConfigService:** Updated `parseChannels()` to parse LINE, Matrix, Google Chat tenant configs
- **JaiClawChannelAutoConfiguration:** Added `LineAutoConfiguration`, `MatrixAutoConfiguration`, `GoogleChatAutoConfiguration`
- **POMs updated:** root pom.xml (dependencyManagement + properties), jaiclaw-bom, channels/pom.xml, jaiclaw-spring-boot-starter, jaiclaw-starter-gateway

## Test Summary

| Module | Spec Classes | Tests |
|--------|-------------|-------|
| jaiclaw-channel-line | LineAdapterSpec | 16 |
| jaiclaw-channel-matrix | MatrixAdapterSpec, MatrixApiClientSpec | 20 |
| jaiclaw-channel-googlechat | GoogleChatAdapterSpec | 15 |
| **Total new** | **4** | **51** |

Full test suite: BUILD SUCCESS — 0 failures, 0 errors across all modules.

## Session Log

### Session 1 — 2026-05-28
- Revised all items to leverage existing libraries
  - 4.1 LINE: Official SDK with Spring Boot starter → 3-5 days (was 1-2 weeks)
  - 4.3 Google Chat: Google Cloud client library → 1 week (was 1-2 weeks)
  - 4.2 Matrix: Unchanged — no usable SDK, native HTTP required

### Session 2 — 2026-05-29
- Implemented all three channels
  - Added PlatformLimits constants for LINE, Matrix, Google Chat
  - Added ChannelsProperties records for all three channels
  - Created jaiclaw-channel-line module (16 tests)
  - Created jaiclaw-channel-googlechat module (15 tests)
  - Created jaiclaw-channel-matrix module (20 tests)
  - Wired auto-configuration in JaiClawChannelAutoConfiguration
  - Updated all POMs (root, BOM, channels parent, starter, gateway starter)
  - Updated TenantChannelsConfig and TenantAgentConfigService for multi-tenancy
  - Full build + test suite passes (BUILD SUCCESS)

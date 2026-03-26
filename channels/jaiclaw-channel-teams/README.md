# JaiClaw :: Channel :: Microsoft Teams

Microsoft Teams channel adapter using the Bot Framework REST API. No MS Bot Framework SDK — pure REST, zero external dependencies beyond what JaiClaw already uses.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Microsoft Cloud                                 │
│                                                                         │
│  ┌──────────────┐    ┌───────────────────┐    ┌──────────────────────┐  │
│  │  Teams Client │───▶│ Bot Framework     │───▶│ Azure AD (login.    │  │
│  │  (user types  │    │ Channel Service   │    │ microsoftonline.com)│  │
│  │   a message)  │    │                   │    │                     │  │
│  └──────────────┘    │ - Routes messages  │    │ - Issues OAuth 2.0  │  │
│                      │ - Signs JWTs       │    │   tokens for        │  │
│                      │ - Exposes serviceUrl│   │   outbound calls    │  │
│                      └──────┬────────▲────┘    └──────────▲──────────┘  │
│                             │        │                    │             │
└─────────────────────────────┼────────┼────────────────────┼─────────────┘
                              │        │                    │
                    HTTP POST │        │ HTTP POST          │ POST
                    (Activity │        │ (reply Activity    │ client_credentials
                     + JWT)   │        │  + Bearer token)   │ grant
                              │        │                    │
┌─────────────────────────────┼────────┼────────────────────┼─────────────┐
│  JaiClaw Gateway              │        │                    │             │
│                              ▼        │                    │             │
│  ┌────────────────────────────────────┴────────────────────┴──────────┐  │
│  │                        TeamsAdapter                                │  │
│  │                                                                    │  │
│  │  ┌──────────────┐  ┌──────────────────┐  ┌─────────────────────┐  │  │
│  │  │TeamsJwt      │  │ Message Router   │  │ TeamsTokenManager   │  │  │
│  │  │Validator     │  │                  │  │                     │  │  │
│  │  │              │  │ message ──▶ agent │  │ Caches OAuth token  │  │  │
│  │  │ Validates    │  │ convUpdate ──▶log │  │ Refreshes 5 min     │  │  │
│  │  │ RS256 JWT    │  │ invoke ──▶ ack   │  │ before expiry       │  │  │
│  │  │ from JWKS    │  │                  │  │                     │  │  │
│  │  └──────────────┘  └────────┬─────────┘  └─────────────────────┘  │  │
│  │                             │                                      │  │
│  └─────────────────────────────┼──────────────────────────────────────┘  │
│                                │                                         │
│                                ▼                                         │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ AgentRuntime ──▶ LLM ──▶ Tools ──▶ Response ──▶ TeamsAdapter    │    │
│  │                                                  .sendMessage() │    │
│  └──────────────────────────────────────────────────────────────────┘    │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │ WebhookDispatcher                                                │    │
│  │   POST /webhook/teams ──▶ TeamsAdapter.handleWebhook()           │    │
│  └──────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────┘
```

### Component Roles

| Component | Responsibility |
|-----------|---------------|
| **Bot Framework Channel Service** | Microsoft's cloud service that bridges Teams clients and bot endpoints. Routes messages, signs outbound JWTs. |
| **Azure AD** | Issues OAuth 2.0 access tokens to the bot for authenticating outbound REST API calls. |
| **WebhookDispatcher** | JaiClaw's generic webhook router. Maps `POST /webhook/teams` to `TeamsAdapter.handleWebhook()`. |
| **TeamsJwtValidator** | Validates inbound JWT tokens using RSA public keys from the Bot Framework JWKS endpoint. |
| **TeamsTokenManager** | Obtains and caches Azure AD OAuth 2.0 tokens for outbound API calls. |
| **TeamsAdapter** | Orchestrates everything: registers webhook, validates JWTs, parses Activities, caches serviceUrls, sends replies. |

## Inbound Message Flow

When a user sends a message in Teams, here is the complete path through the system:

```
 User types       Bot Framework        JaiClaw Gateway          JaiClaw Agent
 in Teams         Channel Service      /webhook/teams         Runtime
    │                   │                    │                     │
    │  1. User sends    │                    │                     │
    │     message       │                    │                     │
    ├──────────────────▶│                    │                     │
    │                   │                    │                     │
    │                   │  2. HTTP POST      │                     │
    │                   │     Activity JSON  │                     │
    │                   │     + JWT in       │                     │
    │                   │     Authorization  │                     │
    │                   ├───────────────────▶│                     │
    │                   │                    │                     │
    │                   │                    │  3. Validate JWT    │
    │                   │                    │     (RS256 sig,     │
    │                   │                    │      aud, exp/nbf)  │
    │                   │                    │                     │
    │                   │                    │  4. Parse Activity  │
    │                   │                    │     - Extract text  │
    │                   │                    │     - Strip <at>    │
    │                   │                    │       mention       │
    │                   │                    │     - Cache         │
    │                   │                    │       serviceUrl    │
    │                   │                    │     - Check sender  │
    │                   │                    │       allowlist     │
    │                   │                    │                     │
    │                   │                    │  5. Create          │
    │                   │                    │     ChannelMessage   │
    │                   │                    │     .inbound()      │
    │                   │                    ├────────────────────▶│
    │                   │                    │                     │
    │                   │   6. HTTP 200 OK   │                     │
    │                   │◀───────────────────┤                     │
    │                   │                    │                     │
    │                   │                    │  7. Agent processes  │
    │                   │                    │     message, calls   │
    │                   │                    │     LLM + tools      │
    │                   │                    │                     │
```

### Inbound Processing Details

**Step 3 — JWT Validation** (`TeamsJwtValidator`):

```
TeamsJwtValidator
    │
    ├── Decode JWT header + payload (Base64url)
    │
    ├── Verify alg == RS256
    │
    ├── Verify aud == TEAMS_APP_ID
    │
    ├── Verify exp (+ 5 min clock skew tolerance)
    │
    ├── Verify nbf (- 5 min clock skew tolerance)
    │
    ├── Lookup RSA public key by kid
    │   │
    │   ├── Cache hit? ──▶ Use cached key (24-hour TTL)
    │   │
    │   └── Cache miss? ──▶ Fetch OpenID config
    │                       │
    │                       ├── GET https://login.botframework.com/
    │                       │       v1/.well-known/openidconfiguration
    │                       │
    │                       ├── Extract jwks_uri
    │                       │
    │                       ├── GET {jwks_uri}
    │                       │
    │                       └── Parse RSA keys from n/e components
    │                           Store in cache
    │
    └── Verify RS256 signature (java.security.Signature)
```

**Step 4 — Activity Routing** (by `type` field):

```
Activity.type
    │
    ├── "message"
    │   ├── from.role == "bot"?  ──▶ Drop (ignore own messages)
    │   ├── aadObjectId not in allowlist? ──▶ Drop
    │   ├── text empty? ──▶ Drop
    │   ├── Strip <at>BotName</at> prefix
    │   ├── Download file attachments (if any)
    │   └── Dispatch ChannelMessage to AgentRuntime
    │
    ├── "conversationUpdate"
    │   └── Log membersAdded (bot added to conversation)
    │
    ├── "invoke"
    │   └── Respond {"status": 200} (file consent, card actions)
    │
    └── (other)
        └── Ignore with debug log
```

## Outbound Message Flow

When the agent generates a response, here is the path back to Teams:

```
 JaiClaw Agent         TeamsAdapter          Azure AD              Bot Framework
 Runtime                                   (OAuth)               Channel Service
    │                     │                   │                       │
    │  1. Agent produces  │                   │                       │
    │     response        │                   │                       │
    ├────────────────────▶│                   │                       │
    │                     │                   │                       │
    │                     │  2. Need token?   │                       │
    │                     │     (cached token │                       │
    │                     │      expired or   │                       │
    │                     │      missing)     │                       │
    │                     │                   │                       │
    │                     │  3. POST          │                       │
    │                     │  client_credentials                      │
    │                     │  grant            │                       │
    │                     ├──────────────────▶│                       │
    │                     │                   │                       │
    │                     │  4. access_token  │                       │
    │                     │◀──────────────────┤                       │
    │                     │                   │                       │
    │                     │  5. POST {serviceUrl}/v3/conversations/  │
    │                     │     {conversationId}/activities/{replyTo} │
    │                     │     Authorization: Bearer {token}         │
    │                     │     Body: {"type":"message","text":"..."}  │
    │                     ├──────────────────────────────────────────▶│
    │                     │                   │                       │
    │                     │  6. 200 OK        │                       │
    │                     │     {"id":"..."}  │                       │
    │                     │◀──────────────────────────────────────────┤
    │                     │                   │                       │
    │  7. DeliveryResult  │                   │                       │
    │     .Success        │                   │                       │
    │◀────────────────────┤                   │                       │
    │                     │                   │                       │
```

### Outbound Processing Details

**Token Management** (`TeamsTokenManager`):

```
getAccessToken()
    │
    ├── Cached token valid (>5 min until expiry)?
    │   └── Yes ──▶ Return cached token
    │
    └── No ──▶ Acquire ReentrantLock
              │
              ├── Double-check (another thread may have refreshed)
              │
              └── POST https://login.microsoftonline.com/
                      botframework.com/oauth2/v2.0/token
                  │
                  ├── grant_type=client_credentials
                  ├── client_id=TEAMS_APP_ID
                  ├── client_secret=TEAMS_APP_SECRET
                  └── scope=https://api.botframework.com/.default
                  │
                  └── Cache token + expiresAt
```

**ServiceUrl Resolution**:

The `serviceUrl` is how Microsoft tells the bot where to send replies. It varies by region (e.g., `https://smba.trafficmanager.net/amer/`, `https://smba.trafficmanager.net/emea/`).

```
resolveServiceUrl(outboundMessage)
    │
    ├── Check message.platformData["serviceUrl"]
    │   └── Present? ──▶ Use it
    │
    └── Check serviceUrlCache[conversationId]
        └── Present? ──▶ Use it
            (cached from the most recent inbound Activity)
```

## Prerequisites

1. **Azure Bot registration** — Create a Bot resource in the [Azure Portal](https://portal.azure.com/#create/Microsoft.AzureBot)
2. **App ID & Secret** — From the bot's **Configuration** blade, note the Microsoft App ID and generate a client secret
3. **Messaging endpoint** — Set to `https://<your-domain>/webhook/teams` in the bot's Configuration blade
4. **Teams channel** — Enable the Microsoft Teams channel in the bot's **Channels** blade

### Azure Bot Setup Walkthrough

```
Azure Portal
    │
    ├── 1. Create Resource ──▶ "Azure Bot"
    │
    ├── 2. Configuration blade
    │       ├── Bot handle: your-bot-name
    │       ├── Microsoft App ID: (auto-generated or existing)
    │       │   └── Copy this ──▶ TEAMS_APP_ID
    │       ├── App password: click "Manage"
    │       │   └── New client secret ──▶ TEAMS_APP_SECRET
    │       └── Messaging endpoint:
    │           └── https://your-domain.com/webhook/teams
    │
    ├── 3. Channels blade
    │       └── Add "Microsoft Teams" channel
    │           └── Enable
    │
    └── 4. (Optional) Settings blade
            └── Tenant ID ──▶ TEAMS_TENANT_ID
                (for single-tenant restriction)
```

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `TEAMS_ENABLED` | Yes | `false` | Set to `true` to activate the Teams adapter |
| `TEAMS_APP_ID` | Yes | _(empty)_ | Microsoft App ID from your Azure Bot registration |
| `TEAMS_APP_SECRET` | Yes | _(empty)_ | Client secret generated for the App ID |
| `TEAMS_TENANT_ID` | No | _(empty)_ | Azure AD tenant ID — leave blank for multi-tenant bots, set to restrict to a single tenant |
| `TEAMS_SKIP_JWT_VALIDATION` | No | `false` | Skip inbound JWT validation (**testing only** — must be `false` in production) |
| `TEAMS_ALLOWED_SENDERS` | No | _(empty)_ | Comma-separated list of AAD Object IDs allowed to message the bot. Empty = allow everyone |

## YAML Configuration

These map to `jaiclaw.channels.teams.*` in `application.yml`:

```yaml
jaiclaw:
  channels:
    teams:
      enabled: ${TEAMS_ENABLED:false}
      app-id: ${TEAMS_APP_ID:}
      app-secret: ${TEAMS_APP_SECRET:}
      tenant-id: ${TEAMS_TENANT_ID:}
      skip-jwt-validation: ${TEAMS_SKIP_JWT_VALIDATION:false}
      allowed-senders: ${TEAMS_ALLOWED_SENDERS:}
```

## Minimal `.env` Example

```env
TEAMS_ENABLED=true
TEAMS_APP_ID=12345678-abcd-efgh-ijkl-123456789abc
TEAMS_APP_SECRET=your-client-secret-here
```

## Session Key Format

```
{agentId}:teams:{tenantId}:{conversationId}
```

| Component | Source | Example |
|-----------|--------|---------|
| `agentId` | JaiClaw agent config | `default` |
| `channelId` | Always `teams` | `teams` |
| `tenantId` (accountId) | `channelData.tenant.id` from Activity | `72f988bf-...` |
| `conversationId` (peerId) | `conversation.id` from Activity | `19:abc123...@thread.tacv2` |

This means each Teams conversation (1:1 chat, group chat, or channel thread) gets its own isolated agent session, scoped to the Azure AD tenant.

## Platform Data

Every inbound `ChannelMessage` carries these fields in `platformData`:

| Key | Description | Example |
|-----|-------------|---------|
| `activityId` | Bot Framework activity ID | `1234567890` |
| `serviceUrl` | Regional Bot Connector endpoint | `https://smba.trafficmanager.net/amer/` |
| `tenantId` | Azure AD tenant ID | `72f988bf-86f1-41af-91ab-...` |
| `conversationId` | Teams conversation ID | `19:abc123...@thread.tacv2` |
| `fromId` | Sender's Bot Framework user ID | `29:1abc...` |
| `fromName` | Sender's display name | `Jane Doe` |
| `aadObjectId` | Sender's Azure AD object ID | `a1b2c3d4-...` |

## Security

### JWT Validation (Inbound)

All inbound webhooks carry a `Bearer` JWT in the `Authorization` header, signed by the Bot Framework Channel Service. The adapter validates:

| Check | Detail |
|-------|--------|
| Algorithm | Must be `RS256` |
| Audience (`aud`) | Must match `TEAMS_APP_ID` |
| Expiry (`exp`) | Must not be expired (5-min clock skew tolerance) |
| Not-before (`nbf`) | Must be valid (5-min clock skew tolerance) |
| Signature | RSA signature verified against Bot Framework JWKS public keys |

JWKS keys are fetched from the Bot Framework OpenID configuration endpoint and cached for 24 hours. On unknown `kid`, keys are re-fetched automatically.

### OAuth Token (Outbound)

Outbound API calls authenticate with an Azure AD token:

| Parameter | Value |
|-----------|-------|
| Token endpoint | `https://login.microsoftonline.com/botframework.com/oauth2/v2.0/token` |
| Grant type | `client_credentials` |
| Scope | `https://api.botframework.com/.default` |
| Token lifetime | ~60 minutes (proactively refreshed 5 min before expiry) |
| Thread safety | `ReentrantLock` with double-check pattern |

### Sender Allowlist

Set `TEAMS_ALLOWED_SENDERS` to a comma-separated list of Azure AD Object IDs to restrict who can message the bot. When the list is empty (default), all users in tenants where the bot is installed can interact with it.

```env
# Only allow these two users
TEAMS_ALLOWED_SENDERS=a1b2c3d4-e5f6-7890-abcd-ef1234567890,b2c3d4e5-f6a7-8901-bcde-f12345678901
```

## Module Structure

```
jaiclaw-channel-teams/
├── pom.xml
├── README.md
└── src/
    ├── main/java/io/jaiclaw/channel/teams/
    │   ├── TeamsAdapter.java          Core ChannelAdapter implementation
    │   ├── TeamsConfig.java           Configuration record
    │   ├── TeamsJwtValidator.java      Inbound JWT validation (JDK RSA)
    │   └── TeamsTokenManager.java      Outbound OAuth 2.0 token cache
    └── test/groovy/io/jaiclaw/channel/teams/
        ├── TeamsAdapterSpec.groovy     15 specs (message flow, filtering, routing)
        └── TeamsTokenManagerSpec.groovy 3 specs (caching, refresh, errors)
```

## Design Decisions

- **No MS Bot Framework SDK** — The Java SDK is archived/EOL (Dec 2023). Pure REST matches all other JaiClaw channel adapters and avoids a dead dependency.
- **JDK-native JWT validation** — Uses `java.security.Signature` with RSA public keys reconstructed from JWKS `n`/`e` components. Zero external crypto libraries.
- **Webhook-only** — Unlike Telegram or Signal, Teams has no pull-based delivery model. The adapter is simpler as a result (no polling thread, no reconnect loop).
- **ServiceUrl caching** — Microsoft requires outbound replies to go to the `serviceUrl` from the inbound Activity (varies by region). The adapter caches this per `conversationId`.

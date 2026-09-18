# OIDC & Verified Identity — Implementation Plan

> **Renamed and resequenced.** This was "Logto Integration". Logto is now
> treated as **one implementation of an abstraction**, not the subject of the
> plan. Roughly two-thirds of what an earlier draft labelled "Logto-specific"
> — the channel-linking ceremony, `IdentityLink`, the GDPR wiring, the
> AgentMind key migration — is ordinary OAuth and belongs in neutral modules
> behind SPIs.
>
> **Order (Logto last):**
>
> | Part | Deliverable | Vendor-neutral? | Est. |
> |---|---|---|---|
> | **A** | Tenant-resolution remediation + API key store | yes | ✅ **shipped** |
> | **B** | `jaiclaw-security-oidc` — OAuth2 resource server | **yes** | ~9d |
> | **C** | MCP OAuth — RFC 9728 metadata + challenge | **yes** | ~6d |
> | **D** | Verified identity — linking ceremony, `IdentityLink`, GDPR, AgentMind keys | **yes** | ~14d |
> | **E** | Logto adapter — Management API client, webhook verifier, JWT customizer | no | ~7d |
>
> The first 29 days carry **no vendor commitment**. Parts B–D work against
> Keycloak, Okta, Auth0, or Entra ID equally.
>
> Section numbering below still reads "Phase 1/2/3" from the original draft;
> Phase 1 = Part B, Phase 3 = Part C, Phase 2 = Parts D+E split by the SPI
> boundary described in **Part D/E** at the end of this document.

*Created: September 17, 2026 · Targets: 1.3.0 (Phase 1) · 1.4.0 (Phase 2) · TBD (Phase 3)*

> **Companion documents**
> - Analysis and rationale: [LOGTO-OIDC-INTEGRATION-ANALYSIS](/Users/tap/dev/docs/jaiclaw/LOGTO-OIDC-INTEGRATION-ANALYSIS.md) (private)
> - **Prerequisite**: [TENANT-RESOLUTION-REMEDIATION](TENANT-RESOLUTION-REMEDIATION.md) — ships first, independently
> - Outbound provider OAuth (unrelated): [OAUTH-IMPLEMENTATION-PLAN](OAUTH-IMPLEMENTATION-PLAN.md)

---

## 0. Prerequisite and Sequencing

**Phase 1 depends on the remediation work landing first.** Specifically on
`JaiClawAuthentication` (remediation R1) and `SecurityContextTenantResolver`
(R2): the OIDC mode produces the same principal type, so tenant resolution and
every downstream consumer need no further change.

```
1.2.0   TENANT-RESOLUTION-REMEDIATION          (no Logto dependency)
          └─ JaiClawAuthentication
          └─ SecurityContextTenantResolver
          └─ JwtTenantResolver deleted
                    ↓
1.3.0   Phase 1 — jaiclaw-security-oidc        (standards only, any IdP)
                    ↓
1.4.0   Phase 2 — jaiclaw-identity-logto       (Logto-specific)
                    ↓
TBD     Phase 3 — MCP OAuth                    (gated on MCP spec)
```

Phases 1 and 2 are separately shippable. **Phase 1 contains no Logto-specific
code** and works against Keycloak, Okta, Auth0, or Entra ID — it retains full
value if the Logto decision is reversed.

---

## Phase 1 — `jaiclaw-security-oidc`

**Goal:** make JaiClaw a correct OAuth 2.0 / OIDC resource server.
**Target:** 1.3.0 · **Estimate:** ~2 weeks · **No Logto dependency.**

### 1.1 Why a separate module

`spring-boot-starter-oauth2-resource-server` pulls in `nimbus-jose-jwt` and the
Spring Security OAuth2 stack. Putting it in `jaiclaw-security` would place those
on every adopter's classpath, including single-user CLI deployments that will
never use OIDC. A separate opt-in module keeps the default footprint unchanged.

```
core/jaiclaw-security-oidc/
  pom.xml                       → depends on jaiclaw-security, jaiclaw-core,
                                  spring-boot-starter-oauth2-resource-server
  src/main/java/io/jaiclaw/security/oidc/
    JaiClawOidcAutoConfiguration.java
    OidcProperties.java
    JaiClawJwtAuthenticationConverter.java
    ScopeToolProfileMapper.java
    AudienceValidator.java
  src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Adopters add the dependency explicitly; it is **not** pulled by
`jaiclaw-spring-boot-starter`.

### 1.2 Configuration surface

```yaml
jaiclaw:
  security:
    mode: oidc
    oidc:
      issuer-uri: https://logto.example.com/oidc
      jwk-set-uri: https://logto.example.com/oidc/jwks   # optional; derived from issuer
      audience: https://jaiclaw.example.com/api          # our resource indicator
      tenant-claim: organization_id                      # Logto's org claim
      jws-algorithms: [ES384, RS256]
      required-scope: ""                                 # optional global gate
      scope-to-profile:
        "jaiclaw:tools:full": FULL
        "jaiclaw:tools:coding": CODING
        "jaiclaw:tools:messaging": MESSAGING
        "jaiclaw:tools:minimal": MINIMAL
```

> **Spring Boot 4 record-binder constraint.** `OidcProperties` and every nested
> record must expose **exactly one public constructor** (the canonical one).
> Any delegating or no-arg constructor must be `private`, or nested YAML values
> are silently dropped. See `JaiClawSecurityProperties` and
> `docs/user/VERSIONS.md:135`.

`OidcProperties` is a nested record on `JaiClawSecurityProperties`. Because that
record's canonical constructor is the only public one, adding a field means
updating the canonical signature and the `Builder` — the existing `private`
delegating overloads stay as they are.

### 1.3 The decoder — three non-obvious requirements

Each of these costs a day if discovered during integration rather than before.

```java
@Bean
@ConditionalOnMissingBean(JwtDecoder.class)
JwtDecoder jwtDecoder(JaiClawSecurityProperties props) {
    OidcProperties oidc = props.oidc();

    NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri(oidc.resolvedJwkSetUri())
            // (1) Logto's default keypair is EC secp384r1 → ES384, NOT RS256.
            //     Spring defaults to RS256 and will reject every token.
            .jwsAlgorithms(algs -> oidc.jwsAlgorithms()
                    .forEach(a -> algs.add(SignatureAlgorithm.from(a))))
            // (2) Access tokens carry `typ: at+jwt` (RFC 9068). Spring's default
            //     JOSE type verifier rejects it outright.
            .jwtProcessorCustomizer(p -> p.setJWSTypeVerifier(
                    new DefaultJOSEObjectTypeVerifier<>(
                            new JOSEObjectType("at+jwt"), JOSEObjectType.JWT, null)))
            .build();

    // (3) Audience validation — absent entirely from the current HMAC validator.
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtIssuerValidator(oidc.issuerUri()),
            new JwtTimestampValidator(),
            new AudienceValidator(oidc.audience())));

    return decoder;
}
```

**(4) A fourth requirement lives on the client side, not here.** Logto only mints
a token audienced to JaiClaw when the authorization/token request carries
`resource={indicator}` (RFC 8707). Spring Security has **no first-class support**
for that parameter — any browser or CLI login flow needs a custom
`OAuth2AuthorizationRequestResolver`. This is out of scope for Phase 1 (which is
resource-server only) but must be documented, because a resource server that
never receives correctly-audienced tokens looks broken for a non-obvious reason.

JWKS caching and rotation are handled by `NimbusJwtDecoder` itself — it refreshes
on unknown `kid`, which is what makes Logto's staged key rotation transparent.

### 1.4 The converter — producing `JaiClawAuthentication`

This is the seam with the remediation work. The converter turns a validated `Jwt`
into the same principal type the API-key and JWT filters produce:

```java
public class JaiClawJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final OidcProperties props;
    private final ScopeToolProfileMapper profileMapper;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String tenantId = jwt.getClaimAsString(props.tenantClaim());
        TenantContext tenant = (tenantId == null || tenantId.isBlank())
                ? null
                : new DefaultTenantContext(tenantId, tenantId);

        List<String> scopes = scopes(jwt);                       // space-delimited `scope`
        ToolProfile profile = profileMapper.map(scopes);         // null if unmapped

        return new JaiClawAuthentication(
                jwt.getSubject(),
                tenant,
                profile,
                AuthSource.OIDC,
                scopes.stream()
                      .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                      .toList());
    }
}
```

> **⚠️ Roles are not in Logto access tokens.** Only the space-delimited `scope`
> claim is. `roles` / `organizations` / `organization_roles` are ID-token and
> userinfo claims, and the extended ones are off unless enabled per-tenant. So
> `ScopeToolProfileMapper` maps **scopes**, not roles — do not plan on reusing
> `RoleToolProfileResolver` here. (If role-driven mapping is required, Phase 2's
> JWT customizer can inject roles into the access token; see §2.6.)

Unlike the existing `JwtAuthenticationFilter`, the converter does **not** reject a
token that lacks a tenant claim — it produces a principal with a null tenant and
lets `TenantGuard` decide. That preserves single-tenant deployments where a stock
IdP token carries no organization.

### 1.5 The filter chain

A fourth nested `@Configuration`, matching the existing three:

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "jaiclaw.security.mode", havingValue = "oidc")
@EnableWebSecurity
@EnableMethodSecurity
static class OidcSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain oidcFilterChain(HttpSecurity http,
                                        JwtDecoder decoder,
                                        JaiClawJwtAuthenticationConverter converter,
                                        ObjectProvider<RateLimitFilter> rateLimit)
            throws Exception {
        configureSecurityHeaders(http);
        HttpSecurity builder = http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/webhook/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/mcp/**").authenticated()
                .anyRequest().denyAll())
            .oauth2ResourceServer(o -> o.jwt(j -> j
                .decoder(decoder)
                .jwtAuthenticationConverter(converter)));
        // rate-limit filter appended as in the other chains
        return builder.build();
    }
}
```

The matcher set is deliberately identical to `api-key` and `jwt` modes — this
phase changes *how* callers authenticate, not *what* is protected.

Because the converter emits `JaiClawAuthentication`, an
`OncePerRequestFilter` populating `TenantContextHolder` / `ToolProfileHolder`
from the principal is shared with the other modes rather than written again.

### 1.6 Tasks

| # | Task | Est. |
|---|---|---|
| P1.1 | Module skeleton, POM, auto-config registration | 0.5d |
| P1.2 | `OidcProperties` (canonical-ctor discipline) + `JaiClawSecurityProperties` wiring | 0.5d |
| P1.3 | `JwtDecoder` bean — algs, `at+jwt` verifier, audience/issuer/timestamp validators | 1d |
| P1.4 | `AudienceValidator` | 0.25d |
| P1.5 | `ScopeToolProfileMapper` | 0.5d |
| P1.6 | `JaiClawJwtAuthenticationConverter` → `JaiClawAuthentication` | 0.5d |
| P1.7 | `OidcSecurityConfiguration` filter chain | 0.5d |
| P1.8 | Shared principal→holder filter (reused across modes) | 0.5d |
| P1.9 | Spock specs (§1.7) | 2d |
| P1.10 | `docs/user/OIDC-AUTHENTICATION.md` incl. the RFC 8707 caveat | 1d |
| P1.11 | Example app (`jaiclaw-examples/oidc-gateway`) with `allow-bundled: []` + `jaiclaw-maven-plugin` | 1d |
| P1.12 | Release notes, `CLAUDE.md` module table, `ARCHITECTURE.md` | 0.5d |

**≈ 9 days.**

### 1.7 Tests

Use a static JWKS + locally-signed tokens (Nimbus) — no live Logto required.

| Spec | Asserts |
|---|---|
| `OidcTokenAcceptedSpec` | ES384-signed token with correct `aud`/`iss` authenticates |
| `OidcWrongAudienceRejectedSpec` | Correct signature, wrong `aud` → 401 |
| `OidcWrongIssuerRejectedSpec` | Correct signature, wrong `iss` → 401 |
| `OidcExpiredTokenRejectedSpec` | `exp` in the past → 401 |
| `OidcTypHeaderSpec` | `typ: at+jwt` accepted; unexpected `typ` rejected |
| `OidcAlgorithmConfinementSpec` | Token signed with an algorithm outside `jws-algorithms` → 401; **`alg: none` → 401** |
| `OidcTenantFromClaimSpec` | `organization_id` becomes the `TenantContext` |
| `OidcNoTenantClaimSpec` | Token without the claim authenticates with a null tenant (SINGLE mode works) |
| `OidcScopeToProfileSpec` | `jaiclaw:tools:coding` → `CODING`; unmapped scope → configured default |
| `OidcJwksRotationSpec` | Unknown `kid` triggers a JWKS refresh and then validates |

### 1.8 Definition of done

- `mode=oidc` validates a real Logto-issued token end to end.
- The tenant on `/api/chat` comes from `organization_id`, via
  `SecurityContextTenantResolver`, with no code change in `jaiclaw-gateway`.
- Adding the module to an existing `api-key` deployment and **not** setting
  `mode=oidc` changes nothing.

---

## Phase 2 — `jaiclaw-identity-logto`

**Goal:** verified channel identity, policy-driven tenancy, lifecycle sync.
**Target:** 1.4.0 · **Estimate:** ~3 weeks · **Logto-specific.**

This is where the capability JaiClaw does not have today lands: **a verified
answer to "who is this person?" on the channel path.**

### 2.1 Module

```
extensions/jaiclaw-identity-logto/
  LogtoManagementClient.java          # M2M client_credentials → Management API
  LogtoProperties.java
  link/ChannelLinkService.java        # nonce issue/redeem
  link/ChannelLinkNonceStore.java     # SPI + in-memory + Redis impls
  link/ChannelLinkController.java     # /api/identity/link/callback
  webhook/LogtoWebhookController.java # signed lifecycle events
  webhook/LogtoSignatureVerifier.java # HMAC-SHA256, logto-signature-sha-256
```

### 2.2 Management API client

Authenticate as an M2M application:

```
POST {endpoint}/oidc/token
  grant_type=client_credentials
  resource=https://default.logto.app/api      ← identifier, NOT a reachable URL
  scope=all
```

The resource indicator is literally `https://default.logto.app/api` for a
single-tenant OSS install. The API itself is served at `{endpoint}/api/*`. The
token **must** carry the predefined scope `all` or every call 403s.

Used for: looking up users by identity, creating organizations, reading
organization membership and roles, managing JIT rules.

### 2.3 Channel linking ceremony

The core of Phase 2. One-time per (channel, peerId):

```
1. Unknown Telegram user messages the bot.
2. ChannelLinkService.issue(channel, peerId) → nonce (short TTL, single use).
3. Agent replies with:
     {endpoint}/oidc/auth?client_id=…&redirect_uri={gateway}/api/identity/link/callback
       &response_type=code&scope=openid+profile+urn:logto:scope:organizations
       &resource={jaiclaw-indicator}&state={nonce}&code_challenge=…   ← PKCE
4. User authenticates with Logto (password / social / enterprise SSO).
5. Callback: validate state → redeem nonce → exchange code → read `sub`
   and `organization_id`.
6. IdentityLinkService.link(sub, channel, peerId) — now with a VERIFIED subject.
7. Subsequent messages: IdentityResolver.resolve(channel, peerId) → Logto sub.
```

**Reuse, don't rewrite.** `extensions/jaiclaw-identity/oauth/` already has
`PkceGenerator` (S256), `AuthorizationCodeFlow`, `OAuthCallbackServer`, and a
generic `OAuthProviderConfig`. They are currently used **outbound** (JaiClaw as a
client of LLM providers) but the flow code is provider-agnostic. Phase 2 adds a
`LogtoOAuthProvider` config and a **server-side** callback controller (the
existing loopback `OAuthCallbackServer` suits CLI, not the gateway).

**Nonce store must be tenant-scoped and single-use.** `ChannelLinkNonceStore` is
an SPI with an in-memory default and a Redis impl for multi-pod deployments —
following the `RedisSessionManager` pattern, including `TenantGuard.resolveStorageKey`.

**Lighter alternative for awkward channels.** Where a browser round-trip is poor
UX (SMS), Logto's `POST /api/verification-codes` + `/verify` verifies an email or
phone in-channel and matches it to a Logto user. Ship both; document the
trade-off (the code flow proves control of the identifier, not of the account).

### 2.4 Extending `IdentityLink`

```java
public record IdentityLink(
        String canonicalUserId,   // = Logto `sub` when verified
        String channel,
        String channelUserId,
        String tenantId,
        String issuer,            // NEW — nullable; Logto issuer URI
        Instant verifiedAt        // NEW — nullable; null = self-asserted (legacy)
) {
    /** @deprecated unverified link; prefer the 6-arg form. */
    @Deprecated(since = "1.4.0")
    public IdentityLink(String canonicalUserId, String channel,
                        String channelUserId, String tenantId) {
        this(canonicalUserId, channel, channelUserId, tenantId, null, null);
    }
}
```

`issuer != null && verifiedAt != null` is the predicate for "this link was proven,
not asserted" — which is what authorization decisions must key on.

> `IdentityLink` lives in `jaiclaw-core`. The existing 3-arg and 4-arg
> constructors stay for compatibility. This is **not** a
> `@ConfigurationProperties` record, so the canonical-constructor rule does not
> apply — ordinary overloads are fine here.

### 2.5 Per-user state migration (highest-risk item)

AgentMind tendencies are keyed `sha256(channelId:peerId)[0:16]`
(`TendenciesUserMessageInjector:135-145`); memory `PEER` scope uses the raw
`peerId`. Switching to a canonical subject **changes every key and orphans all
stored state.**

The seam is already anticipated in a comment at `:129-133` ("*when the soul
module's canonical resolver isn't on the classpath … falls back to* a
deterministic hash") but was never implemented.

**Implement it as a dual-read, never a cutover:**

```java
String canonical = resolver.resolve(channelId, peerId);   // Logto sub, if linked
String legacy    = userKeyFor(channelId, peerId);         // sha256 hash

// read: canonical first, fall back to legacy
// write: canonical when a VERIFIED link exists, else legacy
// optional: one-time copy legacy → canonical on first verified read
```

Gate behind `jaiclaw.agentmind.canonical-user-keys=false` (default off in 1.4.0)
so the migration is opt-in and reversible. A background copy job is deliberately
**out of scope** — lazy migration on read is sufficient and has no failure mode
that loses data.

### 2.6 JWT customizer artifact

Ship a documented script for `PUT /api/configs/jwt-customizer/access-token` that
injects a JaiClaw tool-profile scope derived from the user's organization roles —
the supported workaround for roles being absent from access tokens (§1.4).

The script's `context` carries `user.roles[]` with nested `scopes[]`,
`user.organizations[]`, and `user.organizationRoles[]`. It runs **locally in
`vm2`** on self-hosted (`isCloud === false`), with no Cloud dependency and no
quota gate.

Ship as `docs/logto/jwt-customizer-access-token.js` plus a `LogtoSetupCommand`
shell command that uploads it via the Management API.

### 2.7 Lifecycle webhooks

`LogtoWebhookController` at `POST /webhook/logto` (already `permitAll` and in the
api-key skip list — signature **is** the authentication):

| Event | JaiClaw action |
|---|---|
| `Organization.Membership.Updated` | Invalidate cached authority for affected users |
| `User.SuspensionStatus.Updated` | Revoke sessions; refuse new turns |
| `User.Deleted` | Trigger GDPR erasure via `DataSubjectErasureSpi` |
| `Organization.Created` / `Deleted` | Provision / retire tenant config |

Verify `logto-signature-sha-256` (HMAC-SHA256, lowercase hex over the raw body)
**before** parsing. Use `MessageDigest.isEqual`, and capture the raw body via
`ContentCachingRequestWrapper` — re-serializing changes the bytes and breaks the
HMAC.

> **⚠️ Deployment conflict — decide before building.** Logto's CIMD (needed for
> Phase 3) requires SSRF protection **active with an empty allowlist**. Webhook
> delivery to an in-cluster JaiClaw (`http://jaiclaw-gateway.svc:8080`) also
> traverses the SSRF guard and needs `SSRF_ALLOWED_ADDRESSES` — which **disables
> CIMD**. Resolution: route webhooks via a **public ingress hostname** so no
> private-address allowlist is required. Alternatives (two instances sharing a DB,
> or polling instead of webhooks) are materially worse.

### 2.8 Wire identity into GDPR

`dataSubjectId` is currently the raw `peerId`
(`DataSubjectErasureSpi:36-38`), so the same human on two channels is two data
subjects and an erasure satisfies only one. `IdentityResolver` exists but is not
referenced anywhere in `jaiclaw-compliance`.

Resolve `dataSubjectId` through `IdentityResolver` in
`AggregateDataSubjectErasureSpi` and `AggregateDataSubjectExportService`, fanning
out to **all** linked channel ids for a canonical subject. Keep the raw-id path
for unlinked subjects.

### 2.9 Tasks

| # | Task | Est. |
|---|---|---|
| P2.1 | Module skeleton, `LogtoProperties`, auto-config | 0.5d |
| P2.2 | `LogtoManagementClient` (M2M, token cache, retry) | 1.5d |
| P2.3 | `ChannelLinkNonceStore` SPI + in-memory + Redis | 1d |
| P2.4 | `ChannelLinkService` + `ChannelLinkController` (PKCE, state) | 2d |
| P2.5 | `IdentityLink` `(issuer, verifiedAt)` + store/serialization migration | 1d |
| P2.6 | Verification-code linking alternative | 1d |
| P2.7 | Canonical user-key dual-read for tendencies + memory | 2d |
| P2.8 | JWT customizer script + `LogtoSetupCommand` uploader | 1d |
| P2.9 | Webhook controller + signature verifier + handlers | 2d |
| P2.10 | GDPR `IdentityResolver` wiring | 1d |
| P2.11 | Spock specs | 3d |
| P2.12 | Docs: `docs/user/LOGTO-SETUP.md`, identity docs update | 1.5d |
| P2.13 | Helm chart for Logto + Postgres (none ships upstream) | 1.5d |

**≈ 19 days.**

### 2.10 Definition of done

- A Telegram user completes the link ceremony and their subsequent messages
  resolve to a Logto `sub` with a **verified** `IdentityLink`.
- Tenant comes from the **user's** organization, not the bot token.
- `ToolProfile` comes from their organization roles, not the fail-open default.
- Suspending the user in Logto stops their next turn.
- GDPR export for that subject returns data from **both** linked channels.

---

## Phase 3 — MCP OAuth

**Goal:** per-user, scoped authorization for hosted MCP servers.
**Target:** TBD · **Estimate:** ~2 weeks · **Gated on the MCP authorization spec.**

### 3.1 JaiClaw must publish RFC 9728 itself

Logto implements **CIMD** (unregistered clients using an HTTPS URL as
`client_id`) but does **not** implement RFC 9728 Protected Resource Metadata —
verified absent. An MCP client discovers its authorization server by fetching
that document **from the resource server**, so JaiClaw must serve it:

```java
@GetMapping("/.well-known/oauth-protected-resource")
public Map<String, Object> protectedResourceMetadata() {
    return Map.of(
        "resource", props.resourceIndicator(),
        "authorization_servers", List.of(props.issuerUri()),
        "scopes_supported", props.supportedScopes(),
        "bearer_methods_supported", List.of("header"));
}
```

Plus `WWW-Authenticate: Bearer resource_metadata="…"` on 401 from `/mcp/**`. Both
the endpoint and its `permitAll` / api-key-skip entries must be added.

### 3.2 CIMD's hard limits — choose the right client type

Logto **overwrites** a CIMD client's declared metadata:

- `grant_types` forced to `['authorization_code', 'refresh_token']` — a CIMD
  client can **never** use `client_credentials`, device code, or token exchange.
- `response_types` forced to `['code']`.
- `scope` capped by a **tenant-wide** ceiling (`cimd_user_scopes`), since
  unregistered clients have no per-client consent record.

| MCP caller | Use | Why |
|---|---|---|
| Interactive, user-delegated (IDE, desktop agent) | **CIMD** | No pre-registration; user consents |
| Service-to-service, unattended | **M2M application** | Needs `client_credentials`, which CIMD forbids |

Enabling CIMD also requires `PATCH /api/configs/cimd` plus SSRF protection with
an empty allowlist — see the §2.7 conflict.

### 3.3 Per-tool scope enforcement (optional, larger)

Today `ToolProfile` is a single coarse enum; `ToolDefinition.isAvailableIn` is set
membership. Real least-privilege for MCP means per-tool scopes:

```java
public record ToolDefinition(..., Set<String> requiredScopes) { }
```

…filtered in `ToolRegistry.resolveForProfile`. This is a **core API change**
touching every tool module and **deserves its own design note** — do not fold it
into the auth migration. Phase 3 can ship with profile-level enforcement only.

### 3.4 Token exchange for delegated tool calls

Optional. Logto's `token_exchange` grant emits an `act: { sub }` actor claim,
letting a tool call carry the **user's** authority rather than the bot's. Requires
`allowTokenExchange` per app (default false; never available to third-party apps).
Note the response omits `issued_token_type` — don't assume strict RFC 8693.

### 3.5 Tasks

| # | Task | Est. |
|---|---|---|
| P3.1 | `/.well-known/oauth-protected-resource` + chain entries | 1d |
| P3.2 | `WWW-Authenticate` challenge on `/mcp/**` 401 | 0.5d |
| P3.3 | MCP-specific scope validation | 1.5d |
| P3.4 | CIMD enablement + ceiling config via Management API | 1d |
| P3.5 | Docs: CIMD-vs-M2M decision guide | 1d |
| P3.6 | Optional: token exchange for delegated calls | 2d |
| P3.7 | Specs + MCP-client interop testing | 3d |

**≈ 10 days**, excluding §3.3.

---

## Cross-Cutting Concerns

### Documentation

| Doc | Change |
|---|---|
| `CLAUDE.md` | Add both modules to the layout, module counts, dependency graph |
| `docs/dev/ARCHITECTURE.md` | Auth flow diagrams per mode |
| `docs/user/OPERATIONS.md` | `mode=oidc` operational guidance |
| `docs/user/PRODUCTION-DEPLOYMENT.md` | Extend §9 hardening with auth-strength flags |
| `docs/user/features/IDENTITY.md` | Linking ceremony; fix the stale storage-format example (missing `tenantId`) |
| `docs/user/LOGTO-SETUP.md` | **New** — end-to-end setup |
| `docs/user/OIDC-AUTHENTICATION.md` | **New** — provider-agnostic |
| `releases/release-1.3.0.md`, `-1.4.0.md` | Per the release-notes policy |

### Multi-tenancy conformance

Per `CLAUDE.md`, every change is checked for: tenant-scoped persistence
(`ChannelLinkNonceStore` Redis keys, `IdentityLinkStore` writes), async context
propagation (webhook handlers spawning work must use `TenantContextPropagator`),
`TenantGuard` injection over direct `TenantContextHolder` access, SINGLE-mode
compatibility, and tenant pass-through on new MCP tools.

### Examples policy

Any new example sets `jaiclaw.skills.allow-bundled: []` and includes the
`jaiclaw-maven-plugin` `analyze` execution.

### Testing

Spock, `*Spec` naming, `src/test/groovy/`. Phase 1 uses locally-signed tokens
against a static JWKS — **no live Logto required**. Phase 2 needs a Logto
container; add a `logto-e2e` skill following the `kanban-e2e` pattern rather than
a reactor-blocking integration test.

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| AgentMind key migration loses per-user state | Medium | High | Dual-read (§2.5), opt-in flag, no cutover |
| CIMD ⇄ webhook SSRF conflict blocks Phase 3 | **High** | Medium | Decide ingress routing in Phase 2 (§2.7) |
| Logto enforces `ossDefaultQuota` in a later release | Medium | Low-Med | Pin the image tag; nothing we need is gated today |
| ES384 / `at+jwt` / RFC 8707 discovered late | **High** if undocumented | Medium | Called out in §1.3; covered by specs |
| `consoleCollaboration` locked (single admin) | Certain | Low | Automate day-2 via Management API |
| Logto becomes a tier-0 dependency | Certain | Medium | Offline JWKS validation survives brief outages; HA Postgres |
| Maintaining a Helm chart (none upstream) | Certain | Low | P2.13; use the Docker-based `helm/helm-deploy.sh` pattern |

---

## Summary

| Phase | Deliverable | Est. | Logto-specific? |
|---|---|---|---|
| **Prereq** | [Tenant resolution remediation](TENANT-RESOLUTION-REMEDIATION.md) | 6.5d | No |
| **1** | `jaiclaw-security-oidc` — correct OAuth2 resource server | 9d | **No** |
| **2** | `jaiclaw-identity-logto` — verified identity, JIT tenancy, lifecycle | 19d | Yes |
| **3** | MCP OAuth — RFC 9728 + CIMD | 10d | Partly |

**≈ 45 days total**, of which the first 15.5 carry no Logto commitment.

Start with the prerequisite — it fixes two critical defects and is worth shipping
on its own merits.

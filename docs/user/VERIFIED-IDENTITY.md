# Verified Channel Identity

*Applies to 1.4.0 and later. Module: `jaiclaw-identity`.*

Prove that the person on a messaging channel controls a real external identity,
and use that proof for tenancy, authorization, per-user memory, and GDPR.

**Provider-neutral.** The ceremony is a standard OAuth authorization-code flow
with PKCE, so it works with any OIDC provider. Nothing in this module names a
vendor.

---

## 1. The problem this solves

Channel identity is **asserted, never verified**. A Slack user id arrives in an
inbound message and is taken at face value. Three consequences:

| Consequence | Why it matters |
|---|---|
| **Tenant comes from the bot, not the person** | Everyone talking to one bot lands in one tenant with identical authority. One bot cannot serve two customers. |
| **`ToolProfile` cannot vary by user** | There is no principal to attach a profile to, so every channel user gets the deployment default. |
| **A person is several data subjects** | GDPR erasure keyed on a channel id deletes one channel's data and leaves the rest. |

Worse, `peerId` is not always a user id: on Slack and Discord it is the
*channel*, so two people in one room are indistinguishable.

---

## 2. The ceremony

One-time, per (channel, user):

```
1. Unknown user messages the bot.
2. ChannelLinkService.issue(channel, peerId)
     → a single-use nonce and an authorization URL
3. The agent sends the URL to the user.
4. The user authenticates with the identity provider.
5. The provider redirects to /api/identity/link/callback with code + state.
6. ChannelLinkService.complete(state, code)
     → exchanges the code, records a VERIFIED IdentityLink
7. Later messages resolve (channel, peerId) → the provider's subject.
```

### Configuration

```yaml
jaiclaw:
  identity:
    link:
      enabled: true
      authorize-uri: https://idp.example.com/oidc/auth
      token-uri: https://idp.example.com/oidc/token
      client-id: jaiclaw-gateway
      client-secret: ${LINK_CLIENT_SECRET}   # omit for a public PKCE client
      redirect-uri: https://jaiclaw.example.com/api/identity/link/callback
      scopes: [openid, profile]
      resource: https://jaiclaw.example.com/api   # RFC 8707, when required
      nonce-ttl: 10m
```

`redirect-uri` must match what is registered with the provider **exactly** —
including scheme, port, and trailing slash.

### Security properties

| Property | How |
|---|---|
| **Intercepted code is useless** | PKCE S256 is mandatory, not conditional on a client secret |
| **CSRF and replay protection** | The OAuth `state` is a single-use nonce; redemption is atomic |
| **Cannot link someone else's channel** | The channel binding comes from the server-side record, never the callback |
| **Bounded attack window** | Nonces expire (default 10 minutes) |
| **Secrets stay out of logs** | `ChannelLinkRequest.toString()` redacts the PKCE verifier |

> **Multi-pod deployments need a shared nonce store.** The default is in-memory,
> so a callback landing on a different replica than issued the nonce fails.
> Supply a `@Bean ChannelLinkNonceStore` backed by Redis or a database. The
> contract requires atomic single-use redemption — a nonce redeemable twice is a
> replay vector.

---

## 3. Verified vs. asserted links

```java
link.isVerified()   // issuer != null && verifiedAt != null
```

A link created by calling `IdentityLinkService.link(...)` directly is
**asserted** — nothing proves the binding. A link created by completing the
ceremony is **verified**.

The distinction is load-bearing. Anything that grants access keys on
verification only; honouring an asserted link would let anyone able to call
`link()` adopt another user's identity. Asserted links remain useful for
conversational continuity, which is what they were always for.

---

## 4. Per-user state migration

AgentMind memory and tendencies have always been keyed by
`sha256(channelId:peerId)` truncated to 16 characters. Moving to a verified
subject is right, but a naive switch **changes every key and orphans all stored
state** — silently, with users appearing to have been forgotten.

`CanonicalUserKeyResolver` dual-reads instead:

| | Returns |
|---|---|
| `resolveForRead` | `[canonical, legacy]` — try each in turn |
| `resolveForWrite` | canonical **only if verified**, else legacy |

State migrates on the first write after linking. No batch job, no window where
reads fail, and unlinked users are untouched.

```yaml
jaiclaw:
  agentmind:
    canonical-user-keys: true    # default false
```

Opt-in and reversible: turning it off returns every user to the legacy key, and
pre-migration state is still there because nothing was deleted.

---

## 5. GDPR: one person, one erasure

`DataSubjectAliasResolver` expands a request across every identifier the same
person's data is stored under:

```
DELETE /api/gdpr/subject/12345      (a Telegram id)
  → aliases: [12345, U999, subject-abc]
  → erases Telegram AND Slack AND canonical-keyed data
```

Wired automatically when `jaiclaw-identity` is on the classpath. Without it the
resolver is identity and behaviour is unchanged.

Only **verified** links are followed — otherwise a bogus link becomes a way to
have someone else's data deleted.

---

## 6. Provider SPIs

Two optional interfaces for capabilities standard OAuth does not cover. Both are
provider-neutral; implementations are per-provider.

```java
public interface IdentityProviderClient {
    Optional<ExternalUser> findUser(String subject);
    List<OrganizationMembership> memberships(String subject);
}

public interface IdentityProviderWebhookVerifier {
    boolean verify(String rawBody, Map<String, String> headers);
    Optional<IdentityLifecycleEvent> parse(String rawBody);
}
```

The linking ceremony needs **neither** — it runs entirely on standard OAuth.
They are for enumerating a user's organizations, and for reacting to lifecycle
events (membership changed, user suspended, user deleted) so cached authority
can be revoked promptly rather than at token expiry.

Lifecycle events use a small closed vocabulary rather than any one provider's
catalogue, so consumers need not know whose webhook fired.

> **Implementers:** verify the signature over the **raw** body before parsing.
> Re-serializing parsed JSON changes the bytes and breaks the comparison —
> usually intermittently, which is worse than breaking outright.

---

## 7. Operating notes

- The callback endpoint is **unauthenticated by necessity** — the caller has not
  yet proven who they are — and is permitted in every security mode. The
  single-use OAuth state is what makes that safe.
- Failures are deliberately indistinguishable to the caller: expired, replayed,
  unknown, and failed-exchange all render the same message. Distinguishing them
  helps someone probing for live nonces and helps a genuine user not at all.
- The ID token returned from the token endpoint is not signature-verified — it
  arrived over a direct TLS connection in response to a code we generated, which
  OpenID Connect Core §3.1.3.7 permits. Tokens arriving by any other route
  **are** verified; that is what `jaiclaw-security-oidc` does.

---

## 8. See also

- [`OIDC-AUTHENTICATION.md`](OIDC-AUTHENTICATION.md) — authenticating API callers
- [`API-KEY-AUTHENTICATION.md`](API-KEY-AUTHENTICATION.md) — the default mode
- [`features/IDENTITY.md`](features/IDENTITY.md) — identity linking basics

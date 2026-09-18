# JaiClaw 1.2.0 Release Notes

**Release Date:** _pending_
**Distribution:** Maven Central + TapTech Nexus (`tooling.taptech.net`)

> 1.2.0 is the **security and identity release.** It closes the CRITICAL and HIGH findings from the 2026-08-24 security scan, fixes two further critical defects found during a subsequent identity-provider review, and adds a provider-neutral authentication and identity stack: an OIDC resource server, OAuth discovery for MCP clients, and verified channel identity.

## Highlights — authentication & identity

- 🔴 **Tenant context can no longer be set from an unverified JWT.** `JwtTenantResolver` scraped the tenant claim out of a base64 payload without checking the signature, and was auto-wired into the resolver chain that `/api/chat`, `/mcp/*` and `/v1/chat/completions` all consult. In the **default `api-key` mode** no JWT-validating filter exists at all, so an attacker needed only to *omit* a valid signature. Replaced with `SecurityContextTenantResolver`, which reads the validated principal and cannot trust request headers. **See Breaking changes.**
- 🔴 **Tool authorization no longer fails open.** `ToolProfileHolder.getOrDefault()` returned `FULL` when unset — which was every request in api-key mode, every channel message, and the `permitAll` `/webhook/**` path. Agents ran with shell, filesystem and browser access. Added `getOrDefault(fallback)` plus `jaiclaw.security.default-tool-profile`; `/webhook/**` is clamped to `WEBHOOK_SAFE`.
- **API key store** — `jaiclaw.security.api-keys[]` binds a key to exactly one tenant and one role. Constant-time SHA-256 indexed lookup. Revoking a capability is deleting one key. Fixes `ApiKeyAuthenticationFilter`'s multi-tenant branch, which had been dead code in every prior release (`tenantGuard` was hardcoded `null`).
- **New module `jaiclaw-security-oidc`** — `jaiclaw.security.mode=oidc` validates tokens against an issuer's JWKS. Provider-agnostic: Logto, Keycloak, Okta, Auth0, Entra ID by configuration alone. Opt-in; not pulled by the starter.
- **OAuth discovery for MCP clients** — RFC 9728 metadata at `/.well-known/oauth-protected-resource` plus the `WWW-Authenticate` pointer that makes it discoverable.
- **Verified channel identity** — a PKCE ceremony proving a channel user controls an external identity, so tenancy and authorization can finally derive from the *person* rather than the bot. Includes a dual-read migration for per-user AgentMind state and GDPR erasure that spans linked channels.
- 🔴 **46 auto-configurations were missing from git.** A bare `META-INF/` ignore rule swallowed every module's `AutoConfiguration.imports`. On a fresh clone `jaiclaw-security`'s auto-config never registered at all — the build succeeded and the beans simply never appeared.

## Highlights — security scan remediation

- **MCP cross-tenant defense** — `CalendarMcpToolProvider`, `PipelineMcpToolProvider`, and `KanbanMcpToolProvider` now reject caller-supplied `tenantId` arguments that don't match the caller's `TenantContext`. Shared helper `io.jaiclaw.core.tenant.TenantArgResolver` centralizes the check; `TenantContextHolder.withTenant(...)` provides a save-restore wrap that no longer clobbers an outer caller's context.
- **PDF tool workspace boundary** — `PdfReadFieldsTool` and `PdfFillFormTool` in `jaiclaw-documents` now route every path through `WorkspaceBoundary.resolve(...)` (mirrors `FileEditTool`). Absolute paths escaping the workspace are rejected with a `ToolResult.Error`. Opt out with `jaiclaw.tools.documents.workspace-boundary=false`.
- **Method-level authorization is now honest** — `@EnableMethodSecurity` is registered on all three security modes (`api-key`, `jwt`, `none`). Prior to this release the `@PreAuthorize` annotations on `PipelineDeploymentController` and `GdprController` were silent no-ops. New `PipelineStudioControllerAuthzSpec` is the first-of-its-kind regression guard proving the annotations actually fire.
- **Admin controller now gated** — `AdminController` picked up a class-level `@PreAuthorize("@adminAuthzExpressions.admin()")` backed by the new `AdminAuthzProperties` / `AdminAuthzExpressions` beans (`jaiclaw.gateway.admin.roles.admin`).
- **Pipeline Studio controller now gated** — every endpoint on `PipelineStudioController` picked up per-endpoint `@PreAuthorize` — `viewer()` for read paths, `author()` for mutating paths.
- **OWASP dependency-check wired for NVD** — the plugin now consumes an NVD API key via `-Dnvd.api.key=... / $NVD_API_KEY`. Without a key the plugin aborted on the first artifact; with one, the scan runs end-to-end.

## Breaking changes

### `JwtTenantResolver` removed

Tenant context is now carried by the authenticated principal
(`JaiClawAuthentication`) in every mode. Nothing reads a tenant claim from an
unverified `Authorization` header any more.

Deployments that relied on the header-scraping behaviour must move to
`mode=jwt` or `mode=oidc`, where the tenant claim is signed, or configure
`jaiclaw.security.api-keys[]` with an explicit `tenant-id` per key.

### api-key + multi-tenant now requires the tenant header

`jaiclaw.security.mode=api-key` with `jaiclaw.tenant.mode=multi` now requires
`X-Tenant-Id` (configurable via `jaiclaw.tenant.tenant-header`, which was
previously ignored):

| Key's `tenant-id` | Header sent | Result |
|---|---|---|
| `acme` | *(absent)* | **401** `missing_tenant_id` |
| `acme` | `acme` | **200** |
| `acme` | `globex` | **403** `cross_tenant_denied` |
| *(none)* | *(anything)* | **401** `key_has_no_tenant` |

This is `ApiKeyAuthenticationFilter`'s documented-but-never-executed branch
running for the first time. **Single-tenant deployments are unaffected**, and
the legacy `jaiclaw.security.api-key` scalar keeps working there; in
multi-tenant mode it has no tenant association and is rejected, with a startup
WARN naming the fix.

### `ToolProfileHolder.getOrDefault()` deprecated

Fails open. Use `getOrDefault(ToolProfile)` and supply
`jaiclaw.security.default-tool-profile`. **That property defaults to `FULL` in
1.2.0** to preserve existing behaviour, and **becomes `MINIMAL` in 1.3.0** —
set it explicitly now.

### `RoleToolProfileResolver` ranking corrected

Ranked by `ordinal()` (declaration order) rather than `ToolProfile.privilege()`.
A user holding both `CODING` and `MESSAGING` previously received `MESSAGING`;
they now correctly receive `CODING`.

### Method-security defaults changed

`@EnableMethodSecurity` is now on. Any `@PreAuthorize` annotation in downstream code (including third-party code that JaiClaw brings in) that was previously silently ignored will now be enforced.

**Backward-compat mitigation shipped with this release:** `Roles.DEFAULT` on `PipelineAuthoringProperties`, `GdprAuthzProperties`, and the new `AdminAuthzProperties` were flipped from concrete role names (e.g., `ROLE_PIPELINE_DEPLOYER`, `GDPR_OPERATOR`) to **blank strings**. The existing `hasAuthorityOrBlank(...)` helpers already treat blank as "allow any authenticated principal", so default deployments behave identically to before enabling method security.

Adopters who **want** role-based gating must set the property explicitly in `application.yml` AND ensure their auth layer grants a matching authority. Example:

```yaml
jaiclaw:
  pipeline:
    authoring:
      roles:
        deployer: ROLE_PIPELINE_DEPLOYER   # opt-in — requires the auth layer to grant this authority
  compliance:
    gdpr:
      roles:
        operator: GDPR_OPERATOR
  gateway:
    admin:
      roles:
        admin: JAICLAW_ADMIN
```

**Caution — api-key deployments:** this was true when the method-security work landed, and is now only partly true. In **single-tenant** mode the api-key principal still carries **zero granted authorities**, so a non-blank role config will return `403 Forbidden` on every api-key call. In **multi-tenant** mode, a key configured through `jaiclaw.security.api-keys[]` now carries its declared `role` as a granted authority, so `@PreAuthorize` gating works — see *API key store* below.

### Naming convention for admin role

`AdminAuthzProperties.Roles.admin` uses the `JAICLAW_ADMIN` naming style (matching `GDPR_OPERATOR`, no `ROLE_` prefix). This differs from the `PipelineAuthoringProperties.Roles.*` defaults (which used the `ROLE_PIPELINE_*` prefix pre-1.2.0). Both work — `hasAuthorityOrBlank` does an exact string match, no `ROLE_` magic. Adopters pick whichever convention suits their auth layer.

## New modules

- **`jaiclaw-security-oidc`** — OAuth 2.0 / OIDC resource server. Opt-in via `jaiclaw.security.mode=oidc`; **not** pulled by `jaiclaw-spring-boot-starter`, so `nimbus-jose-jwt` and the OAuth2 stack stay off the classpath unless asked for.

## New APIs

- **`io.jaiclaw.core.tenant.TenantArgResolver`** — static helper `resolveOrValidate(TenantContext caller, Map<String,Object> args, String argName, String defaultTenant)`. Returns the caller tenant on match; throws `CrossTenantAccessException` on mismatch. Use in any MCP tool provider that accepts a caller-supplied `tenantId` arg.
- **`io.jaiclaw.core.tenant.CrossTenantAccessException`** — thrown by `TenantArgResolver` when caller and requested tenants diverge.
- **`io.jaiclaw.core.tenant.TenantContextHolder.withTenant(TenantContext, Supplier)`** and `.withTenant(TenantContext, Runnable)` — save/restore-safe wrappers. Prefer over manual `set/clear` pairs — the naive pattern clobbers an outer caller's tenant on `finally`.
- **`io.jaiclaw.gateway.admin.AdminAuthzProperties`** / **`AdminAuthzExpressions`** — role config + SpEL holder for the `AdminController`. Bean name: `adminAuthzExpressions`.
- **`io.jaiclaw.security.authn.JaiClawAuthentication`** — the principal every authentication filter now emits: caller, tenant, tool profile, and an `AuthSource` (`API_KEY | JWT | OIDC`). Read tenancy from this rather than from request attributes.
- **`io.jaiclaw.security.ApiKeyStore`** — SPI answering "is this key known, and to which tenant and role?". Reference impl `ConfigApiKeyStore`; adopters can supply Redis/JDBC backends.
- **`io.jaiclaw.core.tenant.AuthenticatedTenantSupplier`** — bridges the validated principal to tenant resolution without `jaiclaw-gateway` depending on Spring Security.
- **`io.jaiclaw.core.gdpr.DataSubjectAliasResolver`** — expands a data subject across every identifier their data is stored under. Defaults to identity.
- **`io.jaiclaw.identity.link.ChannelLinkService`** / **`ChannelLinkNonceStore`** — the verified-identity ceremony and its pluggable nonce store.
- **`io.jaiclaw.identity.provider.IdentityProviderClient`** / **`IdentityProviderWebhookVerifier`** — provider-neutral SPIs for organization membership and lifecycle events.
- **`io.jaiclaw.identity.CanonicalUserKeyResolver`** — dual-read migration for per-user AgentMind keys.

## Security fixes

Closes findings from `security-report-2026-08-24.md`:

- **SEV-001** (HIGH) — OWASP `dependency-check-maven` now consumes `-Dnvd.api.key=...`; scan runs end-to-end.
- **SEV-002** (CRITICAL) — `CalendarMcpToolProvider` cross-tenant write via `tenantId` arg rejected.
- **SEV-003** (CRITICAL) — `PipelineMcpToolProvider` cross-tenant trigger via `tenantId` arg rejected.
- **SEV-004** (CRITICAL) — `PdfReadFieldsTool` / `PdfFillFormTool` reject paths escaping the workspace.
- **SEV-005** (HIGH) — `MessagingMcpToolProvider` switched from naive `set/clear` to save-restore `withTenant`; regression tests added. `handleBroadcastMessage` recipient tenant validation deferred as SEV-005b — tracked as a `@PendingFeature` in `MessagingMcpToolProviderSpec`.
- **SEV-006** (HIGH) — `AdminController` gated with `@PreAuthorize("@adminAuthzExpressions.admin()")`.
- **SEV-007** (HIGH) — `PipelineStudioController` gated per-endpoint with `viewer()` / `author()` roles.
- **SEV-008** (HIGH) — `KanbanMcpToolProvider` now wraps `execute()` with `TenantContextHolder.withTenant(...)`.

## Deferred (backlog)

- **SEV-005b** — `handleBroadcastMessage` recipient tenant validation. Needs a design pass on whether `ChannelRegistry` tracks per-peer tenant.
- **SEV-009 through SEV-019** — Medium/Low/Info findings from the same report.

## Build fixes

- **46 auto-configurations were missing from git.** A bare `META-INF/` rule in `.gitignore` — intended for build output — also matched `src/main/resources/META-INF`, excluding every module's `AutoConfiguration.imports`. Only 5 of 51 were tracked; the rest had been `git add -f`'d by contributors who noticed, or not added at all. `jaiclaw-security` was in the second group, so **on a fresh clone its entire auto-configuration never registered**. The failure is quiet: the build succeeds, the jar packages, and the beans simply never appear. 18 further source resources were affected, including Drools `kmodule.xml` files and pipeline processor templates.

## Bug fixes

- **Non-interactive CLI commands ignored their arguments.** `jaiclaw chat "hello"` started a REPL and discarded the message. Two causes: `spring.shell.interactive.enabled` was hardcoded `true`, which made Spring Shell's interactive runner win and consume the process; and Spring Shell 4's `@Option` is named-only, so the positional message bound nothing. Affected every one-shot command, not just `chat`.
- **`jaiclaw version` could describe a different jar than the one that runs.** `resolve_jaiclaw_version` had drifted from `resolve_jar`, ignoring both `JAICLAW_CLI_JAR` and the dev build. A globally installed jar also outranked the source tree, so a stale install shadowed local work.

## Dependency additions

- `spring-boot-starter-oauth2-resource-server` — in the new `jaiclaw-security-oidc` module only.
- `spring-boot-actuator` (`optional`) in `jaiclaw-security`, for `/actuator/jaiclaw-api-keys`; gated by class name so it stays inert when absent.
- `spring-boot-autoconfigure` and `spring-web` (`optional`) in `jaiclaw-identity`, for the linking ceremony.

## Version alignment

_No framework version bumps in 1.2.0 — Spring Boot 4.1.0, Spring AI 2.0.0, Embabel 1.5.0 unchanged._

## Verification

- **9,865 unit tests**, full reactor `BUILD SUCCESS`.
- **End-to-end verified** against a live Anthropic-compatible provider: CLI fast-path and one-shot dispatch, pipeline startup validator, `/actuator/pipelines`, HTTP trigger with `{{input}}` templating, gateway auth enforcement (401/403/200 across five surfaces), and an authenticated LLM round-trip.
- **Regression-tested the critical fix in a live app**: a forged `Bearer` token naming another tenant, presented alongside a *valid* API key, produced zero occurrences of that tenant in either application's logs.

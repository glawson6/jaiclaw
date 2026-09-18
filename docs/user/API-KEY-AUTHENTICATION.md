# API Key Authentication

*Applies to 1.2.0 and later.*

JaiClaw's default security mode is `api-key`. This guide covers configuring
multiple keys, binding each to a tenant and role, and the exact status codes
callers receive.

---

## 1. The model: one key, one tenant, one role

An API key is a **complete, single-purpose credential**:

- It belongs to **exactly one tenant**.
- It grants **exactly one role**.

There are deliberately no multi-tenant or multi-role keys. An operator who needs
authority in two tenants, or two roles within one tenant, holds two keys and
presents the one matching the job.

The cost is real — a caller needing two capabilities in one request cannot have
them — and it buys two things worth more: revoking a capability is deleting one
key, and a leaked key exposes exactly one (tenant, role) pair.

---

## 2. Configuration

```yaml
jaiclaw:
  tenant:
    mode: multi
    tenant-header: X-Tenant-Id      # optional; this is the default

  security:
    mode: api-key
    api-keys:
      - name: acme-admin            # operator-facing label; appears in logs
        key: ${ACME_ADMIN_KEY}      # supply via env var, not inline
        tenant-id: acme
        role: jaiclaw.admin

      - name: acme-chat
        key: ${ACME_CHAT_KEY}
        tenant-id: acme
        role: jaiclaw.user

      - name: globex-readonly
        key: ${GLOBEX_RO_KEY}
        tenant-id: globex
        role: jaiclaw.readonly
```

### Fields

| Field | Required | Notes |
|---|---|---|
| `name` | recommended | Logged on every denial. **Never** log or expose `key`. |
| `key` | one of | The key material. Use a `${ENV_VAR}` placeholder. |
| `secret-ref` | one of | `provider://vault/item/field`, resolved via the core `SecretsResolver`. |
| `tenant-id` | in multi-tenant | The one tenant this key may act for. |
| `role` | **always** | The one authority granted. **Missing role aborts startup.** |

### Key rotation

Duplicate `(tenant-id, role)` pairs are allowed precisely so rotation needs no
downtime — deploy the new key alongside the old, migrate callers, then remove
the old entry:

```yaml
      - name: acme-admin-2025
        key: ${ACME_ADMIN_KEY_OLD}
        tenant-id: acme
        role: jaiclaw.admin
      - name: acme-admin-2026
        key: ${ACME_ADMIN_KEY_NEW}
        tenant-id: acme
        role: jaiclaw.admin
```

Duplicate **key material** is rejected at startup — one entry would silently
shadow the other and hand a caller authority they were not meant to have.

---

## 3. Making a request

In multi-tenant mode a caller sends both headers:

```bash
curl -H "X-API-Key: $ACME_ADMIN_KEY" \
     -H "X-Tenant-Id: acme" \
     https://jaiclaw.example.com/api/chat -d '{"content":"hello"}'
```

The key says *who you are*; the header says *which tenant you are acting for*.
Both must agree.

### Status codes

| Key's `tenant-id` | `X-Tenant-Id` | Result |
|---|---|---|
| `acme` | *(absent)* | **401** `missing_tenant_id` |
| `acme` | `acme` | **200** |
| `acme` | `globex` | **403** `cross_tenant_denied` |
| *(none)* | *(anything)* | **401** `key_has_no_tenant` |
| unknown key | *(anything)* | **401** `invalid_api_key` |

**Why 401 for a missing header and 403 for a wrong one.** A missing header means
the credential is incomplete — authentication has not finished — so the response
carries a `WWW-Authenticate` challenge. A wrong header means authentication
*succeeded* and the caller is simply not authorised for that tenant. Conflating
the two hides which of the caller's problems is which.

A 403 is logged at WARN with the key name, its bound tenant, and the attempted
tenant. It is a credential reaching outside its binding, and worth alerting on.

---

## 4. Single-tenant mode

With `jaiclaw.tenant.mode: single` (the default), the tenant header is never
required or consulted, and **roles are ignored** — callers authenticate with no
authorities, exactly as before 1.2.0. Roles only become meaningful once there are
tenants to scope them to.

The legacy `jaiclaw.security.api-key` scalar continues to work here. In
multi-tenant mode it has no tenant association and is rejected with 401; a
startup WARN names the migration.

---

## 5. Diagnostics

Expose the read-only endpoint to see what the deployment loaded:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: jaiclaw-api-keys,health
```

```json
GET /actuator/jaiclaw-api-keys
{
  "mode": "api-key",
  "count": 3,
  "defaultToolProfile": "FULL",
  "keys": [
    {"name": "acme-admin", "tenantId": "acme", "role": "jaiclaw.admin", "source": "config"}
  ]
}
```

It reports names, tenants, and roles — **never key material in any form**.

Like every actuator endpoint in this codebase it performs no authorization of its
own: front `/actuator/**` with the same auth as the rest of your admin surface.

---

## 6. Tool profiles

Requests that carry no authenticated tool profile fall back to
`jaiclaw.security.default-tool-profile`.

```yaml
jaiclaw:
  security:
    default-tool-profile: MINIMAL    # 1.2.0 default is FULL
```

> ⚠️ **This defaults to `FULL` in 1.2.0 and becomes `MINIMAL` in 1.3.0.** `FULL`
> means api-key callers and every channel-originated message reach the complete
> tool surface — shell, filesystem, browser. The permissive default is retained
> for one minor so the security work does not silently strip access from existing
> deployments. Set it explicitly.

The `/webhook/**` path is independently clamped to `WEBHOOK_SAFE` regardless of
this setting, because it is `permitAll` and its payload is attacker-controlled.

---

## 7. See also

- [`docs/dev/TENANT-RESOLUTION-REMEDIATION.md`](../dev/TENANT-RESOLUTION-REMEDIATION.md) — why this model exists
- [`docs/dev/LOGTO-IMPLEMENTATION-PLAN.md`](../dev/LOGTO-IMPLEMENTATION-PLAN.md) — the OIDC path that supersedes API keys for user-facing auth

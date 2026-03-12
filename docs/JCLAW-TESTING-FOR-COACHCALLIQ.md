# JClaw — Testing Requirements for CoachCallIQ

Comprehensive test specifications covering every JClaw capability required to support the CoachCallIQ coaching platform

| | |
|---|---|
| **Version** | 1.0 |
| **Date** | March 2026 |
| **Repo** | https://github.com/glawson6/jclaw |
| **Companion Docs** | JCLAW-REQUIREMENTS-FOR-COACHCALLIQ.md, coachcalliq.md |
| **Status** | Planning |

## 1. Overview & Testing Philosophy

JClaw must be verified across six dimensions to be considered CoachCallIQ-ready:

1. **Correctness** — does the feature do what it says?
2. **Tenant isolation** — does one tenant's data ever bleed into another's?
3. **Failure handling** — does the system fail gracefully when channels, LLMs, or storage are unavailable?
4. **Concurrency** — does the system behave correctly under simultaneous multi-tenant load?
5. **Security** — do auth boundaries hold under adversarial conditions?
6. **Performance** — do key workflows complete within acceptable bounds?

### Test Categories Used in This Document

| Category | Scope | LLM Calls | External Services |
|---|---|---|---|
| Unit | Single class or method | Mocked | Mocked |
| Integration | Module boundary | Mocked or stubbed | Embedded (H2, WireMock) |
| Contract | SPI implementation compliance | Mocked | Mocked |
| Tenant Isolation | Cross-tenant data safety | Mocked | Embedded |
| Channel | End-to-end channel adapter | Mocked LLM | WireMock for external APIs |
| Security | Auth and permission enforcement | Mocked | Embedded |
| Performance | Latency and throughput under load | Real or stubbed | Embedded |
| End-to-End | Full request path, no mocks | Real LLM (CI gate) | Real or sandboxed |

### Test Framework Stack

- **JUnit 5** — test lifecycle
- **Mockito** — mocking and verification
- **AssertJ** — fluent assertions
- **WireMock** — HTTP service simulation (Telegram, Twilio, YouTube, Gmail, Embabel)
- **Testcontainers** — PostgreSQL with pgvector, MinIO, Redis for realistic embedded dependencies
- **Spring Boot Test** — `@SpringBootTest` for integration and end-to-end slices
- **Embabel FakePromptRunner / FakeOperationContext** — mock Embabel LLM calls in unit tests
- **Awaitility** — async assertion for video pipeline and background ingestion jobs

## 2. Tenant Context Tests (jclaw-core)

The `TenantContext` and `TenantContextHolder` are the foundation of everything. These must be rock-solid.

### 2.1 TenantContextHolder Unit Tests

**TC-CORE-001: Set and get within same thread**

- Given: No tenant context is set
- When: `TenantContextHolder.set(tenantA)` is called
- Then: `TenantContextHolder.get()` returns `tenantA`

**TC-CORE-002: Context is cleared after clear()**

- Given: `TenantContextHolder.set(tenantA)` has been called
- When: `TenantContextHolder.clear()` is called
- Then: `TenantContextHolder.get()` returns `null`

**TC-CORE-003: Thread isolation — context does not bleed across threads**

- Given: Thread-1 sets `TenantContextHolder` to `tenantA`
- When: Thread-2 calls `TenantContextHolder.get()` simultaneously
- Then: Thread-2 receives `null` (not `tenantA`)

**TC-CORE-004: Thread pool task inherits no tenant context**

- Given: `TenantContextHolder` is set to `tenantA` on the main thread
- When: A `@Async` task executes on a Spring thread pool thread
- Then: `TenantContextHolder.get()` inside the `@Async` task returns `null`
  (async tasks must explicitly receive and re-set tenant context)

**TC-CORE-005: Context is cleared even when an exception is thrown**

- Given: A gateway request sets `TenantContextHolder` to `tenantA`
- When: The agent throws a `RuntimeException` mid-execution
- Then: `TenantContextHolder.get()` after the request completes returns `null`
  (verify via finally block or filter cleanup)

### 2.2 TenantContext Propagation Integration Tests

**TC-CORE-010: Tenant resolved from JWT claim propagates to tool execution**

- Given: An HTTP request carries a JWT with claim `programId=uga-football`
- When: The request reaches a tool execution
- Then: `TenantContextHolder.get().getTenantId()` inside the tool equals `"uga-football"`

**TC-CORE-011: Tenant resolved from Telegram bot token propagates to agent**

- Given: A Telegram update arrives for bot token mapped to `programId=alabama-basketball`
- When: The JClaw gateway processes the update
- Then: The agent receives `TenantContext` with `tenantId="alabama-basketball"`

**TC-CORE-012: Missing tenant context causes request rejection**

- Given: An HTTP request arrives with no JWT and no recognizable bot token
- When: The gateway attempts tenant resolution
- Then: The request is rejected with `401 Unauthorized`
- And: No agent execution occurs

**TC-CORE-013: Invalid JWT signature causes request rejection**

- Given: An HTTP request carries a JWT signed with a different secret
- When: The gateway validates the token
- Then: The request is rejected with `401 Unauthorized`

**TC-CORE-014: Expired JWT causes request rejection**

- Given: An HTTP request carries a valid but expired JWT
- When: The gateway validates the token
- Then: The request is rejected with `401 Unauthorized`

## 3. Tenant Isolation Tests (jclaw-memory, jclaw-agent)

These are the highest-stakes tests in the document. Tenant data leakage would be a catastrophic security failure for a coaching platform.

### 3.1 Memory Isolation

**TC-ISO-001: Memory records written by tenant A are not visible to tenant B (vector search)**

- Given: TenantA stores evaluation note "Marcus Thompson - elite pass rusher"
  TenantB stores evaluation note "Jordan Smith - good route runner"
- When: TenantB executes a vector similarity search for "pass rusher"
- Then: TenantB's results contain zero records from TenantA
- And: TenantB does NOT see the Marcus Thompson record

**TC-ISO-002: Memory records written by tenant A are not visible to tenant B (direct lookup)**

- Given: TenantA stores a memory record with key="prospect:abc-123"
- When: TenantB attempts to retrieve a record with key="prospect:abc-123"
- Then: TenantB receives null / not found (not TenantA's record)

**TC-ISO-003: Memory flush for one tenant does not affect another tenant**

- Given: TenantA and TenantB both have memory records stored
- When: An admin flushes all memory for TenantA
- Then: TenantA's memory store is empty
- And: TenantB's memory records are completely intact

**TC-ISO-004: Entity memory is scoped to tenant and entity**

- Given: TenantA stores entity memory for `prospectId="prospect-001"`
  TenantB also has a `prospectId="prospect-001"` (same ID, different tenant)
- When: TenantB queries entity memory for `prospectId="prospect-001"`
- Then: TenantB sees only its own records, not TenantA's

**TC-ISO-005: Vector similarity search under concurrent multi-tenant load**

- Given: 10 tenants each have 1000 memory records stored
- When: All 10 tenants simultaneously execute vector searches
- Then: Every tenant's results contain only records belonging to that tenant
- And: No cross-tenant contamination occurs in any response

**TC-ISO-006: Memory export includes only the target tenant's records**

- Given: TenantA and TenantB have memory records stored
- When: `MemoryExportPort.export(tenantA)` is called
- Then: The export file contains only TenantA's records
- And: TenantB's records are completely absent from the export

### 3.2 Session Isolation

**TC-ISO-010: Sessions created by tenant A are not visible to tenant B**

- Given: TenantA creates session `sessionId="sess-xyz"`
- When: TenantB calls `SessionManager.getSession("sess-xyz")`
- Then: `SessionManager` throws `SessionNotFoundException` (not TenantB's session)

**TC-ISO-011: Session list returns only the current tenant's sessions**

- Given: TenantA has 3 active sessions, TenantB has 5 active sessions
  TenantB's `TenantContext` is active
- When: `SessionManager.listSessions()` is called
- Then: Exactly 5 sessions are returned, all belonging to TenantB

**TC-ISO-012: Session history is tenant-scoped**

- Given: TenantA's session contains messages about prospect DeShawn Williams
  TenantB attempts to access session history using TenantA's session ID
- When: `SessionManager.getSession("tenantA-session-id")` is called under TenantB context
- Then: `SessionNotFoundException` is thrown — TenantB cannot read TenantA's conversation

### 3.3 Tool Execution Isolation

**TC-ISO-020: Tool execution always uses the current TenantContext, not a stale one**

- Given: Request 1 from TenantA is processing (tool call in progress)
  Request 2 from TenantB arrives simultaneously
- When: Both tools execute concurrently
- Then: TenantA's tool sees only TenantA's data
- And: TenantB's tool sees only TenantB's data
- And: No cross-contamination occurs

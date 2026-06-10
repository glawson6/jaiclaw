# JaiClaw Pipeline Strategy

A "Camel-First, Batch-Optional" strategy for declarative multi-stage pipelines with YAML DSL, multi-tenant isolation, and cross-instance federation.

---

## 1. Current State Assessment

JaiClaw has substantial pipeline infrastructure spread across two extension modules. This section inventories the existing primitives with exact code references.

### 1.1 Pipeline Runtime — `extensions/jaiclaw-camel/`

**`PipelineEnvelope`** (`io.jaiclaw.camel.PipelineEnvelope`, line 17)
Immutable Java record tracking multi-stage execution. Fields: `pipelineId`, `correlationId`, `stageIndex` (0-based), `totalStages`, `replyChannelId`, `replyPeerId`, and `stageOutputs` (accumulated `List<String>`). The `nextStage(String currentOutput)` method (line 40) returns a new envelope with incremented index and appended output. `isLastStage()` (line 54) checks `stageIndex >= totalStages - 1`. Defensive copying via `List.copyOf()` in the canonical constructor (line 30) ensures immutability.

**`AgentProcessor`** (`io.jaiclaw.camel.AgentProcessor`, line 13)
A Camel `Processor` that bridges route execution to JaiClaw's agent. Accepts a `GatewayServiceAccessor` (functional interface, line 8 of `GatewayServiceAccessor.java`), `channelId`, and `accountId`. The `process()` method (line 26) converts the Camel exchange to a `ChannelMessage`, calls `gateway.handleSync()`, sets the response as the exchange body, and — critically — auto-advances the `PipelineEnvelope` header if present (lines 32–35).

**`CamelChannelAdapter`** (`io.jaiclaw.camel.CamelChannelAdapter`, line 34+)
Full `ChannelAdapter` SPI implementation using SEDA queues. Constants define `SEDA_IN_PREFIX = "seda:jaiclaw-"` with `-in`/`-out` suffixes. The adapter dynamically adds Camel `RouteBuilder` instances during `start()` (line 80) and supports three outbound bridge types: URI-based, cross-channel, and logger fallback. This SEDA routing pattern is the blueprint for pipeline stage interconnection.

**`CamelChannelConfig`** (`io.jaiclaw.camel.CamelChannelConfig`, line 17)
Record with 8 fields: `channelId`, `displayName`, `accountId`, `outboundUri`, `inboundUri`, `outbound`, `stateless`, `platformLimits`. Enforces mutual exclusivity between `outboundUri` and `outbound` (line 37–41).

**`JaiClawCamelAutoConfiguration`** (`io.jaiclaw.camel.JaiClawCamelAutoConfiguration`, line 37)
`@AutoConfiguration` with 3-phase channel discovery (YAML → `@JaiClawChannel` annotation → route properties) followed by bridge creation and adapter startup. The `ApplicationRunner` pattern (line 48) defers registration until all routes are added to `CamelContext`. This auto-config pattern is the model for `PipelineAutoConfiguration`.

**`CamelChannelProperties`** (`io.jaiclaw.camel.CamelChannelProperties`, line 20)
`@ConfigurationProperties(prefix = "jaiclaw.camel")` record binding `List<CamelChannelConfig> channels` from YAML.

### 1.2 Task Persistence — `extensions/jaiclaw-tasks/`

**`TaskFlow`** (`io.jaiclaw.tasks.TaskFlow`, line 20)
Record representing an ordered task sequence: `id`, `name`, `taskIds` (ordered `List<String>`), `status` (`TaskFlowStatus`), `createdAt`, `completedAt`, `tenantId`. Status progression: `PENDING → RUNNING → COMPLETED | FAILED | CANCELLED`.

**`TaskRecord`** (`io.jaiclaw.tasks.TaskRecord`, line 26)
Record representing a single unit of work: `id`, `name`, `description`, `status` (`TaskStatus`: `QUEUED → RUNNING → SUCCEEDED | FAILED | CANCELLED | BLOCKED`), `deliveryState`, `result`, `error`, `flowId` (links to parent `TaskFlow`), `metadata` map, lifecycle timestamps, `tenantId`. Fluent builders: `withResult()`, `withError()`, `withStarted()`, `withDeliveryState()`.

**`CamelTaskRoute`** (`io.jaiclaw.tasks.CamelTaskRoute`, line 25)
SEDA-based async task queue: `seda:jaiclaw-tasks?size=100&concurrentConsumers=5` (line 29). Tasks are `submit()`-ed with a handler callback (line 64), placed on the SEDA queue, and `processTask()` (line 69) dequeues and delegates to `TaskExecutor`. Bounded queue size provides backpressure.

**`TaskService`** (`io.jaiclaw.tasks.TaskService`, line 12)
CRUD facade over `TaskStore` and `FlowStore`. Creates tasks and flows with UUID IDs. Handles status transitions and persistence. No connection to `PipelineEnvelope` or Camel routing.

### 1.3 Multi-Tenancy Foundation

**`TenantGuard`** (`io.jaiclaw.core.tenant.TenantGuard`, line 27)
Central tenant resolution utility. Three strategies: `requireTenantIfMulti()` (throws in MULTI mode if no context), `resolveTenantIdForStorage()` (returns tenant ID or default), `resolveTenantPrefix()` (returns empty string in SINGLE mode). Abstracts SINGLE vs MULTI decision logic.

**`TenantContextPropagator`** (`io.jaiclaw.core.tenant.TenantContextPropagator`, line 24)
Captures `TenantContext` from the calling thread and restores it in target threads. Wraps `Runnable`, `Supplier<T>`, and `Executor`. No-op when context is null (zero overhead in SINGLE mode). Essential for SEDA queue transitions where messages cross thread boundaries.

**`CronJobExecutor`** (`io.jaiclaw.cron.CronJobExecutor`, line 19)
Sets `TenantContextHolder` from the job's `tenantId` before execution (line 37). This is the pattern for pipeline execution: set tenant context at pipeline start, propagate through stages.

### 1.4 Existing Examples

**`camel-html-summarizer`** — File polling → SEDA inbound → agent processing → SEDA outbound → logger. Demonstrates single-stage Camel pipeline with `CamelChannelAdapter` auto-wiring. `HtmlIngestRoute` polls `target/data/inbox/*.html`, sets `JaiClawPeerId` header, routes to SEDA. `SummaryLoggerRoute` consumes from SEDA outbound and logs.

**`data-pipeline`** — Tool-loop pattern with `DataPipelinePlugin` registering 4 tools (`ValidateSchemaTool`, `RunTransformTool`, `PreviewDataTool`, `LoadDataTool`). Uses `BEFORE_TOOL_CALL`/`AFTER_TOOL_CALL` hooks for audit trail. `ConsoleApprovalHandler` implements human-in-the-loop for `LoadDataTool`. This is an agent-directed pipeline, not a declarative one — the LLM decides tool order.

---

## 2. Gap Analysis

| Capability | Status | Location | Gap |
|---|---|---|---|
| Multi-stage tracking | **Exists** | `PipelineEnvelope` | No execution ID, no tenant field, no Map-keyed outputs |
| Agent ↔ Camel bridge | **Exists** | `AgentProcessor` | Single-channel only, no stage-type abstraction |
| SEDA routing | **Exists** | `CamelChannelAdapter` | Channel-specific, not generic pipeline stages |
| Task persistence | **Exists** | `TaskFlow` / `TaskRecord` | Not connected to `PipelineEnvelope` |
| SEDA task queue | **Exists** | `CamelTaskRoute` | Task-specific, not stage-generic |
| Tenant propagation | **Exists** | `TenantContextPropagator` | Not wired into pipeline execution |
| YAML pipeline DSL | **Missing** | — | No declarative pipeline definition |
| Pipeline executor | **Missing** | — | No component chains stages with data passing |
| Pipeline registry | **Missing** | — | No discovery/management of pipeline definitions |
| Conditional stages | **Missing** | — | No if/when/switch branching |
| Parallel stages | **Missing** | — | No fork/join semantics |
| Error strategies | **Missing** | — | No dead-letter, retry-then-fail config |
| Cross-instance federation | **Missing** | — | No distributed stage execution |
| Observability | **Partial** | Audit hooks | No pipeline-specific metrics/tracing |

### The Bridge Problem

`PipelineEnvelope` (runtime state) and `TaskFlow`/`TaskRecord` (persistence) represent the same concept — a multi-stage execution sequence — with no connection between them. `PipelineEnvelope` has no execution ID, no tenant context, and stores outputs as a `List<String>` (positional, not keyed). `TaskFlow` has tenant support and persistence but no integration with Camel routing. The core challenge is unifying these into a single `PipelineContext` that serves both runtime data passing and persistent state tracking.

---

## 3. Proposed YAML Pipeline DSL

### 3.1 Pipeline Definition Schema

```yaml
jaiclaw:
  pipelines:
    - id: doc-processor
      name: "Document Processing Pipeline"
      description: "Parse, summarize, and store incoming documents"
      tenant-ids: []                          # empty = all tenants
      enabled: true

      trigger:
        type: file                            # file | cron | http | camel-uri | manual
        uri: "file:target/data/inbox?include=.*\\.pdf&move=.done"

      error-strategy: retry-then-fail         # dead-letter | retry-then-fail | stop
      max-retries: 3
      dead-letter-uri: "seda:pipeline-dlq"    # only used with dead-letter strategy

      stages:
        - name: parse
          type: processor                     # agent | processor | camel | batch-chunk
          bean: documentParser                 # Spring bean name
          timeout: 30s

        - name: summarize
          type: agent
          agent-id: default
          system-prompt: "Summarize the following document concisely."
          channel-id: pipeline-internal
          timeout: 120s

        - name: store
          type: camel
          uri: "jpa:io.jaiclaw.example.DocumentSummary"

      output:
        type: channel                         # channel | camel-uri | log | none
        channel-id: slack
        template: "Summary for {{stages.parse.metadata.filename}}: {{stages.summarize.output}}"
```

### 3.2 Stage Types

| Type | Description | Required Fields | Processor Class |
|---|---|---|---|
| `agent` | Invoke JaiClaw agent | `agent-id` | `AgentStageProcessor` |
| `processor` | Call a Spring bean | `bean` | `BeanStageProcessor` |
| `camel` | Route to a Camel URI | `uri` | `CamelStageProcessor` |
| `batch-chunk` | Spring Batch chunk processing | `reader`, `writer`, `chunk-size` | `BatchStageProcessor` |

### 3.3 Trigger Types

| Type | Configuration | Notes |
|---|---|---|
| `file` | `uri` (Camel file component URI) | Leverages Camel's file polling |
| `cron` | `expression` (cron string) | Integrates with `jaiclaw-cron` module |
| `http` | `path` (REST endpoint path) | Exposes POST endpoint |
| `camel-uri` | `uri` (any Camel consumer URI) | Maximum flexibility |
| `manual` | none | API/CLI invocation only |

### 3.4 Conditional and Parallel Stages (Phase 2)

```yaml
stages:
  - name: classify
    type: agent
    agent-id: classifier

  - name: route-by-type
    type: switch
    on: "{{stages.classify.output}}"
    cases:
      invoice:
        - name: process-invoice
          type: processor
          bean: invoiceProcessor
      contract:
        - name: process-contract
          type: agent
          agent-id: contract-analyst

  - name: parallel-analysis
    type: parallel
    branches:
      - name: sentiment
        type: agent
        agent-id: sentiment-analyzer
      - name: entity-extract
        type: processor
        bean: nerExtractor
    join: merge-map                           # merge-map | first-wins | all
```

---

## 4. Architecture — "Camel-First, Batch-Optional"

### 4.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          jaiclaw-pipeline module                            │
│                                                                             │
│  ┌─────────────────────┐    ┌──────────────────┐    ┌───────────────────┐  │
│  │ PipelineDefinition  │    │ PipelineRegistry │    │ PipelineContext   │  │
│  │ Loader (YAML)       │───▶│ (in-memory +     │    │ (runtime state)   │  │
│  │                     │    │  tenant-scoped)  │    │                   │  │
│  └─────────────────────┘    └────────┬─────────┘    └───────────────────┘  │
│                                      │                         ▲            │
│                                      ▼                         │            │
│  ┌──────────────────────────────────────────────────────────┐  │            │
│  │              PipelineRouteBuilder                        │  │            │
│  │  Reads PipelineDefinition → builds CamelContext routes   │──┘            │
│  │  from(trigger) → stage1 → stage2 → ... → output         │               │
│  └────────────┬──────────────┬──────────────┬───────────────┘               │
│               │              │              │                               │
│               ▼              ▼              ▼                               │
│  ┌────────────────┐ ┌───────────────┐ ┌──────────────┐ ┌────────────────┐  │
│  │AgentStage      │ │BeanStage      │ │CamelStage    │ │BatchStage      │  │
│  │Processor       │ │Processor      │ │Processor     │ │Processor       │  │
│  │                │ │               │ │              │ │(optional dep)  │  │
│  │Wraps           │ │Invokes Spring │ │Routes to     │ │Spring Batch    │  │
│  │AgentProcessor  │ │bean by name   │ │Camel URI     │ │chunk step      │  │
│  └────────────────┘ └───────────────┘ └──────────────┘ └────────────────┘  │
│                                                                             │
│  Dependencies: jaiclaw-camel, jaiclaw-tasks (optional), jaiclaw-config      │
│  Optional: spring-batch-core (only if batch-chunk stages are used)          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Key Classes

**`PipelineDefinition`** — Immutable record parsed from YAML. Contains pipeline ID, name, tenant IDs, trigger config, list of stage definitions, error strategy, and output config.

**`PipelineContext`** — Evolution of `PipelineEnvelope`. Adds `executionId` (UUID per run), `tenantId`, `Map<String, StageOutput>` (keyed by stage name, replacing positional `List<String>`), and `metadata` (propagated across stages). Replaces `PipelineEnvelope` for new pipelines; `PipelineEnvelope` remains for backward compatibility.

```java
public record PipelineContext(
    String pipelineId,
    String executionId,        // NEW: UUID per execution
    String tenantId,           // NEW: tenant isolation
    String correlationId,
    int stageIndex,
    int totalStages,
    String replyChannelId,
    String replyPeerId,
    Map<String, StageOutput> stageOutputs,   // NEW: keyed by stage name
    Map<String, String> metadata              // NEW: propagated metadata
) {
    public record StageOutput(String output, Map<String, String> metadata, Instant completedAt) {}
}
```

**`PipelineRegistry`** — Manages pipeline definitions. Supports tenant scoping: `getForTenant(String tenantId)` returns only pipelines where `tenantIds` is empty or contains the tenant.

**`PipelineDefinitionLoader`** — Reads `jaiclaw.pipelines[]` from `application.yml` and per-tenant files (`tenants/{tenantId}/pipelines.yml`). Uses `@ConfigurationProperties`.

**`PipelineRouteBuilder`** — For each `PipelineDefinition`, builds a Camel route: `from(trigger) → seda:pipeline-{id}-stage-0 → seda:pipeline-{id}-stage-1 → ... → output`. Each inter-stage SEDA queue isolates stage processing and enables backpressure.

**`StageProcessor`** — Interface implemented by all stage types:

```java
public interface StageProcessor {
    void process(Exchange exchange, StageDefinition stage, PipelineContext context);
}
```

### 4.3 Execution Flow

```
1. Trigger fires (file, cron, HTTP, camel-uri, manual API)
       │
2. PipelineRouteBuilder's trigger route creates PipelineContext
   (executionId=UUID, tenantId from TenantContextHolder or definition)
       │
3. TenantContextHolder.set() — tenant context propagated via
   TenantContextPropagator.wrap() on SEDA transitions
       │
4. Stage N processor reads PipelineContext from exchange header
       │
   ┌───┴──────────────────────────────────────────────┐
   │ agent:     AgentStageProcessor → handleSync()    │
   │ processor: BeanStageProcessor → bean.process()   │
   │ camel:     CamelStageProcessor → producerTemplate│
   │ batch:     BatchStageProcessor → Job.execute()   │
   └───┬──────────────────────────────────────────────┘
       │
5. Stage output written to PipelineContext.stageOutputs[stageName]
       │
6. PipelineContext advanced (stageIndex++) and set back on exchange
       │
7. If more stages → route to next SEDA queue (step 4)
   If last stage → route to output (channel, camel-uri, log)
       │
8. TaskRecord created/updated for each stage (persistence bridge)
   TaskFlow tracks overall execution
```

### 4.4 Module Placement

New module: `extensions/jaiclaw-pipeline/`

```xml
<dependencies>
    <dependency>
        <groupId>io.jaiclaw</groupId>
        <artifactId>jaiclaw-camel</artifactId>        <!-- PipelineEnvelope, AgentProcessor, SEDA patterns -->
    </dependency>
    <dependency>
        <groupId>io.jaiclaw</groupId>
        <artifactId>jaiclaw-tasks</artifactId>         <!-- TaskFlow/TaskRecord persistence -->
        <optional>true</optional>                       <!-- Optional: persistence layer -->
    </dependency>
    <dependency>
        <groupId>io.jaiclaw</groupId>
        <artifactId>jaiclaw-config</artifactId>        <!-- ConfigurationProperties -->
    </dependency>
    <dependency>
        <groupId>org.springframework.batch</groupId>
        <artifactId>spring-batch-core</artifactId>
        <optional>true</optional>                       <!-- Only needed for batch-chunk stages -->
    </dependency>
</dependencies>
```

A corresponding `jaiclaw-starter-pipeline` in `jaiclaw-starters/` would provide `PipelineAutoConfiguration` following the `JaiClawCamelAutoConfiguration` pattern: `@AutoConfigureAfter` the Camel auto-config, `@ConditionalOnClass(CamelContext.class)`, `ApplicationRunner` for pipeline route registration.

---

## 5. Multi-Tenant Pipeline Isolation

### 5.1 Pipeline Definition Scoping

Each pipeline definition includes a `tenant-ids` field:
- **Empty list** (`[]`) — available to all tenants (global pipeline)
- **Specific IDs** (`[acme, globex]`) — restricted to listed tenants

`PipelineRegistry.getForTenant(tenantId)` filters accordingly. In SINGLE mode (`jaiclaw.tenant.mode: single`), all pipelines are visible regardless of `tenant-ids`.

### 5.2 Per-Tenant Pipeline Files

Beyond `application.yml`, tenant-specific pipelines can be defined in:

```
config/
  tenants/
    acme/
      pipelines.yml       # Acme-specific pipelines
    globex/
      pipelines.yml       # Globex-specific pipelines
```

`PipelineDefinitionLoader` scans these files and assigns `tenantId` automatically. This parallels the per-tenant skill filtering in `jaiclaw-skills`.

### 5.3 Tenant Context in Execution

Following the `CronJobExecutor` pattern (line 37 of `CronJobExecutor.java`):

```java
// At pipeline execution start
if (definition.tenantIds().contains(resolvedTenantId)) {
    TenantContextHolder.set(new DefaultTenantContext(tenantId, tenantId));
}
```

For SEDA queue transitions between stages, wrap the processor with `TenantContextPropagator`:

```java
// In PipelineRouteBuilder, when wiring stage processors
from(sedaUri)
    .process(exchange -> {
        TenantContextPropagator.wrap(() -> {
            stageProcessor.process(exchange, stageDefinition, context);
        }).run();
    });
```

### 5.4 Per-Tenant Concurrency and Queue Sizing

```yaml
jaiclaw:
  pipeline:
    defaults:
      seda-size: 100
      concurrent-consumers: 5
    tenants:
      acme:
        seda-size: 500              # Higher throughput for premium tenant
        concurrent-consumers: 10
      globex:
        seda-size: 50               # Conservative for smaller tenant
```

SEDA endpoint URIs include tenant prefix when multi-tenant: `seda:pipeline-{tenantId}-{pipelineId}-stage-{N}`.

### 5.5 Agent Configuration Resolution

For `agent`-type stages, the agent ID and system prompt are resolved via tenant config. `TenantAgentRuntimeFactory` (referenced in `AgentRuntime.java`) creates per-tenant runtime instances with tenant-specific LLM model configuration. The `AgentStageProcessor` uses the tenant context to obtain the correct agent runtime.

---

## 6. Cross-Instance Federation

### 6.1 When Needed

Federation becomes necessary for:
- **Heterogeneous models** — stage 1 needs GPT-4o on instance A, stage 2 needs Claude on instance B
- **Data locality** — raw data in EU region, summarization in US region
- **Scale-out** — separate worker pools for CPU-intensive vs LLM-intensive stages
- **Isolation** — sensitive data processing on a dedicated instance

### 6.2 Distributed Stage Execution

Replace inter-stage SEDA with Kafka or AMQP for stages that cross instance boundaries:

```yaml
stages:
  - name: research
    type: agent
    agent-id: researcher
    federation:
      execute-on: worker-pool-gpu       # target instance/pool
      transport: kafka                   # kafka | amqp
      topic: pipeline.doc-processor.research
      timeout: 300s
```

### 6.3 Architecture

```
Instance A (Coordinator)              Instance B (Worker)
┌─────────────────────────┐          ┌─────────────────────────┐
│ PipelineRouteBuilder    │          │ StageWorker             │
│                         │          │                         │
│ stage-0 (local)         │          │ Listens on Kafka topic  │
│   │                     │          │   │                     │
│   ▼                     │          │   ▼                     │
│ Kafka Producer ─────────┼────────▶│ AgentStageProcessor     │
│ (serialize context)     │  Kafka   │   │                     │
│                         │          │   ▼                     │
│ Kafka Consumer ◀────────┼──────── │ Kafka Producer          │
│   │                     │  Kafka   │ (serialize result)      │
│   ▼                     │          └─────────────────────────┘
│ stage-2 (local)         │
└─────────────────────────┘
```

**Pipeline Coordinator** — Runs on the orchestrating instance. Builds routes where federated stages produce to Kafka/AMQP instead of local SEDA, and consume results from a response topic.

**Stage Worker** — Runs on remote instances. Consumes from stage-specific topics, executes the stage processor locally, and publishes results back. `PipelineContext` is serialized as JSON for transport.

### 6.4 Why Not Spring Batch Remote Partitioning

Spring Batch's remote partitioning (`PartitionHandler` + `StepExecutionAggregator`) requires a shared `JobRepository` database accessible by all instances. This introduces:
- Mandatory RDBMS dependency (JaiClaw currently uses no relational database for runtime)
- Network latency for step metadata writes
- Schema management overhead
- Tight coupling to Spring Batch's `StepExecution` model

The Kafka/AMQP approach is lighter, uses infrastructure already common in distributed systems, and aligns with Camel's transport-agnostic routing.

---

## 7. Spring Batch Evaluation

### 7.1 Where Spring Batch Helps

**High-volume chunk processing** — When a pipeline stage needs to process 100K+ records (e.g., batch ETL, bulk data import, mass notification), Spring Batch's `ItemReader`/`ItemProcessor`/`ItemWriter` with configurable chunk sizes, skip policies, and retry logic is proven infrastructure.

**Restartability** — `JobRepository` tracks step execution state. A failed batch job can be restarted from the last committed chunk, not from the beginning. Valuable for multi-hour data migrations.

**Built-in readers/writers** — `JdbcCursorItemReader`, `FlatFileItemReader`, `JpaItemWriter`, etc. reduce boilerplate for common data sources.

### 7.2 Where Spring Batch Hurts

**Simple agent pipelines** — A 3-stage agent pipeline (research → analyze → write) processes exactly one item through stages sequentially. Spring Batch's `Job` → `Step` → `Chunk` model adds layers of abstraction (JobLauncher, JobRepository, StepExecution, ExecutionContext) that provide no benefit for single-item flows. The overhead is both runtime (JobRepository writes per step) and cognitive (developers must understand Batch's execution model).

**Streaming/event-driven** — Spring Batch is pull-based: readers pull items in chunks. Camel/SEDA is push-based: messages arrive and are processed reactively. Wrapping a push-based pipeline in a pull-based batch framework creates impedance mismatch — you end up writing adapter code to bridge the two paradigms.

**LLM latency tolerance** — LLM calls take 2–30 seconds. Spring Batch's value proposition (processing thousands of items per second with commit intervals) is irrelevant when each "item" involves an LLM round-trip.

### 7.3 Recommendation

Offer Spring Batch as a **stage type** (`batch-chunk`) within the Camel-orchestrated pipeline, not as the pipeline orchestrator.

```yaml
stages:
  - name: bulk-transform
    type: batch-chunk
    reader:
      type: jdbc
      sql: "SELECT * FROM raw_data WHERE status = 'NEW'"
      datasource: primaryDs
    processor:
      bean: dataTransformer
    writer:
      type: jpa
      entity: TransformedRecord
    chunk-size: 500
    skip-limit: 10
    retry-limit: 3
```

The `BatchStageProcessor` creates a Spring Batch `Job` with a single `Step`, executes it via `JobLauncher`, and writes the job's exit status and summary to `PipelineContext`. The Spring Batch dependency is `<optional>true</optional>` — projects that never use `batch-chunk` stages pay no classpath cost.

### 7.4 Comparison Matrix

| Criterion | Camel-Only | Camel + Batch (recommended) | Batch-Only |
|---|---|---|---|
| Simple agent pipelines | Excellent | Excellent | Over-engineered |
| Streaming / event-driven | Native | Native (Camel for events) | Poor fit |
| High-volume chunk ETL | Manual chunking | Spring Batch stage | Excellent |
| Restartability | Manual (PipelineContext) | Batch stage restartable | Native |
| Infrastructure overhead | None (in-memory SEDA) | Optional RDBMS for batch | Mandatory RDBMS |
| Developer familiarity | Camel knowledge | Both needed (but isolated) | Batch knowledge |
| Multi-tenancy | Full control | Full control (Camel); limited (Batch) | Limited |
| Dependency weight | `camel-core` only | + `spring-batch-core` optional | Heavy |

---

## 8. Implementation Roadmap

### Phase 1 — Foundation (**IMPLEMENTED** — `extensions/jaiclaw-pipeline/`)

**Module**: `extensions/jaiclaw-pipeline/`

| Component | Class | Description |
|---|---|---|
| `PipelineContext` | `io.jaiclaw.pipeline.PipelineContext` | Evolved `PipelineEnvelope` with executionId, tenantId, Map-keyed outputs, metadata |
| `PipelineDefinition` | `io.jaiclaw.pipeline.PipelineDefinition` | Immutable record for YAML-parsed pipeline config |
| `StageDefinition` | `io.jaiclaw.pipeline.StageDefinition` | Record with nested `TransportConfig` and `TransportAuth` for per-stage transport override |
| `PipelineProperties` | `io.jaiclaw.pipeline.PipelineProperties` | `@ConfigurationProperties(prefix = "jaiclaw.pipeline")` with `PipelineDefaults` |
| `PipelineRegistry` | `io.jaiclaw.pipeline.PipelineRegistry` | In-memory registry, tenant-scoped access via `getForTenant()` |
| `StageProcessor` | `io.jaiclaw.pipeline.StageProcessor` | Interface: `process(Exchange, StageDefinition, PipelineContext)` |
| `AgentStageProcessor` | `io.jaiclaw.pipeline.AgentStageProcessor` | Uses `GatewayServiceAccessor.handleSync()`, template substitution |
| `BeanStageProcessor` | `io.jaiclaw.pipeline.BeanStageProcessor` | Invokes Spring `Function<String,String>` bean by name |
| `CamelStageProcessor` | `io.jaiclaw.pipeline.CamelStageProcessor` | Routes to arbitrary Camel endpoint URI via `ProducerTemplate` |
| `PipelineRouteBuilder` | `io.jaiclaw.pipeline.PipelineRouteBuilder` | Builds trigger → stage → ... → output routes in CamelContext |
| `PipelineAutoConfiguration` | `io.jaiclaw.pipeline.PipelineAutoConfiguration` | Two-phase discovery: YAML first, code beans override on ID conflict |
| `JaiClawPipeline` | `io.jaiclaw.pipeline.dsl.JaiClawPipeline` | Abstract class for Java code DSL (analogous to Camel's `RouteBuilder`) |
| `PipelineBuilder` | `io.jaiclaw.pipeline.dsl.PipelineBuilder` | Fluent builder: `pipeline() → trigger() → stage() → output()` |
| `PipelineAuditor` | `io.jaiclaw.pipeline.PipelineAuditor` | Emits `AuditEvent` for pipeline/stage lifecycle via `AuditLogger` SPI |
| `PipelineHookFirer` | `io.jaiclaw.pipeline.PipelineHookFirer` | Fires existing hook names via `HookRunner` for plugin compatibility |
| `PipelineMetrics` | `io.jaiclaw.pipeline.PipelineMetrics` | Micrometer timers/gauges: `jaiclaw.pipeline.executions`, `jaiclaw.pipeline.stage.duration`, `jaiclaw.pipeline.active` |
| `PipelineSecurityGuard` | `io.jaiclaw.pipeline.PipelineSecurityGuard` | Authentication, tenant isolation, input validation, output size cap |
| `PipelineTransportAuthenticator` | `io.jaiclaw.pipeline.PipelineTransportAuthenticator` | HMAC-SHA256 and Bearer Token auth for external transports |
| `TemplateResolver` | `io.jaiclaw.pipeline.TemplateResolver` | Resolves `{{stages.X.output}}` and `{{stages.X.metadata.key}}` placeholders |

**Deliverables**: Dual DSL (YAML + Java code) pipeline definition. Sequential execution with agent, processor, and camel stage types. Configurable inter-stage transport (SEDA default, any Camel URI per-stage). Transport authentication (HMAC-SHA256, Bearer Token). Tenant context propagated across SEDA transitions. Full audit trail, hook events, and Micrometer metrics. Configurable security layer (off by default). Starter: `jaiclaw-starter-pipeline`. 72 Spock tests.

### Phase 2 — Hardening

| Component | Description |
|---|---|
| Error handling | Dead-letter, retry-then-fail, stop strategies per pipeline |
| `BatchStageProcessor` | Spring Batch chunk stage type (optional dependency) |
| Conditional stages | `switch` stage type with expression evaluation |
| Parallel stages | `parallel` stage type with fork/join and configurable join strategies |
| REST API | `GET /api/pipelines`, `POST /api/pipelines/{id}/execute`, `GET /api/pipelines/{id}/executions` |
| CLI commands | `pipeline list`, `pipeline run {id}`, `pipeline status {id}` (Spring Shell) |
| Observability | Micrometer metrics (pipeline.executions, stage.duration), MDC correlation IDs |
| TaskFlow bridge | Write `TaskFlow`/`TaskRecord` entries for each pipeline execution and stage |

**Deliverables**: Production-ready pipeline execution with error recovery, monitoring, and persistence. REST and CLI management interfaces.

### Phase 3 — Federation

| Component | Description |
|---|---|
| `FederatedStageProcessor` | Serializes PipelineContext to Kafka/AMQP, waits for response |
| `StageWorker` | Consumes from topic, executes stage, publishes result |
| `PipelineCoordinator` | Manages distributed execution state |
| Context serialization | JSON serialization of `PipelineContext` for transport |
| Health checks | Worker heartbeats, stage timeout detection, orphan cleanup |

**Deliverables**: Pipeline stages can execute on remote instances via Kafka or AMQP. Pipeline coordinator tracks distributed execution.

---

## 9. Concrete YAML Examples

### 9.1 Document Processing Pipeline

```yaml
jaiclaw:
  pipelines:
    - id: doc-processor
      name: "Document Processor"
      description: "Ingest PDFs, extract text, summarize, and store"
      tenant-ids: []
      enabled: true

      trigger:
        type: file
        uri: "file:data/inbox?include=.*\\.pdf&move=.done&readLock=changed"

      error-strategy: retry-then-fail
      max-retries: 2

      stages:
        - name: parse
          type: processor
          bean: pdfTextExtractor
          timeout: 60s

        - name: summarize
          type: agent
          agent-id: default
          system-prompt: >
            You are a document summarizer. Produce a structured summary
            with: title, key points (bullet list), and a one-paragraph abstract.
          timeout: 120s

        - name: store
          type: camel
          uri: "jpa:io.example.DocumentSummary"

      output:
        type: log
```

### 9.2 Data ETL Pipeline

```yaml
jaiclaw:
  pipelines:
    - id: daily-etl
      name: "Daily Data ETL"
      description: "Extract from source DB, transform with AI, load to warehouse"
      tenant-ids: []
      enabled: true

      trigger:
        type: cron
        expression: "0 2 * * *"

      error-strategy: dead-letter
      dead-letter-uri: "seda:etl-failures"

      stages:
        - name: extract
          type: batch-chunk
          reader:
            type: jdbc
            sql: "SELECT * FROM events WHERE date = CURRENT_DATE - 1"
            datasource: sourceDs
          writer:
            type: memory
          chunk-size: 1000

        - name: transform
          type: agent
          agent-id: data-analyst
          system-prompt: >
            Analyze the following raw event data. Categorize each event,
            extract entities, and output structured JSON.
          timeout: 300s

        - name: load
          type: batch-chunk
          reader:
            type: memory
          writer:
            type: jpa
            entity: WarehouseEvent
          chunk-size: 500

      output:
        type: none
```

### 9.3 Multi-Agent Orchestration

```yaml
jaiclaw:
  pipelines:
    - id: content-pipeline
      name: "Content Creation Pipeline"
      description: "Research, draft, edit, review, and publish content"
      tenant-ids: []
      enabled: true

      trigger:
        type: http
        path: /api/content/create

      error-strategy: stop

      stages:
        - name: research
          type: agent
          agent-id: researcher
          system-prompt: >
            Research the given topic thoroughly. Return structured findings
            with sources, key facts, and relevant statistics.
          timeout: 180s

        - name: draft
          type: agent
          agent-id: writer
          system-prompt: >
            Using the research provided, write a comprehensive article.
            Previous stage output: {{stages.research.output}}
          timeout: 180s

        - name: edit
          type: agent
          agent-id: editor
          system-prompt: >
            Review and improve the following draft. Fix grammar, improve
            clarity, ensure factual accuracy against the research.
            Research: {{stages.research.output}}
            Draft: {{stages.draft.output}}
          timeout: 120s

        - name: review
          type: agent
          agent-id: reviewer
          system-prompt: >
            Final quality review. Score 1-10 on accuracy, clarity, and
            engagement. Approve or request revisions.
          timeout: 60s

      output:
        type: channel
        channel-id: slack
        template: |
          New content ready for publishing:
          Score: {{stages.review.output}}
          ---
          {{stages.edit.output}}
```

### 9.4 Tenant-Isolated Pipeline

```yaml
# config/tenants/acme/pipelines.yml
jaiclaw:
  pipelines:
    - id: acme-invoice-processor
      name: "Acme Invoice Processor"
      description: "Process invoices using Acme's custom model config"
      # tenant-ids auto-set to [acme] by PipelineDefinitionLoader
      enabled: true

      trigger:
        type: file
        uri: "file:data/tenants/acme/invoices?include=.*\\.pdf&move=.done"

      error-strategy: retry-then-fail
      max-retries: 3

      stages:
        - name: extract
          type: processor
          bean: invoiceOcrExtractor
          timeout: 60s

        - name: classify
          type: agent
          agent-id: default         # Resolved via Acme's TenantAgentConfig (uses Acme's LLM)
          system-prompt: >
            Classify this invoice: vendor, amount, currency, due date,
            line items. Output as JSON.
          timeout: 90s

        - name: validate
          type: processor
          bean: invoiceValidator

        - name: submit
          type: camel
          uri: "http:acme-erp.internal/api/invoices"

      output:
        type: channel
        channel-id: acme-notifications
        template: "Invoice processed: {{stages.classify.output}}"
```

---

## 10. Appendix

### 10.1 PipelineContext vs PipelineEnvelope Evolution

| PipelineEnvelope Field | PipelineContext Field | Change |
|---|---|---|
| `pipelineId` | `pipelineId` | Unchanged |
| `correlationId` | `correlationId` | Unchanged |
| `stageIndex` | `stageIndex` | Unchanged |
| `totalStages` | `totalStages` | Unchanged |
| `replyChannelId` | `replyChannelId` | Unchanged |
| `replyPeerId` | `replyPeerId` | Unchanged |
| `stageOutputs` (List\<String\>) | `stageOutputs` (Map\<String, StageOutput\>) | Keyed by stage name, includes metadata and timestamp |
| — | `executionId` | **New**: UUID per pipeline run |
| — | `tenantId` | **New**: tenant isolation |
| — | `metadata` | **New**: propagated key-value pairs |

`PipelineEnvelope` remains in `jaiclaw-camel` for backward compatibility. `PipelineContext` in `jaiclaw-pipeline` extends the concept. A utility method `PipelineContext.fromEnvelope(PipelineEnvelope)` bridges the two.

### 10.2 Module Dependency Placement

```
jaiclaw-core
  ↑
jaiclaw-channel-api
  ↑
jaiclaw-tools
  ↑
jaiclaw-agent
  ↑
jaiclaw-camel (PipelineEnvelope, AgentProcessor, CamelChannelAdapter)
  ↑
jaiclaw-pipeline (NEW — PipelineContext, PipelineRouteBuilder, stage processors)
  ↑ (optional)
jaiclaw-tasks (TaskFlow/TaskRecord persistence bridge)
```

`jaiclaw-pipeline` sits above `jaiclaw-camel` (depends on it) and optionally integrates with `jaiclaw-tasks` for persistence. Spring Batch is an optional dependency, only activated when `batch-chunk` stages are declared.

### 10.3 Cross-Reference Index

| Document | Relevant Section |
|---|---|
| `docs/dev/ARCHITECTURE.md` § High-Level Architecture | Layer placement for jaiclaw-pipeline |
| `docs/dev/ARCHITECTURE.md` § Extension Modules | Registration of jaiclaw-pipeline module |
| `docs/user/OPERATIONS.md` § Skills Configuration | Parallel pattern for pipeline configuration |
| `docs/user/OPERATIONS.md` § Prerequisites | Java 21 + Camel requirements |
| `CLAUDE.md` § Directory Layout | Add jaiclaw-pipeline to extensions list |
| `CLAUDE.md` § Module Dependency Graph | Add jaiclaw-pipeline dependency chain |

---

*Document generated 2026-06-09. References JaiClaw v0.7.1-SNAPSHOT codebase.*

# CoachCallIQ Platform Plan

A multi-tenant college coaching platform built on the JClaw framework

| | |
|---|---|
| **Version** | 1.2 |
| **Date** | March 2026 |
| **Contact** | gregory.lawson@taptech.net |
| **Status** | Planning |

## 1. Vision

CoachCallIQ is a domain-specific coaching intelligence platform built on top of JClaw. It gives college coaching staffs a conversational AI assistant that knows their recruits, enforces compliance, analyzes film, integrates psychological assessments, and operates across every channel coaches already use — Telegram, Slack, Discord, SMS, and email.

JClaw provides the agent runtime, memory, multi-channel messaging, tool execution, skills, and plugin infrastructure. CoachCallIQ provides the coaching domain model, recruiting workflows, video pipeline, and assessment integrations layered on top.

## 2. Multi-Tenancy Architecture

Multi-tenancy is a first-class concern from day one. Every entity, every memory record, every agent session, and every tool execution is scoped to a Program (tenant).

### 2.1 Tenant Model

```
Program (Tenant)
├── programId       UUID — primary partition key
├── name            e.g. "University of Georgia Football"
├── sport           FOOTBALL | BASKETBALL | BASEBALL | ...
├── division        NCAA_D1 | NCAA_D2 | NCAA_D3 | NAIA | NJCAA
├── conference      e.g. "SEC"
├── subscription    TRIAL | STARTER | PRO | ENTERPRISE
└── createdAt
```

### 2.2 Tenancy Strategy

- **Database:** Shared database, schema-per-tenant using Liquibase. Each program gets its own schema (e.g., `program_uga_football`). Tenant resolution happens at the gateway layer via JWT claims.
- **JClaw Memory:** Each JClaw memory store is partitioned by `programId`. Vector store namespaces are tenant-scoped.
- **JClaw Agent Sessions:** Sessions carry `programId` in their metadata. Tools always filter by current tenant context.
- **Channel Connections:** Each program's Telegram bot, Slack workspace, and Discord server maps 1:1 to a JClaw gateway instance configured with that program's `JCLAW_HOME`.
- **LLM Provider:** Programs can configure their own API keys (OpenAI, Anthropic, Ollama). Defaults to platform-level keys on Starter tier.

### 2.3 Staff Roles Within a Tenant

```
StaffMember
├── staffId
├── programId       (tenant scope)
├── name
├── role            HEAD_COACH | COORDINATOR | POSITION_COACH | DIRECTOR_OF_RECRUITING | ANALYST | ADMIN
├── channels        List of connected channel identifiers
└── permissions     EVAL_WRITE | EVAL_READ | RECRUIT_CONTACT | COMPLIANCE_ADMIN | ...
```

Role-based permissions control what each staff member's JClaw agent instance can do within the program.

## 3. Domain Model

### 3.1 Core Entities

**Prospect**

```
Prospect
├── prospectId
├── programId               (tenant scope)
├── firstName / lastName
├── sport / position
├── graduationYear
├── highSchool / location
├── physicalProfile         → PhysicalProfile
├── evaluations             → List<Evaluation>
├── videoReferences         → List<VideoReference>
├── assessmentResults       → List<AssessmentResult>
├── contactLog              → List<ContactEntry>
├── characterNotes          → List<CharacterNote>
├── complianceFlags         → List<ComplianceFlag>
├── academicProfile         → AcademicProfile
├── offerStatus             NONE | OFFERED | COMMITTED | SIGNED | DECOMMITTED
└── recruitingStatus        IDENTIFIED | EVALUATING | ACTIVE | OFFER | CLOSED
```

**PhysicalProfile**

```
PhysicalProfile
├── height / weight
├── fortyYardDash
├── verticalLeap
├── broadJump
├── benchPress
├── shuttleTime
├── wingspan / handSize     (position-specific)
└── combineDate / source
```

**Evaluation**

```
Evaluation
├── evaluationId
├── prospectId / programId
├── evaluatorStaffId
├── evaluationDate
├── location                (game, practice, camp, film)
├── athleticism             1–10
├── technicalSkill          1–10
├── competitiveness         1–10
├── coachability            1–10
├── characterScore          1–10
├── projectedLevel          D1 | D2 | D3 | NAIA
├── notes                   free text → indexed in vector memory
└── recommendation          PURSUE | MONITOR | PASS
```

**VideoReference**

```
VideoReference
├── videoId
├── prospectId / programId
├── title                   human-assigned or auto-extracted label
├── sourceType              YOUTUBE | MP4_UPLOAD | MP4_URL | HUDL | MAXPREPS | TELEGRAM_UPLOAD
├── sourceUri               YouTube URL, MP4 direct URL, or object storage key for uploads
├── durationSeconds
├── fileSizeBytes           (for uploads)
├── uploadedAt
├── submittedByStaffId
├── ingestionStatus         PENDING | DOWNLOADING | EXTRACTING_FRAMES | ANALYZING | INDEXED | FAILED
├── frameExtractionInterval seconds between sampled frames (default: 5)
├── framesExtracted         count
├── transcript              auto-generated if audio present (optional)
├── analysisNotes           → List<VideoAnnotation>
└── summary                 LLM-generated narrative summary
```

**VideoAnnotation**

```
VideoAnnotation
├── annotationId
├── videoId / programId
├── timestampSeconds        which moment in the video
├── frameImageKey           object storage reference to the extracted frame
├── category                TECHNIQUE | ATHLETICISM | DECISION_MAKING | FOOTBALL_IQ | CHARACTER | OTHER
├── observation             LLM-generated text description
├── confidence              0.0–1.0
└── flaggedForReview        boolean — coach can mark for follow-up
```

**Video Source Types and How They Are Submitted**

| sourceType | Example | How Submitted |
|---|---|---|
| YOUTUBE | Highlight reel on YouTube | Scout sends YouTube URL in Telegram/SMS/Slack |
| MP4_UPLOAD | Game film exported from editing software | Scout attaches MP4 directly in Telegram or email |
| MP4_URL | MP4 hosted on a school or cloud drive | Scout sends direct link |
| HUDL | Hudl highlight reel | Scout sends Hudl share link |
| MAXPREPS | MaxPreps game film | Scout sends MaxPreps URL |
| TELEGRAM_UPLOAD | Short clip sent directly in Telegram | Telegram bot receives video message |

**AssessmentResult**

```
AssessmentResult
├── assessmentId
├── prospectId / programId
├── assessmentType          WONDERLIC | PREDICTIVE_INDEX | HOGAN | CUSTOM
├── administeredDate
├── scores                  Map<String, Double>
├── interpretation          LLM-generated synthesis
└── rawDataUrl
```

**ContactEntry**

```
ContactEntry
├── contactId
├── prospectId / programId
├── staffId
├── contactDate
├── contactType             CALL | TEXT | EMAIL | IN_PERSON | UNOFFICIAL_VISIT | OFFICIAL_VISIT
├── channel                 TELEGRAM | SLACK | SMS | EMAIL | DISCORD
├── summary
└── complianceValidated     boolean
```

### 3.2 Resource Abstraction

A Resource is a first-class entity representing any external knowledge artifact that a coaching staff wants the agent to be aware of — a school PDF, a rival program's website, a recruiting blog post, or raw scout notes typed in by a staff member. Resources are ingested, chunked, embedded into the tenant's vector memory, and exposed to the agent via an MCP tool.

**Resource**

```
Resource
├── resourceId
├── programId               (tenant scope)
├── title                   human-assigned label
├── resourceType            PDF | URL | TEXT | HTML | VIDEO_TRANSCRIPT
├── sourceType              UPLOAD | WEB_CRAWL | SCOUT_NOTE | BLOG | RULEBOOK | SCHOOL_PROFILE
├── sourceUri               original URL or upload reference (nullable for TEXT type)
├── rawContent              original text/bytes (stored in object storage for large files)
├── extractedText           cleaned plain text after ingestion
├── ingestionStatus         PENDING | PROCESSING | INDEXED | FAILED
├── linkedProspectId        optional — attach resource to a specific prospect
├── linkedProgramId         optional — attach to a rival/target school program
├── tags                    List<String> — free tagging by staff
├── submittedByStaffId      who added this resource
├── submittedAt
└── expiresAt               optional — auto-purge stale web content
```

**ResourceChunk** (internal, for vector indexing)

```
ResourceChunk
├── chunkId
├── resourceId / programId
├── chunkIndex              sequence within the document
├── text                    the chunk text
├── embedding               stored in pgvector
└── metadata                Map<String, Object> — passed through to retrieval results
```

**Resource Source Types Explained**

| sourceType | Example | How Ingested |
|---|---|---|
| UPLOAD | School's PDF recruiting brochure | Staff uploads file via channel or admin UI |
| WEB_CRAWL | Rival school's athletics website | Staff provides URL; crawler fetches and chunks |
| SCOUT_NOTE | Free-text notes from a camp visit | Staff types or dictates into Telegram/SMS |
| BLOG | Recruiting analyst article on 247Sports | URL provided; article body extracted |
| RULEBOOK | NCAA Division I Manual 2025–26 | Uploaded once per season by compliance admin |
| SCHOOL_PROFILE | Academic profile PDF for a prospect's high school | Attached during prospect evaluation |

## 4. Module Structure

```
coachcalliq-parent
├── coachcalliq-domain              Core entities, repositories, tenant context
├── coachcalliq-security            JWT auth, tenant resolution, role enforcement
├── coachcalliq-tools               JClaw tool implementations (prospect, compliance, eval, resource)
├── coachcalliq-skills              JClaw skill packs (recruiting, compliance, gameprep)
├── coachcalliq-resources           Resource ingestion pipeline, MCP tool exposure, vector indexing
├── coachcalliq-agents              Embabel agent definitions (evaluation, video, compliance, recruiting)
├── coachcalliq-video               Video ingestion, vision LLM pipeline, annotation storage
├── coachcalliq-assessments         Psych/physical test connectors (Wonderlic, PI, custom)
├── coachcalliq-recruiting          Workflow engine, contact logging, offer management
├── coachcalliq-compliance          NCAA rules engine, dead period calendar, violation alerts
├── coachcalliq-channels            Email adapter, SMS/Twilio adapter (extends jclaw-channel-api)
├── coachcalliq-gateway-app         Runnable gateway — extends jclaw-gateway-app
├── coachcalliq-admin               Tenant onboarding, billing, staff management UI
└── coachcalliq-spring-boot-starter Auto-configuration for the full platform
```

## 5. JClaw Integration Points

### 5.1 Spring Boot Starter

CoachCallIQ imports `jclaw-spring-boot-starter` and extends it. The coaching domain tools and skills are registered automatically via Spring's component scanning and JClaw's plugin discovery mechanism.

### 5.2 Tools Registration

Each `coachcalliq-tools` tool implements JClaw's tool SPI and is registered in the tool registry at startup, scoped to the current tenant:

- **ProspectLookupTool** — find and summarize a prospect by name/position/class
- **EvaluationCreateTool** — log a new evaluation from conversational input
- **EvaluationSummaryTool** — summarize all evaluations for a prospect across staff
- **VideoAnalysisTool** — trigger analysis of a video reference
- **ComplianceCheckTool** — validate whether a planned contact is compliant
- **ContactLogTool** — record a coach-prospect contact
- **ProspectCompareTool** — side-by-side comparison of 2–5 prospects
- **AssessmentSummaryTool** — retrieve and interpret psych/physical scores
- **ResourceQueryTool** — semantic search across all indexed resources for the tenant
- **ResourceIngestTool** — submit a new resource (URL, PDF, or text) for indexing

### 5.3 Resource MCP Tool

`coachcalliq-resources` exposes an MCP server that makes the resource index queryable by any MCP-compatible client — including JClaw's own agent and any external tools the staff connects. This is the key architectural decision: resources are not locked inside CoachCallIQ's internal tool registry. They are exposed as a standard MCP resource server, making them composable with other agents and tools.

**MCP Server:** `coachcalliq-resource-server`

**MCP Endpoint:** `/mcp/resources`

**Tools exposed:**

```
resource_search(query, filters?)
  → semantic search over all tenant resources
  → filters: sourceType, tags, linkedProspectId, linkedProgramId, dateRange

resource_get(resourceId)
  → retrieve full extracted text + metadata for a specific resource

resource_ingest(type, content, metadata?)
  → submit new resource for processing
  → type: PDF_URL | WEB_URL | TEXT
  → returns resourceId + estimated completion time

resource_list(filters?)
  → list all resources for the tenant with status
```

**How the agent uses it:** When a coach asks "what does Georgia's recruiting page say about their linebacker needs?" the agent calls `resource_search("Georgia linebacker recruiting needs")` against the MCP server, which does a vector similarity search over all indexed resources scoped to that tenant and returns the most relevant chunks. The agent synthesizes those chunks into a natural language answer.

**How staff submit resources:**

- **Via Telegram (PDF):** Scout attaches a PDF file directly in Telegram — same gesture as sending a photo. Agent detects the attachment, confirms the prospect it should be linked to (from context, filename, or by asking), and initiates ingestion. No web portal needed.
- **Via Telegram (URL):** "Add this URL as a resource: https://georgiadogs.com/recruiting" → ResourceIngestTool fetches and indexes the page.
- **Via SMS/MMS:** Scout sends a PDF or image as an MMS message. Twilio delivers the media URL; the adapter downloads and routes it to the ingestion pipeline.
- **Via email:** Staff forwards a PDF scouting report to the program's CoachCallIQ email address. The email adapter extracts the attachment and ingests it.
- **Via admin UI:** Upload a PDF directly for bulk or sensitive documents.
- **Automated:** Compliance rulebook updated each season via scheduled crawl.

**PDF prospect linking:** When a PDF arrives via any channel, the agent resolves which prospect to link it to using three strategies in order — explicit mention in the message ("this is for Marcus Thompson"), filename parsing (`Marcus_Thompson_Report.pdf`), or active session context. If none resolve, the agent asks before indexing.

### 5.4 Skills Registration

CoachCallIQ skill packs extend `jclaw-skills`:

- **Recruiting Skill** — understands recruiting lifecycle, contact windows, offer timing
- **Compliance Skill** — NCAA division-specific rules, dead periods, quiet periods, contact limits
- **Evaluation Skill** — structured evaluation rubrics per sport and position
- **Game Prep Skill** — opponent analysis, depth chart comparison, practice planning

### 5.5 Memory Partitioning

CoachCallIQ configures JClaw's memory store with `programId` as the partition key. All vector embeddings (evaluation notes, video summaries, character observations) are stored and searched within the tenant's namespace only.

### 5.6 Channel Instances

Each program's JClaw gateway instance is configured at onboarding with:

- Program-specific `JCLAW_HOME`
- Connected channel tokens (Telegram bot, Slack workspace, Discord server)
- Staff member → channel identity mappings
- LLM provider config

## 6. Embabel Integration

### 6.1 Role Division: JClaw vs Embabel

JClaw and Embabel serve complementary roles in CoachCallIQ. They are not alternatives — they solve different problems and both build on Spring AI.

| Concern | JClaw | Embabel |
|---|---|---|
| Channel messaging (Telegram, SMS, email) | Yes | No |
| Gateway + webhook routing | Yes | No |
| Multi-channel session management | Yes | No |
| Memory / vector store | Yes | No |
| Plugin & skill loading | Yes | No |
| Simple conversational tool calls | Yes | Yes |
| Complex multi-step agentic workflows | No | Yes |
| Goal-Oriented Action Planning (GOAP) | No | Yes |
| Per-step LLM selection (cheap vs. powerful) | No | Yes |
| Strongly-typed domain model flows | No | Yes |
| Replanning after each action | No | Yes |

**The practical split:** JClaw receives the coach's message and manages the session. Embabel executes the complex reasoning workflow triggered by that message. For simple lookups (`ProspectLookupTool`), JClaw's tool registry handles it directly. For multi-step workflows (full prospect evaluation, video analysis pipeline, compliance audit), JClaw delegates to an Embabel agent.

### 6.2 How They Connect

JClaw calls Embabel via a new `AgentOrchestrationTool` in `coachcalliq-tools`. When a message requires a complex workflow, the JClaw agent invokes this tool, which starts an Embabel `AgentProcess` and awaits the result:

```java
@Tool("orchestrate_evaluation")
public EvaluationResult orchestrateEvaluation(String prospectId, String rawNotes) {
    ProspectEvaluationInput input = new ProspectEvaluationInput(prospectId, rawNotes,
                                       TenantContextHolder.get());
    return agentPlatform.run(input, EvaluationResult.class);
}
```

Embabel's `AgentPlatform.run()` takes a typed input domain object and a goal type, plans the steps needed, and returns a typed result. JClaw then formats that result into a natural language response for the coach's channel.

### 6.3 CoachCallIQ Embabel Agents

Each complex workflow in CoachCallIQ becomes an Embabel `@Agent`:

**ProspectEvaluationAgent** — Orchestrates a full talent evaluation from raw scout notes. Steps might include: parse physical observations → compare against position benchmarks → query historical evaluations in memory → synthesize a recommendation. Different LLMs for different steps — a cheap model for parsing, a powerful model for final synthesis.

```java
@Agent(description = "Synthesizes a complete prospect evaluation from raw scout input")
public class ProspectEvaluationAgent {

    @Action
    public ParsedObservations parseScoutNotes(RawScoutInput input, PromptRunner promptRunner) {
        return promptRunner.createObject(input, ParsedObservations.class,
            Llm.byName("gpt-4o-mini")); // cheap model for parsing
    }

    @Action
    public BenchmarkComparison compareToPosition(ParsedObservations obs, ProspectRepository repo) {
        // pure code — no LLM needed
        return repo.findBenchmarks(obs.position(), obs.division());
    }

    @Action
    public EvaluationRecommendation synthesize(ParsedObservations obs,
                                                BenchmarkComparison bench,
                                                PromptRunner promptRunner) {
        return promptRunner.createObject(
            new EvaluationSynthesisInput(obs, bench),
            EvaluationRecommendation.class,
            Llm.byName("claude-sonnet")); // powerful model for final judgment
    }

    @Goal
    public boolean evaluationComplete(EvaluationRecommendation rec) {
        return rec != null && rec.confidence() > 0.7;
    }
}
```

**VideoAnalysisAgent** — Orchestrates the full video ingestion pipeline: detect source type → download → extract frames → batch-analyze with vision LLM → generate narrative summary → store annotations. Each stage is an `@Action`. Embabel replans if a stage fails (e.g., YouTube download fails → try alternate source).

**ComplianceAuditAgent** — Multi-step compliance review: identify planned contact → check division rulebook resource → check dead period calendar → check prospect's contact log → produce a compliance verdict with reasoning. Each step uses the appropriate tool and LLM.

**RecruitingClassAgent** — Synthesizes a full recruiting class snapshot across all prospects — aggregates evaluations, identifies positional gaps, suggests prioritization. Uses cheaper models for data gathering, powerful model for strategic synthesis.

### 6.4 Per-Step LLM Selection

One of Embabel's most practical features for CoachCallIQ is the ability to use different LLMs for different steps in the same workflow. This directly controls cost:

- **Parsing, extraction, classification** → GPT-4o Mini or Haiku (cheap, fast)
- **Compliance rule lookup, factual retrieval** → local Ollama model (free, private)
- **Final synthesis, recommendations, narrative summaries** → GPT-4o or Claude Sonnet (best quality)
- **Vision frame analysis** → GPT-4o Vision (no cheaper substitute yet)

A full prospect evaluation might cost $0.003 total versus $0.04 if every step used GPT-4o.

### 6.5 New CoachCallIQ Module

`coachcalliq-agents` — Embabel agent definitions for CoachCallIQ workflows:

- ProspectEvaluationAgent
- VideoAnalysisAgent
- ComplianceAuditAgent
- RecruitingClassAgent
- ScoutNoteIngestionAgent — parses free-text scout notes into structured `ParsedObservations`

## 7. Phased Delivery Plan

### Phase 1 — Foundation (Months 1–2)

**Goal:** Coaches can log evaluations, look up prospects, and submit scout notes conversationally via Telegram.

- [ ] `coachcalliq-domain` — Prospect, Evaluation, PhysicalProfile, Resource entities + repositories
- [ ] `coachcalliq-security` — Tenant resolution, JWT, role-based access
- [ ] `coachcalliq-tools` — ProspectLookupTool, EvaluationCreateTool, EvaluationSummaryTool
- [ ] `coachcalliq-resources` — TEXT and PDF ingestion pipeline, vector indexing, ResourceQueryTool, ResourceIngestTool
- [ ] `coachcalliq-resources` MCP server — resource_search, resource_ingest, resource_get
- [ ] `coachcalliq-agents` — ScoutNoteIngestionAgent (first Embabel agent, simplest workflow)
- [ ] `coachcalliq-spring-boot-starter` — auto-configuration wiring
- [ ] Telegram channel live for first pilot program
- [ ] Liquibase migrations for schema-per-tenant
- [ ] Basic onboarding wizard (extends JClaw `onboard` command)

**Success metric:** A coach can say "log eval for Marcus Thompson — 9 athleticism, 7 technique, great motor, pursue" and retrieve a full staff summary the next day. A scout can submit a camp visit note via Telegram and have it searchable within minutes.

### Phase 2 — Compliance & Recruiting Workflow (Months 2–3)

**Goal:** Compliance anxiety eliminated. Contact logging automatic.

- [ ] `coachcalliq-compliance` — NCAA rules engine, division-specific calendar, dead period alerts
- [ ] `coachcalliq-recruiting` — Contact log, offer tracking, recruiting status workflow
- [ ] `coachcalliq-tools` — ComplianceCheckTool, ContactLogTool
- [ ] `coachcalliq-skills` — Compliance Skill, Recruiting Skill
- [ ] Proactive compliance alerts pushed to coach's Telegram/Slack

**Success metric:** Zero compliance violations for pilot programs during a contact period.

### Phase 3 — Video Pipeline (Months 3–5)

**Goal:** Coaches get AI-assisted film analysis for YouTube links and MP4 uploads attached to prospect profiles.

- [ ] `coachcalliq-video` — full ingestion pipeline with source-type routing
- [ ] YouTube ingestion — URL detection → yt-dlp download → frame extraction → vision LLM analysis
- [ ] MP4 upload ingestion — Telegram/email attachment → object storage → frame extraction → vision LLM analysis
- [ ] MP4 direct URL ingestion — remote URL download → same pipeline as upload
- [ ] Hudl share link support — scrape/download → pipeline
- [ ] Frame extraction via FFmpeg (configurable interval, default every 5 seconds)
- [ ] Optional audio transcription (Whisper via Spring AI or Ollama) for sideline audio, coach commentary
- [ ] Vision LLM integration via Spring AI multimodal (GPT-4o Vision or LLaVA local)
- [ ] VideoAnnotation storage + indexing in JClaw memory
- [ ] VideoAnalysisTool — trigger and retrieve analysis conversationally
- [ ] VideoReferenceTool — link a video to a prospect by URL or attachment
- [ ] Video summary auto-appended to prospect profile

**Scout workflow (YouTube):**

```
Scout (Telegram): "Add this highlight film for DeShawn Williams"
                  https://www.youtube.com/watch?v=abc123
Agent: "Got it — downloading DeShawn's highlight reel now.
        Analysis will be ready in about 3 minutes."
[3 minutes later]
Agent: "DeShawn's film is ready. Here's the summary:
        Strong first-step explosion off the line, consistently wins
        inside leverage. Footwork breaks down in space at ~2:14 and 4:38.
        Recommend watching those two clips with the D-line coach."
```

**Scout workflow (MP4 upload):**

```
Scout (Telegram): [attaches game_film_oct12.mp4]
                  "This is Williams from the Oak Ridge game last Friday"
Agent: "Received the Oak Ridge game film. Linking to DeShawn Williams.
        Processing now — I'll ping you when the analysis is done."
```

**Success metric:** Coach sends a YouTube link or MP4 and gets back timestamped, position-specific annotations within 5 minutes.

### Phase 4 — Assessments + Email/SMS (Months 5–6)

**Goal:** Full picture on every prospect. Coaches reachable on every channel.

- [ ] `coachcalliq-assessments` — Wonderlic connector, Predictive Index connector, custom upload
- [ ] AssessmentSummaryTool — conversational psych score retrieval and interpretation
- [ ] `coachcalliq-channels` — Email adapter (IMAP/SMTP + Gmail MCP), SMS adapter (Twilio)
- [ ] Cross-assessment comparative analysis skill

**Success metric:** Coach asks "compare the Wonderlic and character scores for our top 3 safeties" and gets a synthesized recommendation.

### Phase 5 — Multi-Program Scale + Admin (Month 6+)

**Goal:** Platform ready for commercial rollout.

- [ ] `coachcalliq-admin` — tenant onboarding UI, billing integration, staff management
- [ ] Program-level analytics dashboard (evaluation trends, recruiting class health)
- [ ] API for third-party integrations (Front Rush, Rivals, 247Sports data feeds)
- [ ] Enterprise tier with dedicated LLM instances

## 8. Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.5 |
| AI Framework | Spring AI 1.1 |
| Channel & Gateway | JClaw (glawson6/jclaw) |
| Agentic Orchestration | Embabel (embabel/embabel-agent) |
| LLM Providers | OpenAI GPT-4o, Anthropic Claude, Ollama (local) |
| Vision LLM | GPT-4o Vision / LLaVA via Ollama |
| Database | PostgreSQL (schema-per-tenant) |
| Migrations | Liquibase |
| Vector Store | pgvector (tenant-namespaced) |
| Messaging Channels | Telegram, Slack, Discord (via JClaw), Email, SMS |
| SMS | Twilio |
| Video Ingestion | YouTube (yt-dlp), MP4 upload/URL, Hudl, MaxPreps |
| Video Processing | FFmpeg (frame extraction), Whisper (audio transcription) |
| Container | Docker, Kubernetes |
| CI/CD | GitHub Actions + ArgoCD |
| Auth | JWT, Spring Security |

## 9. Open Questions

- **Licensing** — JClaw is licensed for personal/small org use. Commercial use of CoachCallIQ requires a commercial license from TapTech. Clarify terms before first paying customer.
- **FERPA Compliance** — Prospect data for minors may trigger FERPA obligations. Legal review needed before handling academic records.
- **NCAA Data Rules** — Confirm that storing and AI-analyzing prospect data doesn't violate any NCAA data/recruiting rules for the program.
- **Video Rights** — Hudl, MaxPreps, and YouTube data usage rights for AI analysis need verification. YouTube Terms of Service restrict automated downloading; yt-dlp usage should be reviewed by counsel. Consider requiring coaches to confirm they have rights to any uploaded film.
- **First Pilot Program** — Identify a single program willing to be the guinea pig for Phase 1. Ideally a D1 recruiting coordinator who is already Telegram-native.

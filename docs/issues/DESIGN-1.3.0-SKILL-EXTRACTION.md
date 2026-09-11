# Design note — batch skill extraction over the audit corpus (1.3.0)

*Written 2026-09-10, after the 1.2.0 learning loop shipped and was reviewed.
Companion to [`IMPLEMENTATION-PLAN-1.3.0.md`](./IMPLEMENTATION-PLAN-1.3.0.md).
Status: **proposed, not scheduled.** Read §7 before building anything.*

---

## 1. What this replaces, and why

1.2.0 ships a learning loop that reviews a **live session** immediately after a
turn ends: `AgentEndedEvent` → cadence gate → render the in-memory transcript →
one LLM call → proposals. It works, it is specced, and it is off by default.

Reviewing it end-to-end surfaced three structural problems that are properties of
the *trigger*, not of the implementation:

1. **It asks an unanswerable question.** A reviewer looking at one conversation
   must decide "is anything here durable?" from a single sample. Durability is a
   claim about recurrence, and recurrence is not visible in one transcript.
2. **It reads live session state.** That required two invariants and dedicated
   specs to keep the provider's prompt cache intact. The risk is managed, but it
   exists only because the reviewer touches a running session at all.
3. **It cannot be evaluated.** Changing the prompt means waiting for live traffic
   and hoping. There is no way to re-run the same input and compare.

Batch extraction over an archived corpus fixes all three, because it changes what
the extractor is looking at rather than how carefully it looks.

| | 1.2.0 live review | Proposed batch extraction |
|---|---|---|
| Trigger | Post-turn, on a cadence | Operator- or schedule-initiated |
| Input | One live session | N archived transcripts |
| Question it can ask | "Is this durable?" | "What recurs?" |
| Cache risk | Reads a live session; managed by two invariants | Reads disk; cannot touch a live session by construction |
| Cost shape | Per qualifying session, forever | Per run, when you choose |
| Evaluable | No | **Yes** — fixed corpus, re-runnable |
| Provenance | Exact originating session | Coarser: a set of sessions |

## 2. Why audit is a *strict* dependency

**`jaiclaw-audit` moves from optional to required for this feature.** That is a
deliberate change from 1.2.0 and the single most important constraint in this
note.

Today in `extensions/jaiclaw-learning/pom.xml`:

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-audit</artifactId>
    <optional>true</optional>   <!-- 1.2.0: transcripts fall back to SessionManager -->
</dependency>
```

The 1.2.0 reviewer degrades gracefully without audit because it can read the live
`SessionManager`. **A batch extractor has no such fallback.** There is nothing to
batch over if transcripts were never archived — live sessions are ephemeral, and
in the default `InMemorySessionManager` they now evict under the retention bounds
added alongside this note.

So the requirement is not stylistic. It is:

- **Compile-time:** `jaiclaw-audit` becomes a non-optional dependency of whatever
  module hosts the extractor.
- **Runtime:** a `TranscriptStore` bean must exist. Note that
  `FileTranscriptStore` currently has **no autoconfiguration** — adopters wire it
  by hand. The extractor's autoconfiguration must therefore either provide one or
  fail loudly.
- **Operational:** archiving must have been **on for long enough to accumulate a
  corpus**. This is the requirement that will actually bite, because it is
  invisible at startup. A deployment that enables extraction today and archiving
  today has nothing to extract from for weeks.

### Failing loudly

The extractor must refuse to start rather than silently produce nothing:

| Condition | Behaviour |
|---|---|
| `jaiclaw-audit` absent from classpath | Autoconfiguration does not activate; log at INFO why |
| No `TranscriptStore` bean | **Fail startup** with a message naming the missing bean and the property that enables archiving |
| Store present but empty | Start normally; first run logs "0 transcripts — archiving may be newly enabled" and does nothing |
| Fewer than `min-corpus-size` transcripts | Skip the run, log the count and the threshold |

The third and fourth rows matter more than they look. "Extraction is on and
finding nothing" and "extraction is on and has nothing to look at" are different
situations, and an operator must be able to tell them apart without reading code.

### Retention interaction

Transcript retention (`TranscriptRetentionSweeper`, shipped alongside this note)
and extraction pull in opposite directions: retention deletes old transcripts,
extraction wants history. The interaction must be explicit:

- Extraction **never** resurrects swept data — it sees only what retention kept.
- The docs must state plainly that `retention-window < extraction-lookback` means
  the lookback is silently truncated to the window.
- Consider warning at startup when configured that way, rather than letting an
  operator believe they are extracting over 90 days of a 30-day store.

## 3. What survives from 1.2.0

Most of it. The 1.2.0 module was built around *"a proposal exists"*, and nothing
downstream of that cares where the proposal came from.

**Reusable unchanged:**

| Component | Why it carries over |
|---|---|
| `Proposal` hierarchy, `ProposalState`, content hashing | Storage and decision model, trigger-agnostic |
| `ProposalStore` / `JsonFileProposalStore` / `ProposalService` | Same queue, same state machine, same dedupe |
| `ProposalApplier`, `MemoryProposalApplier`, `SkillProposalApplier` | Applying is identical |
| `SkillWriter`, `LearnedSkillSidecar`, `LearningLedger`, `BlobStore` | Write + rollback path unchanged |
| `SkillCurator`, `SkillUsageTracker`, `CuratorScheduler` | Lifecycle is downstream of application |
| `LearningSelectivity` | Thresholds and caps apply to batch equally |
| `SkillLoader.loadLearned` | Loading is unchanged |

**Retired:**

| Component | Why |
|---|---|
| `ReviewTrigger` | The live post-turn trigger — the thing being replaced |
| `TranscriptSourceAdapter` | Reads live sessions; the batch path reads the audit store |
| `ReviewCadenceGate` | Per-session cadence is meaningless for a batch run |

**New:**

- `TranscriptCorpus` — reads and filters archived transcripts by tenant and date
- `BatchSkillExtractor implements LearningReviewer` — the existing one-method SPI
- `ExtractionScheduler` — operator-triggered or cron, following `CuratorScheduler`
- `ExtractionRun` — a record of what was extracted from which corpus slice, for
  provenance now that it is no longer one session

`LearningReviewer` staying the SPI boundary is what makes this a swap rather than
a rewrite: both implementations produce `ReviewOutcome`, and `ProposalService`
never learns the difference.

## 4. Sketch

```java
/** Reads archived transcripts. The corpus the extractor reasons over. */
public interface TranscriptCorpus {
    List<TranscriptSession> forTenant(String tenantId, Instant since, int limit);
    int size(String tenantId, Instant since);
}
```

```yaml
jaiclaw:
  learning:
    mode: propose
    extraction:
      enabled: false          # off by default, as with everything in this module
      lookback: 30d           # truncated to the retention window if shorter
      min-corpus-size: 20     # below this, skip the run and say so
      max-transcripts: 200    # cap tokens per run
      schedule: weekly        # or operator-triggered only
```

Proposals from a batch run carry the **set** of contributing session ids rather
than a single `originSessionKey`. That is a real loss of provenance and should be
recorded in the proposal, not papered over.

## 5. Evaluation — the actual point

This is the argument for building it at all.

A fixed corpus makes extraction **testable**, which the live reviewer can never
be. The workflow it unlocks:

1. Freeze a corpus slice.
2. Run extraction. Record the proposals.
3. Change the prompt, the selectivity, or the model.
4. Re-run over the *same* slice. Diff the proposals.
5. Keep the change if it produced better ones.

That is an evaluation loop. It does not require live traffic, it is repeatable,
and it turns "does this feature work?" from an opinion into a measurement.

It is also how the two implementations get compared honestly: run the 1.2.0 live
reviewer and the batch extractor over the same sessions and look at what each
proposes.

## 6. Open questions

| Question | Notes |
|---|---|
| Does batch actually produce better proposals? | **Unknown.** The whole premise. Measure before committing. |
| Corpus slicing | By time, by tenant, by outcome? Outcome needs §7 first. |
| PII in a durable corpus | `jaiclaw-compliance` ships `PromptRedactor`; it is **not** wired into the 1.2.0 reviewer and should be wired into this one before any transcript leaves the process. |
| GDPR erasure | An erasure request must reach archived transcripts. `AggregateDataSubjectErasureSpi` exists; does it cover `TranscriptStore`? Verify. |
| Cost per run | 200 transcripts is a large prompt. Chunk, or map-reduce across slices. |

## 7. Prerequisite: an outcome signal

**Do not build §4 first.**

Batch extraction improves *how* proposals are generated. It does nothing about
the gap that has been flagged at every review of this feature: **we cannot tell
whether an applied skill helped.** `useCount` counts prompt inclusion, not
success. Without a signal, batch extraction is the same unvalidated guess — just
cheaper and better governed.

Build in this order:

1. **Outcome signal.** A hook, a thumbs-up/down, a task-completed marker — some
   ground truth that a skill contributed to a good result.
2. **Batch extractor** over the audit corpus (§4).
3. **Compare** against the 1.2.0 live reviewer on the same corpus.
4. **Keep whichever wins.** Retire the other.

Skipping to step 2 gets a better-engineered version of something whose value is
still unmeasured.

## 8. Recommendation

- **1.2.0:** ship the live loop as-is, `mode: off`. It costs nothing when
  disabled and is fully specced.
- **1.3.0:** build the outcome signal. Treat this note as the design for what
  follows, not as scheduled work.
- **Decide later:** whether batch replaces the live path or complements it.
  Measurement should decide that, not this note.

## 9. Related

- [`docs/user/LEARNING.md`](../user/LEARNING.md) — the shipped 1.2.0 loop
- [`IMPLEMENTATION-PLAN-1.2.0.md`](./IMPLEMENTATION-PLAN-1.2.0.md) §9 — as built
- [`IMPLEMENTATION-PLAN-1.3.0.md`](./IMPLEMENTATION-PLAN-1.3.0.md) — 1.3.0 scope
- `TranscriptRetentionSweeper` — retention, and its interaction with §2
- `SessionRetentionPolicy` — why live sessions cannot serve as the corpus

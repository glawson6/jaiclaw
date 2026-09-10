# jaiclaw-learning

Turns finished conversations into **proposals** — a memory entry, a new skill, or
a patch to an existing one — that an operator reviews and applies.

**Opt-in.** With `jaiclaw.learning.mode: off` (the default) not a single bean in
this module is created, so it is safe to keep on the classpath.

## Quick start

```yaml
jaiclaw:
  learning:
    mode: propose          # off | propose | auto
    selectivity: balanced  # conservative | balanced | eager
```

## What it does

```
turn ends → AgentEndedEvent → cadence gate → [virtual thread]
    → render transcript (read-only)
    → one bounded LLM call
    → proposals → tenant-scoped store
    → propose: an operator applies    auto: memory + new skills apply immediately
```

## Two invariants

1. **A review never mutates the live session.** Appending would invalidate the
   provider's cached prompt prefix and make every later turn more expensive.
2. **An applied skill loads in the *next* session, not the one that proposed
   it.** Swapping instructions under a running conversation would both break the
   cache and confuse the model about rules that changed mid-task.

Both are asserted in `LearningLoopE2ESpec`.

## Layout

| Package | Role |
|---|---|
| `proposal` | Sealed `Proposal` hierarchy, state machine, tenant-scoped JSON store |
| `review` | `LearningReviewer` SPI, LLM implementation, cadence gate, trigger |
| `apply` | Memory and skill appliers |
| `skill` | `SkillWriter`, sidecar, lifecycle |
| `ledger` | Append-only JSONL + content-addressed blobs for rollback |
| `curator` | Ages unused learned skills |

## Extension points

- `LearningReviewer` — replace the review pass entirely
- `ProposalStoreProvider` — swap the JSON store for JDBC/Redis
- `ProposalApplier` — change how a proposal is applied

All marked `@Experimental` pending adopter feedback.

## Docs

[`docs/user/LEARNING.md`](../../docs/user/LEARNING.md)

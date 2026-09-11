# Learning Loop

Turns finished conversations into **proposals** — a memory entry, a new skill, or
a patch to an existing one — that an operator reviews and applies.

Introduced in 1.2.0 as the opt-in `jaiclaw-learning` extension. **Off by
default**: with `mode: off` (the default) not a single bean in the module is
created, so it is safe to keep on the classpath in a deployment that does not
want it.

## Why `propose` is the default

Hermes and OpenClaw default to applying learned changes automatically. JaiClaw
does not, and the reason is the tenant story: an adopter embedding this framework
in a service must **opt in** to an agent rewriting its own skills. A framework
that silently edited its own behaviour on first run would be unusable in a
multi-tenant deployment, where the blast radius of a bad lesson is every user of
that tenant.

`auto` exists for the personal-assistant case. Even there, **skill patches stay
manual** unless you explicitly allow them — creating a skill is additive, editing
one is destructive.

## Configuration

```yaml
jaiclaw:
  learning:
    mode: propose            # off | propose | auto   (default: off)
    selectivity: balanced    # conservative | balanced | eager
    review-min-turns: 0      # 0 = use the selectivity default
    review-min-interval:     # unset = use the selectivity default
    max-transcript-chars: 12000
    proposals-dir: ${user.home}/.jaiclaw/learning
    skills-dir: ${user.home}/.jaiclaw/skills/learned
    auto-allow-patches: false
    curator-enabled: true
    curator-stale-after: 30d
    curator-archive-after: 90d
```

## How it works

```
turn ends → AgentEndedEvent → cadence gate → [virtual thread]
    → render transcript (read-only)
    → one bounded LLM call
    → proposals → tenant-scoped store
    → propose: wait for an operator
      auto:    apply memory + new skills immediately
```

### The two invariants

**1. A review never touches the live session.** The reviewer reads a rendered
transcript; it does not append to the message list or alter the system prompt.
This is what keeps the provider's prompt cache intact — appending would
invalidate the cached prefix and make every later turn more expensive.
`LearningLoopE2ESpec` asserts the message list is byte-identical after a review.

**2. Applying a skill does not affect the session that proposed it.** Skills are
picked up by `SkillLoader` at session start. A skill applied mid-conversation
appears in the *next* session, deliberately — swapping instructions underneath a
running conversation would invalidate the cache and confuse the model about rules
that changed mid-task.

## Selectivity

How eagerly the reviewer proposes. Three named levels rather than a 0.0–1.0
float, because this is a **policy over a queue a human reads**, not a sampling
parameter — turning it up does not produce bolder insights, it produces more
proposals, and the failure mode is a queue nobody reads. Once that happens,
`propose` mode has silently degraded into `auto` mode without anyone deciding to.

| Level | Min turns | Interval | Max per review | Use when |
|---|---|---|---|---|
| `conservative` | 6 | 15m | 2 | The queue is read by someone whose time is expensive, or a wrong skill is costly |
| `balanced` *(default)* | 4 | 5m | 5 | General use — reproduces pre-1.2.0 behaviour exactly |
| `eager` | 2 | 1m | 8 | Personal assistant, or an evaluation run to see what the reviewer surfaces |

Each level also varies the **threshold stated in the reviewer prompt** — that is
the real knob; the caps just bound the damage.

Setting `review-min-turns` or `review-min-interval` explicitly overrides the
level, so you can pick a level and still tune one dimension of it.

> **This is not the LLM's sampling temperature, and is deliberately not wired to
> it.** The review call is structured JSON extraction; raising sampling
> temperature there yields malformed output and invented skill names, not better
> judgement. Selectivity moves the threshold while the model keeps sampling
> conservatively.

`eager` is a poor fit for `mode: auto` in a multi-tenant deployment: you would be
combining "propose freely" with "apply without review".

## Cost control

A review is an LLM call, so three things bound it:

| Control | Effect |
|---|---|
| `review-min-turns` | Short sessions are never reviewed |
| `review-min-interval` | One review per session per window, however chatty |
| `max-transcript-chars` | Transcript truncated head+tail (opening states the goal, closing shows the resolution) |

Point reviews at a cheap model by defining a `ChatModel` bean for the reviewer to
pick up — it does not have to be the model serving conversations.

## Proposals

| Kind | Applies to |
|---|---|
| `MEMORY` | Appends an entry under a heading in the agent's memory |
| `SKILL` | Writes a new `SKILL.md` under `{skills-dir}/{tenant}/{name}/` |
| `SKILL_PATCH` | Unique exact-span replace inside an existing learned skill |

States: `PENDING → APPLIED | REJECTED`, and `APPLIED → ROLLED_BACK`. Nothing
leaves `REJECTED` — re-deciding an operator's rejection is exactly the autonomy
this module avoids. A rejected proposal *can* be suggested again later, because
the situation may have changed.

Duplicate suggestions are dropped by content hash, so a reviewer that keeps
noticing the same pattern does not grow an unreadable queue. The tenant is part
of that hash, so one tenant's dedupe can never suppress another's.

## Safety properties

- **Memory is appended, never replaced.** Memory accumulated over months is the
  user's; a learning pass that overwrote it wholesale could destroy far more than
  it added.
- **Patches must match a unique span.** If `findText` appears zero or more than
  once, the patch is refused rather than guessed at — a wrong edit would silently
  steer every future session.
- **Rollback fails closed.** If the ledger's blob for the prior content is
  missing, rollback refuses rather than restoring partial bytes.
- **A failed apply leaves the proposal `PENDING`**, not falsely `APPLIED`.
- **Names are path-sanitised.** Skill names come from a language model; they are
  reduced to safe single path segments before touching the filesystem.
- **A failed review is a non-event.** The user's turn already completed; a
  reviewer that throws is logged and forgotten.

## The ledger

Every skill mutation appends to `{proposals-dir}/{tenant}/ledger/entries.jsonl`,
with before/after bodies in a content-addressed blob store. Append-only: a
rollback is recorded as a *new* entry rather than removing the one it reverses,
because a ledger that can be quietly edited is not a ledger.

## The curator

Ages unused learned skills `ACTIVE → STALE → ARCHIVED` on time since last use.

- Archived skills are **not deleted** — they record what the agent learned.
- **Pinned skills never transition.** An operator's explicit "keep this" outranks
  any usage heuristic.
- The clock is injectable, so ageing is testable without waiting 90 days.

## What the reviewer sees

The whole conversation transcript, truncated, **excluding system messages** —
feeding the agent its own instructions back invites it to "learn" what it was
already told.

The prompt instructs the model not to propose credentials, tokens, keys or
personal data. That is an instruction, not an enforcement: if you handle
regulated data, review the queue before applying, and consider the redaction
support in `jaiclaw-compliance`.

## Future direction

This loop reviews a **live session** after each turn. A proposed redesign for
1.3.0 runs extraction as a **batch pass over the `jaiclaw-audit` transcript
corpus** instead — which can ask "what recurs across many conversations?" rather
than "is anything in this one conversation durable?", removes the prompt-cache
risk by construction, and makes extraction evaluable by re-running over a fixed
corpus.

That design makes `jaiclaw-audit` a **strict** dependency: the current reviewer
falls back to `SessionManager` when audit is absent, but a batch extractor has
nothing to batch over. Archiving would need to have been enabled long enough to
accumulate a corpus.

It is **not scheduled**, and it is gated behind a prerequisite: an outcome signal
that tells us whether an applied skill actually helped. Without that, batch
extraction is the same unvalidated guess, just cheaper. See
[`docs/issues/DESIGN-1.3.0-SKILL-EXTRACTION.md`](../issues/DESIGN-1.3.0-SKILL-EXTRACTION.md).

## See also

- [`DELEGATION.md`](./DELEGATION.md)
- [`BUDGETS-AND-GUARDS.md`](./BUDGETS-AND-GUARDS.md)

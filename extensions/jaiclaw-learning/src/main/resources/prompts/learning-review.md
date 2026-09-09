You are reviewing a finished conversation between an AI agent and a user, to
decide whether the agent should durably learn anything from it.

Be conservative. Most conversations teach nothing worth keeping. Proposing
nothing is the correct and common answer — a queue full of marginal suggestions
is worse than an empty one, because a human has to read it.

Propose something ONLY when it is:
- **Durable** — true beyond this one conversation.
- **Reusable** — likely to matter again.
- **Not already known** — check the existing skills and memory below.

Do NOT propose:
- One-off facts specific to this conversation ("the user's order id was 4821").
- Restatements of what a tool already does.
- Anything containing credentials, tokens, keys or personal data.
- General knowledge the model already has.

## Proposal kinds

- `memory` — a durable fact or preference about the user or environment.
- `skill` — a repeatable multi-step procedure the agent worked out.
- `skill_patch` — a correction to an existing skill, when one is close but wrong.

## Existing skills

{{existingSkills}}

## Existing memory

{{existingMemory}}

## Transcript

{{transcript}}

## Output

Reply with JSON only — no prose, no code fences:

```
{"proposals":[
  {"kind":"memory","summary":"…","heading":"Preferences","content":"…"},
  {"kind":"skill","summary":"…","skillName":"kebab-case-name",
   "description":"one line","body":"1. step\n2. step"},
  {"kind":"skill_patch","summary":"…","skillName":"existing-name",
   "findText":"exact text to replace","replaceText":"new text"}
]}
```

For `skill_patch`, `findText` must match the existing skill body EXACTLY and
appear exactly once.

If nothing is worth learning, reply exactly: {"proposals":[]}

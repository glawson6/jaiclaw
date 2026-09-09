# Subagent Delegation

Lets an agent hand a self-contained subtask to a **child agent** with its own
session, its own iteration budget, and tool access no wider than its parent's.

Introduced in 1.2.0. **Off by default** — with `enabled: false` the tools are
never registered, so no model can reach them.

## Configuration

```yaml
jaiclaw:
  agent:
    delegation:
      enabled: true            # default false — tools are not registered at all
      max-depth: 2             # how deep delegation may nest
      max-concurrent: 4        # simultaneous children per parent session
      child-max-iterations: 50 # child budget, independent of the parent's
      default-child-profile: MINIMAL
      wait-timeout: 10m        # before a blocking call returns a RUNNING handle
      kanban-enabled: false    # mirror lifecycle onto a kanban card
```

## Tools the model sees

### `delegate_task`

| Parameter | Meaning |
|---|---|
| `goal` (required) | What the child should accomplish. **The child cannot see the parent's conversation**, so this must be self-contained. |
| `context` | Background: file paths, prior findings, constraints. |
| `tool_profile` | `NONE` / `MINIMAL` / `MESSAGING` / `CODING` / `FULL`. Narrowed to the parent's if wider. |
| `max_iterations` | Child budget. Omit for `child-max-iterations`. |
| `wait` | Block for the result (default `true`), or return a handle to poll. |

Returns JSON: `{"status":"COMPLETED","handle_id":"…","session_key":"…","summary":"…"}`.

### `delegate_status`

`{"handle_id": "…", "action": "status" | "result" | "cancel"}`

Never blocks — a parent cannot deadlock waiting on its own child.

## Limits, and why each behaves as it does

**Depth is refused.** A depth violation is a property of the call graph, not of
timing, so waiting can never resolve it. The child is refused immediately with a
message telling the model to do the work directly. `max-depth: 2` means a
user-started run may delegate, and its children may delegate once more.

**Concurrency queues.** Being at the limit is transient. Refusing would fail
`delegate_task` for a reason the model can neither see nor act on, so it would
retry blindly. Instead the child waits on a per-parent semaphore, bounded by
`wait-timeout` — a saturated parent degrades to a `RUNNING` handle, not an error.

**Fan-out is bounded** by `max-depth` × `max-concurrent` × `child-max-iterations`.
With defaults: at most 4 children per parent, 2 levels deep, 50 iterations each.

## Tool profiles

A child can never be granted more access than its parent. `ToolProfile` is an
enum, so "narrower" follows an explicit privilege ordering:

```
NONE  <  MINIMAL  <  MESSAGING  <  CODING  <  FULL
```

`MESSAGING` sits below `CODING` because sending a message is narrower than shell
and file access. A `MINIMAL` parent whose model asks for `FULL` gets `MINIMAL`.

Give children the narrowest profile that can do the job — a research child rarely
needs `CODING`.

## Sessions

Child session key: `{agentId}:subagent:{parentSessionKey}:{n}`, with `n` monotonic
per parent so siblings never collide. Child sessions are **closed on completion**
so they do not accumulate.

## Tenancy

The child runs under the parent's tenant, carried onto its virtual thread by
`TenantContextPropagator`. Without that, a child would run tenant-less and could
read or write the wrong tenant's data — so this is enforced and covered by a spec.

## Events

| Event | Fired when |
|---|---|
| `SubAgentStartedEvent` | Child begins (carries depth, goal, budget) |
| `SubAgentProgressEvent` | Optional progress update |
| `SubAgentEndedEvent` | Child reaches a terminal state — **including failures**, so listeners can always close what they opened |

## Guards apply to children automatically

Children run on the same `AgentRuntime` as their parent, so everything in
[`BUDGETS-AND-GUARDS.md`](./BUDGETS-AND-GUARDS.md) — iteration budgets, the
repetition guard, approval floors — applies to them with no extra configuration.

## Security notes

- **Delegation is refused when no runtime context is attached** to the tool call.
  Without it, depth, tenant and profile narrowing cannot be enforced, and
  spawning unbounded untenanted work would be the worse failure.
- Both tools carry `section: "delegation"`, so adopters can exclude the entire
  surface with a tool policy.
- A child inherits the parent's workspace directory. Combine with
  `jaiclaw.tools.code.workspace-boundary` when children get `CODING`.

## When delegation is the wrong tool

Delegation costs a full extra agent run. Prefer a single turn when the subtask
needs the conversation's context, is a single tool call, or must share state with
the parent — the child starts with an empty session and sees only `goal` and
`context`.

## See also

- [`BUDGETS-AND-GUARDS.md`](./BUDGETS-AND-GUARDS.md)
- [`EMERGENCY-STOP.md`](./EMERGENCY-STOP.md)

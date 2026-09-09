# Budgets and Runtime Guards

Four guards that make an autonomous agent loop safe to leave running: an
iteration budget, a repetition detector, an empty-response detector, and
per-tool approval floors.

Introduced in 1.2.0. **Every guard is neutral by default** — an existing
deployment gets exactly the behaviour it had before, and opts in per agent.

They apply to the `explicit` tool loop
(`jaiclaw.agent.agents.<name>.tool-loop.mode: explicit`), which is the loop that
provides hook observability and approval gates.

## Configuration

```yaml
jaiclaw:
  agent:
    agents:
      default:
        tool-loop:
          mode: explicit
          max-iterations: 25          # hard cap (pre-existing)

          budget-max-iterations: 0    # 0 = use max-iterations
          budget-warning-ratio: 0.9   # warn the model at 90% spent
          repetition-threshold: 3     # identical consecutive calls (0 = off)
          approval-floors:
            shell_exec: PROMPT_ALWAYS
            file_write: PROMPT_ALWAYS
```

## Iteration budget

A per-run countdown, separate from `max-iterations` so a delegated child run can
carry a smaller budget than the loop's hard cap. Each run gets its **own**
counter — parent and child never contend.

Two things happen as it drains:

**At the warning ratio** (default 90%) the loop appends a one-time notice to the
next tool result:

> `[budget] About 3 tool iterations remain for this task. Wrap up: finish the
> work, or persist your progress and summarise what is left.`

Telling the model about its own budget is the point: it can choose to save state
and summarise rather than being cut off mid-thought.

**On exhaustion** the loop makes one final model call **with no tools attached**
and an instruction to summarise. The run ends with a real answer.

> Before 1.2.0, hitting the cap returned the literal string
> `"Max iterations reached (25)"`. That is now a genuine summary of what the run
> accomplished. If the final call itself fails, the guard instruction is
> returned rather than propagating an exception — a guard must never turn a
> partially successful run into a failure.

`BudgetWarningEvent` fires once per run at the threshold.

## Repetition guard

Tracks the most recent `(toolName, arguments)` pair. Identical **consecutive**
calls are the signature of a stuck model; any different call resets the count.
Matching is exact — a model varying its parameters is exploring, not looping.

Two escalation levels:

| Repeats | Action |
|---|---|
| `repetition-threshold` (default 3) | The tool result is replaced with a corrective notice: *"Repeated call detected … change your approach."* `RepetitionDetectedEvent` fires. |
| `threshold + 2` | The correction was ignored. The loop stops calling tools and forces a final answer. |

Between those two points the call is allowed through, so the model still gets a
real result to react to.

Set `repetition-threshold: 0` to disable.

## Empty-response guard

Some providers occasionally return an assistant message with neither text nor
tool calls. One is a hiccup; **two consecutive** means the run is wedged and
would otherwise spin until the budget drains. On the second, the loop forces a
final turn with an explicit instruction to answer.

Not configurable — the threshold of 2 is not a policy choice.

## Approval floors

A per-tool **minimum** approval posture that the model, and a user's earlier
"allow always" click, cannot talk past. A floor can only make approval stricter.

| Floor | Effect |
|---|---|
| `NONE` (default) | Tool follows the session's normal approval config |
| `PROMPT_ALWAYS` | Approval is requested on **every** call, even when the session would otherwise skip it |
| `DENY` | Refused without consulting the approval handler; the model gets a denial as the tool result and can choose another path |

`DENY` returns a tool result rather than throwing, so a blocked tool is a fact
the model can reason about, not a crashed run.

Floors are recommended for anything with real blast radius:

```yaml
approval-floors:
  shell_exec: PROMPT_ALWAYS
  file_write: PROMPT_ALWAYS
```

An unknown floor name in per-tenant YAML logs a warning and is ignored, rather
than failing config load for every tenant in the deployment.

## Events

| Event | Fired when |
|---|---|
| `BudgetWarningEvent` | Budget crosses the warning ratio (once per run) |
| `RepetitionDetectedEvent` | Repetition threshold trips; again with `forcedFinal=true` if it escalates |

## Programmatic use

```java
ToolLoopConfig config = new ToolLoopConfig(
        ToolLoopConfig.Mode.EXPLICIT,
        25,                                   // hard cap
        false,                                // requireApproval
        IterationBudget.of(10),               // per-run budget
        0.9,                                  // warning ratio
        3,                                    // repetition threshold
        Map.of("shell_exec", ApprovalFloor.PROMPT_ALWAYS));
```

The legacy three-argument constructor still exists and takes the defaults above,
so existing code is unaffected.

## See also

- [`EMERGENCY-STOP.md`](./EMERGENCY-STOP.md) — the global pause switch.

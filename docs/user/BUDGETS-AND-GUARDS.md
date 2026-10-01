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

### Two prerequisites — both required, or the floor does nothing

Approval floors are enforced by the **explicit tool loop only**, and
`PROMPT_ALWAYS` needs something to prompt:

1. **`tool-loop.mode` must be `explicit`.** Under the default `spring-ai` mode,
   Spring AI runs the tool loop internally and never consults the approval
   handler or the floors — a configured floor is silently inert.
2. **A `ToolApprovalHandler` bean must be registered** for `PROMPT_ALWAYS`.
   Nothing in the framework provides a default; headless deployments typically
   have none.

```yaml
jaiclaw:
  agent:
    agents:
      default:
        tool-loop:
          mode: explicit          # REQUIRED — floors are inert under spring-ai
          approval-floors:
            shell_exec: PROMPT_ALWAYS
```

**Since 1.3.0 both gaps fail closed rather than open.** A `PROMPT_ALWAYS` tool
with no registered handler is **denied**, not executed — an approval that cannot
be obtained is not an approval. The same applies when a handler throws. Before
1.3.0 both cases executed the tool, which made `PROMPT_ALWAYS` weaker than
`DENY`. `AgentRuntime` also logs a startup WARN when approval controls are
configured but cannot take effect.

If you want a tool blocked outright with no handler involved, use `DENY` — that
is what it is for. `PROMPT_ALWAYS` without a handler is a misconfiguration, and
is now reported as one.

## Approval timing (1.3.0)

A floor says *whether* a tool needs approval. This says *how long* the request
stays open and what silence means.

```yaml
jaiclaw:
  agent:
    agents:
      default:
        tool-loop:
          mode: explicit
          approval-floors:
            shell_exec: PROMPT_ALWAYS
            post_review: PROMPT_ALWAYS
          approval:
            auto-approve: false        # master switch — see below
            default-timeout: 5m        # applies to tools with no entry below
            on-timeout: deny           # deny (default) | approve
            tools:
              shell_exec:  { timeout: 2m,  on-timeout: deny }
              post_review: { timeout: 30m, on-timeout: deny }
```

Timeouts are per tool because blast radius and human latency differ. A
`shell_exec` answered in two minutes or not at all is reasonable; "reply to this
review" may deserve half an hour. One global number is wrong for one of them.

| Setting | Default | Notes |
|---|---|---|
| `default-timeout` | `5m` | Window for tools with no explicit entry |
| `on-timeout` | `deny` | An unanswered request is not consent |
| `tools.<name>.timeout` | inherits | Per-tool window |
| `tools.<name>.on-timeout` | inherits | Set only where waiting is worse than acting |

`on-timeout` takes `deny` or `approve` at either scope, and **resolves to
`deny` unless you typed `approve` for that scope**. Auto-approval on silence
never arrives by inheritance or omission:

| You configure | Silence resolves to |
|---|---|
| nothing | `deny` |
| `tools.harmless.on-timeout: approve` | `approve` for `harmless` only |
| …and any other tool | `deny` — the opt-in does not spread |
| `tools.shell_exec.timeout: 2m` (no action) | `deny`, with a 2-minute window |
| `on-timeout: approve` at the top level | `approve` everywhere (allowed, but explicit) |

The fourth row is the one that bites: narrowing a tool's *window* must never
silently opt it into auto-approval. `ApprovalPolicySpec` pins all five rows.

**The loop enforces the window itself.** Before 1.3.0 the approval call was a
bare `get()` with no timeout — every shipped handler resolved synchronously, so
an asynchronous handler would pin the agent thread forever. The loop now applies
the configured deadline as a backstop even if a handler forgets to.

Durations accept the usual suffixed forms (`30s`, `5m`, `1h`); a bare number is
seconds. An unparseable value logs a warning and falls back rather than failing
config load for every tenant.

### `auto-approve` — running unattended

Setting every tool to "no approval needed" one by one is tedious and easy to get
wrong. One switch covers it:

```yaml
approval:
  auto-approve: true
```

Two guarantees hold:

- **`DENY` still wins.** The loop evaluates a `DENY` floor *before* it decides
  whether approval is required, so auto-approve structurally cannot execute a
  denied tool. Use `DENY` for anything that must stay blocked regardless.
- **It announces itself.** `AgentRuntime` logs a WARN at startup naming the
  setting and how many `DENY` floors remain enforced. Silently disabling a
  security control is how fail-open defaults survive review.

This is the one sanctioned exception to "a floor can only make approval
stricter, never looser" — and deliberately an operator-only, deployment-wide
decision, not something a tool author or the model can set.

## Approval over chat (1.3.0)

`PROMPT_ALWAYS` needs something to prompt. The framework ships a handler that
asks a configured approver in chat and waits for a text reply:

```yaml
jaiclaw:
  approval:
    chat:
      enabled: true                  # opt-in; off by default
      approvers:
        - channel-id: telegram
          account-id: ${TELEGRAM_ACCOUNT_ID}
          peer-id: "9001"            # the chat to ask in
```

The approver replies **yes** or **no** in the chat. The reply is consumed by a
gateway filter rather than becoming a new agent turn, so a bare "yes" authorises
the tool instead of getting a conversational answer.

**Why text and not buttons.** Inline keyboards would remove the parsing
ambiguity, but the Telegram adapter has no `reply_markup` support and drops
`callback_query` updates before they reach the gateway. Text replies work on
every channel that can carry a message.

### Who gets asked

The question goes to the **configured approver**, never to whoever triggered the
run. The person who typed "reboot it" is the last person who should confirm it,
and a cron- or API-triggered run has no requester to ask at all.

With `enabled: true` and no usable approver, every approval-requiring call is
**denied** and startup logs a warning naming the property. An entry missing its
`channel-id` or `peer-id` is dropped, since it could not be messaged anyway.

Replace `ApproverResolver` with your own bean for a rota, per-tenant routing, or
per-tool approvers — the default resolver is the static single-owner case.

### What the approver sees

The tool name, its arguments, the deadline, and what happens on silence — an
approver cannot judge a request without knowing what is being asked or how long
they have.

### Behaviour worth knowing

| Situation | Result |
|---|---|
| Reply parses as yes/no | Tool approved/denied; the message is consumed |
| Reply is something else ("what does that do?") | Passed through to the agent; the request **stays open** |
| Reply from a non-approver | Ordinary message; the approval is untouched |
| Reply after the window closed | Ordinary message; the stale answer does not authorise |
| Same "yes" repeated later | Does not authorise a new call — redemption is single-use |
| Approval channel unavailable, or send fails | Denied immediately rather than waiting out the window |

Replies are matched on whole-message equality against a short vocabulary, not by
substring: `"don't approve that"` contains "approve" and must not read as
consent.

> **Not persisted.** A request in flight is lost on restart. That is honest for a
> human-latency gate — after a restart the agent run is gone too, so a late
> approval would have nothing to authorise.

> **The system prompt is not an approval gate.** Instructing the model to ask
> before acting is useful, but it is advisory — a user can talk the model past
> it. Only the floors above are mechanical.

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

# Emergency Stop (ESTOP)

A global pause switch. While engaged, JaiClaw refuses to start **new** work
across the whole deployment; work already in flight runs to completion.

Introduced in 1.2.0. Absent by default — a deployment that never engages it
behaves exactly as it did before.

## Quick reference

```bash
jaiclaw pause --reason "deploying v2"   # engage
jaiclaw estop                           # check state (exit 1 when engaged)
jaiclaw resume                          # release
```

```bash
curl -s localhost:8080/actuator/jaiclaw-estop
curl -X POST localhost:8080/actuator/jaiclaw-estop \
     -H 'Content-Type: application/json' \
     -d '{"engaged": true, "reason": "incident 4821"}'
```

## What it does and does not do

| While engaged | Behaviour |
|---|---|
| Inbound channel message | Refused with `jaiclaw.gateway.estop-message` (default: "Assistant is paused by the operator.") |
| `GatewayService.handleAsync` | Returns the same refusal text |
| Due cron job | **Skipped and rescheduled** — it fires at its next occurrence after release. Nothing is lost. |
| Kanban column processor | Stands down; the card keeps its state and is processed when moved again after release |
| `POST /api/pipelines/{id}/trigger` | `503` with `Retry-After: 60` |
| A turn already running | **Runs to completion.** ESTOP never kills in-flight work. |

The stop is **resumable** and carries no state beyond the sentinel file — engage
and release as often as you like.

## Why a file, not a database flag

The sentinel is a plain file at `$JAICLAW_HOME/ESTOP` (default
`~/.jaiclaw/ESTOP`). That choice is deliberate:

- it works with no Redis, no JDBC and no running JVM;
- `bin/jaiclaw pause` needs no Java, so you can pause a wedged deployment;
- an operator with only shell access can `touch ~/.jaiclaw/ESTOP`.

**Any file at that path means engaged**, including an empty or corrupt one. This
is intentional fail-safe behaviour: a half-written sentinel must never leave the
fleet running. Only a well-formed body yields a reason in the status output.

### Sentinel format

```json
{"reason":"deploying v2","engagedAt":"2026-09-09T16:30:12Z"}
```

Both the bash fast path and `io.jaiclaw.core.ops.EmergencyStop` read and write
this shape; `EmergencyStopCliCompatSpec` pins the contract so neither side can
drift.

## Path resolution

1. `jaiclaw.home` system property
2. `JAICLAW_HOME` environment variable
3. `$HOME/.jaiclaw`

## Configuration

```yaml
jaiclaw:
  gateway:
    estop-message: "We're briefly paused for maintenance — back shortly."
```

## Wiring it into a custom app

Every integration point takes an optional `EmergencyStop`; passing `null` (or
never calling the setter) disables the check:

```java
EmergencyStop estop = new EmergencyStop();          // default path
gatewayService.setEmergencyStop(estop, "Paused.");  // gateway
cronService.setEmergencyStop(estop);                // cron
columnProcessorManager.setEmergencyStop(estop);     // kanban
pipelineTriggerController.setEmergencyStop(estop);  // pipeline
```

## Security

`/actuator/jaiclaw-estop` **mutates global state** — anyone who can reach it can
pause every agent in the deployment, or release a pause someone else engaged.
The endpoint performs no authorization of its own, in keeping with the other
actuator endpoints here. Front `/actuator/**` with the same authentication you
use for the rest of your admin surface, and prefer restricting the write
operation to an operator role.

## Observability

`EmergencyStopEvent` fires on engage and release, from the actuator and CLI
paths only. It is deliberately **not** fired by the readers: they poll the
sentinel on every inbound message and every cron tick, and firing there would
flood the hook bus.

```java
@Override
public void onEvent(EmergencyStopEvent event) {
    if (event.engaged()) alerting.page("JaiClaw paused: " + event.reason());
}
```

## See also

- [`BUDGETS-AND-GUARDS.md`](./BUDGETS-AND-GUARDS.md) — per-run iteration budgets,
  repetition and empty-response guards, approval floors.

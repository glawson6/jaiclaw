---
name: kanban-orchestrator
description: Decompose and route work through multi-agent Kanban systems
alwaysInclude: false
requiredBins: []
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# Kanban Orchestrator

Coordinate multi-agent workflows using a Kanban-style board. The orchestrator decomposes complex tasks into discrete work items, assigns them to worker agents, and tracks progress through lifecycle stages.

## Board Structure

Maintain a Kanban board with these columns:

| Column | Purpose |
|--------|---------|
| **Backlog** | Decomposed tasks awaiting assignment |
| **In Progress** | Tasks actively being worked by an agent |
| **Review** | Completed tasks awaiting verification |
| **Done** | Verified and accepted tasks |
| **Blocked** | Tasks with unresolved dependencies |

## Task Decomposition

When receiving a complex request:

1. **Break down** the work into independent, well-scoped tasks.
2. **Identify dependencies** — mark tasks that block or are blocked by others.
3. **Estimate complexity** — label tasks as `small`, `medium`, or `large`.
4. **Assign metadata** — each task gets an ID, title, description, acceptance criteria, and assigned worker type.

### Task Card Format

```yaml
id: TASK-001
title: Short imperative title
description: |
  Detailed description of the work to be done.
  Include inputs, expected outputs, and constraints.
acceptance_criteria:
  - Criterion 1
  - Criterion 2
blocked_by: []
complexity: small | medium | large
worker_type: coding | research | testing | review
```

## Orchestration Workflow

1. **Intake** — receive user request, decompose into tasks, populate Backlog.
2. **Prioritize** — order Backlog by dependency graph and business value.
3. **Dispatch** — move top-priority unblocked tasks to In Progress, delegate to workers via background shell or agent delegation.
4. **Monitor** — poll worker progress, collect outputs.
5. **Review** — verify outputs against acceptance criteria, move to Done or return to In Progress with feedback.
6. **Report** — summarize completed work and remaining items to the user.

## Delegation Pattern

Use background processes to run workers in parallel:

```bash
# Dispatch to a coding worker
bash background:true workdir:/path/to/project command:"claude --print 'TASK-001: Implement the login form component'"

# Dispatch to a research worker
bash background:true command:"claude --print 'TASK-002: Research OAuth 2.0 PKCE flow best practices'"
```

Monitor all active workers:

```bash
process action:poll sessionId:WORKER_SESSION_ID
process action:log sessionId:WORKER_SESSION_ID
```

## Rules

1. **Never skip decomposition** — even simple requests benefit from explicit task cards.
2. **Respect the dependency graph** — do not dispatch blocked tasks.
3. **One task per worker** — avoid overloading a single worker with multiple tasks.
4. **Surface blockers early** — move tasks to Blocked immediately when dependencies stall.
5. **Report progress** — keep the user informed after each column transition.

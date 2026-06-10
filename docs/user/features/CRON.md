# Cron Job Scheduling

Module: `jaiclaw-cron`

## Overview

Enables proactive agents by scheduling recurring tasks using standard 5-field cron expressions. Jobs persist to a JSON file and execute via a configurable agent function. Supports job CRUD, immediate execution, and run history.

## Cron Expression Format

Standard 5-field format: `minute hour day-of-month month day-of-week`

```
┌───────── minute (0-59)
│ ┌─────── hour (0-23)
│ │ ┌───── day of month (1-31)
│ │ │ ┌─── month (1-12)
│ │ │ │ ┌─ day of week (0-6, 0=Sunday)
│ │ │ │ │
* * * * *
```

Supports: `*` (any), specific values, ranges (`1-5`), lists (`1,3,5`), and step values (`*/5`).

Examples:
- `0 9 * * *` — daily at 9:00 AM
- `*/15 * * * *` — every 15 minutes
- `0 9 * * 1-5` — weekdays at 9:00 AM
- `0 0 1 * *` — first of each month at midnight

## Usage

```java
// Create components
CronJobStore store = new CronJobStore(Path.of("data/cron-jobs.json"));
CronJobExecutor executor = new CronJobExecutor(job -> agentRuntime.run(job.prompt()));
CronService service = new CronService(store, executor, 100, 3600);

// Add a job
CronJob job = new CronJob(
    "daily-status",         // id
    "Daily Status Check",   // name
    "default",              // agentId
    "0 9 * * *",           // schedule
    "America/New_York",     // timezone
    "Check system status and report any issues",  // prompt
    "slack",                // deliveryChannel (optional)
    "#ops-alerts",          // deliveryTarget (optional)
    true,                   // enabled
    null,                   // lastRunAt
    null                    // nextRunAt (computed automatically)
);
service.addJob(job);

// Start the scheduler
service.start();

// Run a job immediately
CronJobResult result = service.runNow("daily-status");

// View run history
List<CronJobResult> history = service.getHistory("daily-status");

// Stop
service.stop();
```

## Key Classes

| Class | Purpose |
|-------|---------|
| `CronService` | Main service — job CRUD, scheduling, run history |
| `CronScheduleComputer` | Parses cron expressions, computes next fire time |
| `CronJobStore` | JSON file persistence for job definitions |
| `CronJobExecutor` | Executes jobs via a configurable `Function<CronJob, String>` |
| `CronJob` | Job definition record (in `jaiclaw-core`) |
| `CronJobResult` | Sealed interface: Success/Failure (in `jaiclaw-core`) |

## Design

- Uses `ScheduledExecutorService` with virtual threads for the polling loop
- Job store persists to a single JSON file using Jackson with JavaTimeModule
- Run history is kept in-memory with a configurable max size per job
- The executor function is injected, keeping the cron module decoupled from the agent runtime

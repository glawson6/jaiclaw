# Building an AI-Powered Server Monitor with Java, Spring Boot, and Telegram

How we built a server monitoring agent that uses an AI backbone to collect system metrics, analyze them, and deliver daily health reports via Telegram — using JClaw's Composable App Assembly pattern.

## The Problem

Server monitoring tools fall into two camps: heavyweight platforms like Datadog and Grafana that require infrastructure to run, and lightweight scripts that send canned alerts. Neither gives you the ability to *ask questions* about your server or get intelligent, AI-analyzed health reports delivered to your phone.

We wanted something different: a scheduled AI agent that collects system metrics every morning, analyzes them for anomalies, formats a clear health report, and delivers it to a Telegram chat — plus an always-on gateway you can message for on-demand queries.

## The Architecture

The system monitor is a JClaw example application that demonstrates the **Composable App Assembly** pattern: composing JClaw starters to build a purpose-built agent in minimal code.

```
                          Scheduled Cron Job (daily 7 AM)
                                    |
                              AgentRuntime
                               /        \
                        ToolRegistry    SkillLoader
                       /     |     \         |
    WhitelistedCommandTool   |  SendTelegramTool   system-monitor skill
     (as "system_command")   |
                             |
                  ShowAllowedCommandsTool
                   (runtime introspection)

                          + Gateway (REST/WebSocket/Telegram)
                          + Embedded Cron Manager (H2 persistence)
```

Every morning, the cron manager fires a job that instructs the AI agent to collect system metrics, analyze them, and send a formatted report to a configured Telegram chat. The gateway also accepts on-demand queries via Telegram or REST.

The entire application has 2 Java files and 1 Markdown skill file. Everything else — the agent runtime, cron scheduling, session management, tool bridging, Telegram integration — comes from JClaw's starters.

## The Composable App Assembly Pattern

Instead of writing everything from scratch, the system monitor composes three JClaw starters:

```xml
<!-- Gateway: auto-config + REST + WebSocket + all channel adapters -->
<dependency>
    <groupId>io.jclaw</groupId>
    <artifactId>jclaw-starter-gateway</artifactId>
    <type>pom</type>
</dependency>

<!-- Cron manager: scheduling + batch + H2 persistence -->
<dependency>
    <groupId>io.jclaw</groupId>
    <artifactId>jclaw-starter-cron</artifactId>
    <type>pom</type>
</dependency>

<!-- AI provider -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
```

That's it. Three dependencies give you a fully functional AI gateway with Telegram, scheduled cron jobs, and H2-persisted job state. The starters wire everything together through Spring Boot auto-configuration.

## Key Design Decisions

### 1. Command Safety Through Prefix Whitelisting

The most critical design decision: how to let an AI execute shell commands safely. JClaw provides `WhitelistedCommandTool`, which checks every command against a prefix allowlist:

```java
@Bean
WhitelistedCommandConfig systemMonitorCommandConfig() {
    return new WhitelistedCommandConfig(
        List.of(
            "uptime", "free ", "df ", "top -bn1",
            "vmstat", "iostat", "ps aux",
            "cat /proc/loadavg", "cat /proc/meminfo",
            "hostname", "ss -tlnp", "lsblk", "who"
        ),
        30,   // timeout seconds
        200,  // max output lines
        "system_command",
        "System Monitor",
        Set.of(ToolProfile.FULL, ToolProfile.MINIMAL)
    );
}
```

If the AI generates `free -h`, it matches the `free ` prefix and runs. If it generates `rm -rf /`, nothing matches and it's rejected immediately. No command parsing, no shell analysis — just a flat prefix match that fails safe.

Why prefix whitelisting instead of full command parsing?

- **Simplicity**: full shell parsing (handling pipes, subshells, quoting) is complex and fragile
- **Fail-safe**: unrecognized commands are rejected, not analyzed
- **Auditable**: the allowlist is a flat list an admin can review in seconds
- **Configurable**: each app defines its own allowlist for its specific monitoring needs

### 2. Reusable SendTelegramTool

The agent needs to deliver reports to a specific Telegram chat. JClaw's `jclaw-channel-telegram` module provides a reusable `SendTelegramTool` that any app can wire as a bean:

```java
@Bean
SendTelegramTool sendTelegramTool(ChannelRegistry channelRegistry) {
    return new SendTelegramTool(channelRegistry);
}
```

The tool looks up the Telegram adapter from the `ChannelRegistry`, constructs an outbound `ChannelMessage`, and delivers it. It's available in both `FULL` and `MINIMAL` tool profiles.

### 3. Skills as Markdown, Not Code

The monitoring workflow is a Markdown file with YAML frontmatter — not a Java class:

```markdown
---
name: system-monitor
description: Linux system health monitoring and reporting
alwaysInclude: true
---

You are a Linux system health monitoring agent.

## Available Tools

- `show_system_commands` — discover which commands are available
- `system_command` — execute an allowed monitoring command
- `send_telegram` — deliver the formatted report

## Report Generation Workflow

1. **Discovery**: Call `show_system_commands` to see what's installed
2. **Data Collection**: Run available commands (uptime, free, df, top, ss)
3. **Analysis**: Flag memory > 80%, disk > 80%, load > CPU count
4. **Format**: Produce a Markdown report with status, metrics, warnings
5. **Deliver**: Send the report via `send_telegram`
```

Skills are system prompt fragments — they tell the AI *how* to use tools, what workflow to follow, and how to format output. JClaw's `SkillLoader` reads them from the classpath, parses the frontmatter, and injects them into the agent's system prompt. You can edit skills without recompilation.

### 4. Cron Job Registration on Startup

The cron job is registered programmatically via an `ApplicationRunner`:

```java
@Bean
ApplicationRunner registerHealthReportJob(
        CronJobManagerService cronJobManagerService,
        @Value("${sysmonitor.schedule:0 7 * * *}") String schedule,
        @Value("${sysmonitor.timezone:America/New_York}") String timezone,
        @Value("${sysmonitor.telegram.chat-id:}") String chatId) {
    return args -> {
        if (cronJobManagerService.getJob("system-health-report").isPresent()) {
            return;  // already registered (H2 persists across restarts)
        }

        CronJob cronJob = new CronJob(
            "system-health-report",
            "Daily System Health Report",
            "default",       // agent ID
            schedule,        // "0 7 * * *" = 7 AM daily
            timezone,
            buildPrompt(chatId),
            "telegram",      // delivery channel
            chatId,
            true, null, null
        );

        cronJobManagerService.createJob(
            new CronJobDefinition(cronJob, null, null, null,
                ToolProfile.MINIMAL, List.of()));
    };
}
```

The cron manager persists jobs in H2, so the job is only created once. On subsequent restarts, it's loaded from the database. The `CronJobManagerService` handles scheduling, crash recovery, and batch execution.

### 5. Embedded Cron Manager via Auto-Configuration

The cron manager activates through a single property:

```yaml
jclaw:
  cron:
    manager:
      enabled: true
```

This triggers `CronManagerAutoConfiguration` (gated on `@ConditionalOnProperty`), which provides `CronJobManagerService`, `CronAgentFactory`, `CronBatchJobFactory`, and the MCP tool provider. Because the gateway starter is also on the classpath, the gateway's `McpServerRegistry` automatically collects the cron manager's MCP tools — no manual wiring needed.

## Configuration

```yaml
server:
  port: ${GATEWAY_PORT:8080}

sysmonitor:
  schedule: ${MONITOR_SCHEDULE:0 7 * * *}
  timezone: ${MONITOR_TIMEZONE:America/New_York}
  telegram:
    chat-id: ${TELEGRAM_CHAT_ID:}

jclaw:
  cron:
    manager:
      enabled: true
  identity:
    name: System Monitor
    description: Linux system health monitoring agent with scheduled Telegram reports
  agent:
    default-agent: default
    agents:
      default:
        id: default
        name: System Monitor Agent
        tools:
          profile: minimal
  skills:
    allow-bundled:
      - system-monitor
  channels:
    telegram:
      enabled: ${TELEGRAM_ENABLED:true}
      bot-token: ${TELEGRAM_BOT_TOKEN:}

spring:
  datasource:
    url: jdbc:h2:file:${user.home}/.jclaw/system-monitor-db;AUTO_SERVER=TRUE
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:not-set}
      chat:
        options:
          model: ${ANTHROPIC_MODEL:claude-sonnet-4-5}
```

Environment variables make the same build deployable across servers:

| Variable | Purpose |
|----------|---------|
| `TELEGRAM_BOT_TOKEN` | Telegram Bot API token |
| `TELEGRAM_CHAT_ID` | Chat ID to receive daily reports |
| `ANTHROPIC_API_KEY` | AI provider API key |
| `MONITOR_SCHEDULE` | Cron expression (default: 7 AM daily) |
| `MONITOR_TIMEZONE` | Timezone (default: America/New_York) |

## What a Report Looks Like

Every morning at 7 AM, the Telegram chat receives:

```
System Health Report — myserver
2026-03-25 07:00 ET
Status: Healthy

Uptime: 47 days, 3 hours
Load: 0.42 / 0.38 / 0.35

Memory: 5.2 GB / 8.0 GB (65%)
Swap: 0 / 2.0 GB (0%)

Disk Usage:
/       — 34% (18.2 GB free)
/home   — 62% (45.1 GB free)

Top Processes (by memory):
1. java — 12.3% MEM, 8.1% CPU
2. nginx — 2.1% MEM, 0.3% CPU
3. postgres — 1.8% MEM, 0.5% CPU

No warnings detected.
```

You can also message the bot directly for on-demand queries — the gateway is always listening.

## The Complete Application

Two Java files:

1. **`SystemMonitorApplication.java`** — Standard Spring Boot entry point (5 lines)
2. **`SystemMonitorConfig.java`** — Wires tools and registers the cron job (~130 lines)

One skill file:

3. **`skills/system-monitor.md`** — Agent instructions for report generation (~50 lines)

One config file:

4. **`application.yml`** — Framework and app configuration (~75 lines)

Everything else — the agent runtime, cron scheduling, Telegram integration, tool bridging, session management, H2 persistence — comes from the three JClaw starters.

## Getting Started

1. Clone the JClaw repository
2. Build:
   ```bash
   export JAVA_HOME=/path/to/java-21
   ./mvnw install -DskipTests
   ```
3. Set environment variables:
   ```bash
   export TELEGRAM_BOT_TOKEN=your-bot-token
   export TELEGRAM_CHAT_ID=your-chat-id
   export ANTHROPIC_API_KEY=your-api-key
   ```
4. Run:
   ```bash
   ./mvnw spring-boot:run -pl :jclaw-example-system-monitor
   ```

The agent starts, registers the cron job, and sends its first health report at the next scheduled time. Message the Telegram bot for on-demand queries in the meantime.

## Technology Stack

- **Java 21** with virtual threads
- **Spring Boot 3.5** with Spring AI 1.1 and Spring Batch
- **JClaw 0.1.0** — AI agent framework (gateway, cron manager, channels, tools, skills)
- **Claude** (Anthropic) — AI model for metric analysis and report generation
- **Telegram Bot API** — report delivery and interactive queries
- **H2** — cron job persistence across restarts

## Lessons Learned

**The Composable App Assembly pattern works.** Three starter dependencies and two Java files give you a production-capable monitoring agent. The starters handle all the infrastructure plumbing.

**Skills are the most important file.** The quality of the health report depends more on the skill instructions than on code. We iterated on the Markdown far more than on Java.

**Prefix whitelisting is surprisingly effective.** The flat prefix match is simpler, more auditable, and safer than command parsing. It hasn't caused a single false positive.

**Cron + AI + Telegram is a powerful combination.** Scheduled AI agents that collect, analyze, and deliver reports solve a real operational need with minimal code.

---

*This example is part of [JClaw](https://github.com/taptech/jclaw), an open-source Java framework for building embeddable AI agents with multi-channel messaging.*

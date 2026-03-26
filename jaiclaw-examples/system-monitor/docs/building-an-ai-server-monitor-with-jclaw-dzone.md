<!-- DZone Article Submission Format -->
<!-- Title: Building an AI-Powered Server Monitor With Java, Spring Boot, and Telegram -->
<!-- Subtitle: Compose a scheduled AI monitoring agent from framework starters in under 200 lines of Java -->
<!-- Zone: Java -->
<!-- Topics: Java, Spring Boot, AI, DevOps, Monitoring -->
<!-- Type: Tutorial -->

# Building an AI-Powered Server Monitor With Java, Spring Boot, and Telegram

What if your server could send you an AI-analyzed health report every morning on Telegram? This article shows how to build a scheduled monitoring agent in Java that collects system metrics, interprets them, and delivers formatted reports — in under 200 lines of application code.

## What We're Building

A Spring Boot application that:

- Runs a scheduled cron job every morning at 7 AM
- Uses an AI agent to collect system metrics via safe, whitelisted shell commands
- Analyzes results for anomalies (high memory, disk > 80%, excessive load)
- Formats and delivers a health report to a Telegram chat
- Also runs a gateway for on-demand queries via Telegram or REST

The project uses **Java 21**, **Spring Boot 3.5**, **Spring AI**, and [**JaiClaw**](https://github.com/taptech/jaiclaw) — an open-source framework for building AI agents with multi-channel messaging.

## The Composable App Assembly Pattern

Instead of writing boilerplate, we compose three JaiClaw starters:

```xml
<!-- Gateway: auto-config + REST + WebSocket + Telegram + all channels -->
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-starter-gateway</artifactId>
    <type>pom</type>
</dependency>

<!-- Cron manager: scheduling + batch + H2 persistence -->
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-starter-cron</artifactId>
    <type>pom</type>
</dependency>

<!-- AI provider -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
```

Three dependencies. The starters wire agent runtime, cron scheduling, Telegram integration, session management, and H2 persistence through Spring Boot auto-configuration.

## Step 1: Safe Command Execution

The AI agent needs to run shell commands to collect metrics. JaiClaw provides `WhitelistedCommandTool`, which checks every command against a prefix allowlist:

```java
@Bean
WhitelistedCommandConfig systemMonitorCommandConfig() {
    return new WhitelistedCommandConfig(
        List.of("uptime", "free ", "df ", "top -bn1", "vmstat",
                "cat /proc/loadavg", "cat /proc/meminfo",
                "hostname", "ss -tlnp", "lsblk", "who"),
        30,   // timeout seconds
        200,  // max output lines
        "system_command",
        "System Monitor",
        Set.of(ToolProfile.FULL, ToolProfile.MINIMAL)
    );
}

@Bean
WhitelistedCommandTool systemCommandTool(WhitelistedCommandConfig config) {
    return new WhitelistedCommandTool(config);
}

@Bean
ShowAllowedCommandsTool showSystemCommands(WhitelistedCommandConfig config) {
    return new ShowAllowedCommandsTool(config, "show_system_commands");
}
```

When the AI generates `free -h`, it matches the `free ` prefix and executes. If it generates anything not in the allowlist, it's rejected. No command parsing — just a flat prefix match that fails safe.

## Step 2: Telegram Delivery

JaiClaw's `jaiclaw-channel-telegram` module provides a reusable `SendTelegramTool`:

```java
@Bean
SendTelegramTool sendTelegramTool(ChannelRegistry channelRegistry) {
    return new SendTelegramTool(channelRegistry);
}
```

The tool looks up the Telegram adapter from the `ChannelRegistry`, constructs an outbound message, and delivers it. The AI calls this tool at the end of its workflow to send the formatted report.

## Step 3: The Monitoring Skill

A Markdown file tells the AI how to generate reports:

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
2. **Data Collection**: Run commands for uptime, memory, disk, CPU, ports
3. **Analysis**: Flag memory > 80%, disk > 80%, load > CPU count
4. **Format**: Produce a Markdown report with status and warnings
5. **Deliver**: Send via `send_telegram`
```

JaiClaw's `SkillLoader` reads this from the classpath and injects it into the agent's system prompt. Editable without recompilation.

## Step 4: Scheduled Cron Job

Register the daily report job on startup:

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
            "system-health-report", "Daily System Health Report",
            "default", schedule, timezone,
            buildPrompt(chatId),
            "telegram", chatId, true, null, null
        );

        cronJobManagerService.createJob(
            new CronJobDefinition(cronJob, null, null, null,
                ToolProfile.MINIMAL, List.of()));
    };
}
```

The cron manager persists jobs in H2. On restart, existing jobs are loaded from the database — no duplicate registration. The `CronJobManagerService` handles scheduling, crash recovery, and Spring Batch execution.

## Step 5: Configuration

One property activates the embedded cron manager:

```yaml
jaiclaw:
  cron:
    manager:
      enabled: true
```

The full config:

```yaml
sysmonitor:
  schedule: ${MONITOR_SCHEDULE:0 7 * * *}
  timezone: ${MONITOR_TIMEZONE:America/New_York}
  telegram:
    chat-id: ${TELEGRAM_CHAT_ID:}

jaiclaw:
  cron:
    manager:
      enabled: true
  identity:
    name: System Monitor
  agent:
    default-agent: default
    agents:
      default:
        tools:
          profile: minimal
  skills:
    allow-bundled:
      - system-monitor
  channels:
    telegram:
      enabled: true
      bot-token: ${TELEGRAM_BOT_TOKEN:}

spring:
  datasource:
    url: jdbc:h2:file:${user.home}/.jaiclaw/system-monitor-db;AUTO_SERVER=TRUE
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:not-set}
      chat:
        options:
          model: claude-sonnet-4-5
```

| Variable | Purpose |
|----------|---------|
| `TELEGRAM_BOT_TOKEN` | Telegram Bot API token |
| `TELEGRAM_CHAT_ID` | Chat ID to receive daily reports |
| `ANTHROPIC_API_KEY` | AI provider API key |
| `MONITOR_SCHEDULE` | Cron expression (default: 7 AM daily) |

## In Practice

Every morning at 7 AM:

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

No warnings detected.
```

Message the bot anytime for on-demand queries.

## The Complete Application

| File | Lines | Purpose |
|------|-------|---------|
| `SystemMonitorApplication.java` | 5 | Spring Boot entry point |
| `SystemMonitorConfig.java` | ~130 | Tool wiring + cron job registration |
| `skills/system-monitor.md` | ~50 | AI workflow instructions |
| `application.yml` | ~75 | Framework configuration |

Everything else — agent runtime, cron scheduling, Telegram integration, tool bridging, H2 persistence — comes from the three JaiClaw starters.

## What I Learned

1. **Starters eliminate boilerplate.** Three POM dependencies replace hundreds of lines of configuration and wiring code. The Composable App Assembly pattern lets you build focused agents fast.

2. **Skills matter more than code.** The quality of the health report depends on well-written Markdown instructions, not clever Java. We iterated on the skill file far more than on code.

3. **Prefix whitelisting is surprisingly effective.** A flat prefix match against a command allowlist is simpler, safer, and more auditable than AST-based command parsing.

4. **Cron + AI + Telegram is powerful.** Scheduled AI agents that collect, analyze, and deliver reports solve a real operational need with minimal code.

## Getting Started

```bash
# Clone JaiClaw
git clone https://github.com/taptech/jaiclaw && cd jaiclaw

# Build
export JAVA_HOME=/path/to/java-21
./mvnw install -DskipTests

# Configure
export TELEGRAM_BOT_TOKEN=your-bot-token
export TELEGRAM_CHAT_ID=your-chat-id
export ANTHROPIC_API_KEY=your-api-key

# Run
./mvnw spring-boot:run -pl :jaiclaw-example-system-monitor
```

The source is in `jaiclaw-examples/system-monitor/`. JaiClaw is open source at [github.com/taptech/jaiclaw](https://github.com/taptech/jaiclaw).

<!-- DZone metadata -->
<!-- Author Bio: Software engineer building AI-powered infrastructure tooling with Java and Spring Boot. -->
<!-- Related Reading: Spring AI, Spring Boot Monitoring, Telegram Bot API, AI Agents, Cron Jobs -->

package io.jaiclaw.examples.sysmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Gateway with embedded cron manager that sends daily Linux health
 * reports to a configured Telegram user.
 *
 * <p>Demonstrates the "Composable App Assembly" pattern:
 * <ul>
 *   <li>{@code jaiclaw-starter-gateway} — REST + WebSocket + Telegram channel</li>
 *   <li>{@code jaiclaw-starter-cron} — embedded cron manager with MCP tools, batch, H2 persistence</li>
 *   <li>Custom tools for system health data collection</li>
 *   <li>A skill that instructs the agent how to format the report</li>
 * </ul>
 *
 * <h3>Configuration (environment variables)</h3>
 * <pre>
 * TELEGRAM_BOT_TOKEN   — Telegram Bot API token
 * TELEGRAM_CHAT_ID     — Chat ID of the user to receive reports
 * ANTHROPIC_API_KEY    — Anthropic API key
 * MONITOR_SCHEDULE     — Cron expression (default: "0 7 * * *" = 7 AM daily)
 * MONITOR_TIMEZONE     — Timezone (default: America/New_York)
 * </pre>
 */
@SpringBootApplication
public class SystemMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemMonitorApplication.class, args);
    }
}

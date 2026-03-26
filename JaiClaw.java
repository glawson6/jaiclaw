///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS io.jaiclaw:jaiclaw-gateway-app:0.1.0-SNAPSHOT

import io.jaiclaw.gateway.app.JaiClawGatewayApplication;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * JaiClaw Launcher — JBang script that starts the gateway locally (no Docker).
 *
 * JBang handles Java 21 installation automatically.
 * The gateway JAR is resolved as a Maven dependency.
 *
 * Usage:
 *   jbang JaiClaw.java              # start gateway (default)
 *   jbang JaiClaw.java gateway      # start gateway
 *   jbang JaiClaw.java telegram     # validate Telegram token → start gateway
 *   jbang JaiClaw.java help         # print usage
 */
public class JaiClaw {

    // ── ANSI colors ─────────────────────────────────────────────────────────
    static final String RED = "\033[0;31m";
    static final String GREEN = "\033[0;32m";
    static final String YELLOW = "\033[1;33m";
    static final String CYAN = "\033[0;36m";
    static final String DIM = "\033[2m";
    static final String BOLD = "\033[1m";
    static final String NC = "\033[0m";

    static Path projectRoot;
    static Path envFile;

    public static void main(String[] args) throws Exception {
        // Resolve project root
        String jbangSource = System.getProperty("jbang.source");
        if (jbangSource != null) {
            projectRoot = Path.of(jbangSource).getParent();
        } else {
            projectRoot = Path.of("").toAbsolutePath();
        }
        String envOverride = System.getenv("JAICLAW_ENV_FILE");
        envFile = envOverride != null
                ? Path.of(envOverride)
                : projectRoot.resolve("docker-compose").resolve(".env");

        String command = args.length > 0 ? args[0] : "gateway";

        switch (command) {
            case "gateway" -> cmdGateway();
            case "telegram" -> cmdTelegram();
            case "-h", "--help", "help" -> printHelp();
            default -> {
                err("Unknown command: " + command);
                System.out.println("Run 'jbang JaiClaw.java help' for usage.");
                System.exit(1);
            }
        }
    }

    // ── Commands ────────────────────────────────────────────────────────────

    static void cmdGateway() throws Exception {
        header("JaiClaw Gateway");
        loadEnv();

        String port = System.getProperty("server.port", "8080");
        System.out.println("Starting gateway on http://localhost:" + port + "...");
        System.out.println();
        System.out.printf("  %sTest with:%s%n", DIM, NC);
        System.out.printf("  %scurl -X POST http://localhost:%s/api/chat \\%n", BOLD, port);
        System.out.printf("    -H \"Content-Type: application/json\" \\%n");
        System.out.printf("    -d '{\"content\": \"hello\"}'%s%n", NC);
        System.out.println();

        JaiClawGatewayApplication.main(new String[0]);
    }

    static void cmdTelegram() throws Exception {
        header("JaiClaw + Telegram");
        loadEnv();

        String token = System.getProperty("TELEGRAM_BOT_TOKEN",
                System.getenv("TELEGRAM_BOT_TOKEN"));
        if (token == null || token.isBlank()) {
            err("TELEGRAM_BOT_TOKEN is not set.");
            System.out.println();
            System.out.println("To set up Telegram:");
            System.out.println("  1. Open Telegram and message @BotFather");
            System.out.println("  2. Send /newbot and follow the prompts");
            System.out.println("  3. Copy the bot token");
            System.out.println("  4. Add to docker-compose/.env:");
            System.out.println("     TELEGRAM_BOT_TOKEN=<your-token>");
            System.out.println();
            System.out.println("Full guide: docs/TELEGRAM-SETUP.md");
            System.exit(1);
        }

        // Validate token via /getMe
        info("Validating Telegram bot token...");
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + token + "/getMe"))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                err("Telegram token validation failed. Check your TELEGRAM_BOT_TOKEN.");
                System.exit(1);
            }

            String body = response.body();
            String botUsername = extractJsonField(body, "username");
            ok("Bot validated: @" + botUsername);
            System.out.println();
            System.out.printf("  %sBot link: https://t.me/%s%s%n", BOLD, botUsername, NC);
            System.out.println("  Open the link above in Telegram to chat with your bot.");
            System.out.println();
        } catch (Exception e) {
            err("Telegram token validation failed: " + e.getMessage());
            System.exit(1);
        }

        cmdGateway();
    }

    static void printHelp() {
        System.out.println("Usage: jbang JaiClaw.java [command]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  gateway          Start gateway locally (default)");
        System.out.println("  telegram         Validate Telegram bot token and start gateway");
        System.out.println("  help             Print this help");
        System.out.println();
        System.out.println("Configuration is loaded from docker-compose/.env");
        System.out.println("Edit that file to set API keys, provider, and channel tokens.");
        System.out.println();
        System.out.println("For Docker-based deployment, use: jbang JaiClawDocker.java");
    }

    // ── Environment loading ─────────────────────────────────────────────────

    /**
     * Loads .env file and sets each key as a system property.
     *
     * <p>Spring resolves {@code ${TELEGRAM_BOT_TOKEN:}} placeholders in
     * application.yml from system properties, so we just set each key as-is.
     * Real environment variables take precedence (skipped if already set).
     *
     * <p>The .env file path defaults to {@code docker-compose/.env} but can be
     * overridden via the {@code JAICLAW_ENV_FILE} environment variable.
     */
    static void loadEnv() throws IOException {
        if (!Files.exists(envFile)) {
            Path example = envFile.getParent().resolve(".env.example");
            if (Files.exists(example)) {
                Files.copy(example, envFile);
                warn("Created " + envFile + " from template — edit it to add your API keys.");
            } else {
                err("No .env file found at " + envFile);
                System.exit(1);
            }
        }

        try (Stream<String> lines = Files.lines(envFile)) {
            lines.forEach(line -> {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return;
                int eq = trimmed.indexOf('=');
                if (eq <= 0) return;
                String key = trimmed.substring(0, eq);
                String value = trimmed.substring(eq + 1);
                if (value.isBlank()) return;

                // Real env vars take precedence
                if (System.getenv(key) != null) return;

                System.setProperty(key, value);
            });
        }

        ok("Loaded configuration from " + envFile);
    }

    // ── JSON parsing (no Jackson) ───────────────────────────────────────────

    static String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) {
            search = "\"" + field + "\": \"";
            start = json.indexOf(search);
        }
        if (start < 0) return "unknown";
        start += search.length();
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : "unknown";
    }

    // ── Output helpers ──────────────────────────────────────────────────────

    static void info(String msg) { System.out.printf("%s▸%s %s%n", CYAN, NC, msg); }
    static void ok(String msg)   { System.out.printf("%s✓%s %s%n", GREEN, NC, msg); }
    static void warn(String msg) { System.out.printf("%s!%s %s%n", YELLOW, NC, msg); }
    static void err(String msg)  { System.err.printf("%s✗%s %s%n", RED, NC, msg); }
    static void header(String msg) {
        System.out.printf("%n%s%s── %s ──%s%n%n", BOLD, CYAN, msg, NC);
    }
}

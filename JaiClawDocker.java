///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * JaiClaw Docker Launcher — JBang script for Docker-based deployment.
 *
 * Uses Docker Compose via the docker CLI to manage the gateway container.
 *
 * Usage:
 *   jbang JaiClawDocker.java              # start gateway via Docker Compose (default)
 *   jbang JaiClawDocker.java gateway      # start gateway via Docker Compose
 *   jbang JaiClawDocker.java cli          # start interactive CLI shell (Docker)
 *   jbang JaiClawDocker.java telegram     # validate Telegram token → start gateway (Docker)
 *   jbang JaiClawDocker.java stop         # stop Docker Compose stack
 *   jbang JaiClawDocker.java logs         # tail gateway container logs
 *   jbang JaiClawDocker.java help         # print usage
 */
public class JaiClawDocker {

    // ── ANSI colors ─────────────────────────────────────────────────────────
    static final String RED = "\033[0;31m";
    static final String GREEN = "\033[0;32m";
    static final String YELLOW = "\033[1;33m";
    static final String CYAN = "\033[0;36m";
    static final String DIM = "\033[2m";
    static final String BOLD = "\033[1m";
    static final String NC = "\033[0m";

    static Path projectRoot;
    static Path composeDir;
    static Path envFile;
    static Map<String, String> loadedEnv = new HashMap<>();

    public static void main(String[] args) throws Exception {
        // Resolve project root
        String jbangSource = System.getProperty("jbang.source");
        if (jbangSource != null) {
            projectRoot = Path.of(jbangSource).getParent();
        } else {
            projectRoot = Path.of("").toAbsolutePath();
        }
        composeDir = projectRoot.resolve("docker-compose");
        envFile = composeDir.resolve(".env");

        String command = args.length > 0 ? args[0] : "gateway";

        switch (command) {
            case "gateway" -> cmdGateway();
            case "cli" -> cmdCli();
            case "telegram" -> cmdTelegram();
            case "stop" -> cmdStop();
            case "logs" -> cmdLogs();
            case "-h", "--help", "help" -> printHelp();
            default -> {
                err("Unknown command: " + command);
                System.out.println("Run 'jbang JaiClawDocker.java help' for usage.");
                System.exit(1);
            }
        }
    }

    // ── Commands ────────────────────────────────────────────────────────────

    static void cmdGateway() throws Exception {
        header("JaiClaw Gateway (Docker)");
        loadEnv();
        ensureDocker();
        ensureImage("jaiclaw-gateway-app");

        info("Starting gateway container...");
        exec(composeDir, "docker", "compose", "-f", composeDir.resolve("docker-compose.yml").toString(), "up", "-d");

        String port = loadedEnv.getOrDefault("GATEWAY_PORT", "8080");
        System.out.println();
        ok("Gateway is running on http://localhost:" + port);
        System.out.println();
        System.out.println("Test it:");
        System.out.printf("  %scurl -X POST http://localhost:%s/api/chat \\%n", BOLD, port);
        System.out.printf("    -H \"Content-Type: application/json\" \\%n");
        System.out.printf("    -d '{\"content\": \"hello\"}'%s%n", NC);
        System.out.println();
        System.out.println("View logs:");
        System.out.printf("  %sjbang JaiClawDocker.java logs%s%n", BOLD, NC);
        System.out.println();
        System.out.println("Stop:");
        System.out.printf("  %sjbang JaiClawDocker.java stop%s%n", BOLD, NC);
        System.out.println();

        info("Tailing logs (Ctrl+C to detach — gateway keeps running)...");
        System.out.println();
        exec(composeDir, "docker", "compose", "-f", composeDir.resolve("docker-compose.yml").toString(), "logs", "-f", "gateway");
    }

    static void cmdCli() throws Exception {
        header("JaiClaw Interactive Shell (Docker)");
        loadEnv();
        ensureDocker();
        ensureImage("jaiclaw-shell");

        System.out.println("Starting interactive shell container...");
        System.out.println();
        System.out.printf("  %sType 'help' for available commands%s%n", DIM, NC);
        System.out.printf("  %sType 'chat hello' to talk to the agent%s%n", DIM, NC);
        System.out.printf("  %sType 'onboard' to run the setup wizard%s%n", DIM, NC);
        System.out.println();

        exec(composeDir, "docker", "compose", "-f", composeDir.resolve("docker-compose.yml").toString(),
                "--profile", "cli", "run", "--rm", "cli");
    }

    static void cmdTelegram() throws Exception {
        header("JaiClaw + Telegram (Docker)");
        loadEnv();

        String token = getEnv("TELEGRAM_BOT_TOKEN");
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

    static void cmdStop() throws Exception {
        info("Stopping JaiClaw...");
        exec(composeDir, "docker", "compose", "-f", composeDir.resolve("docker-compose.yml").toString(), "down");
        ok("Stopped");
    }

    static void cmdLogs() throws Exception {
        exec(composeDir, "docker", "compose", "-f", composeDir.resolve("docker-compose.yml").toString(), "logs", "-f", "gateway");
    }

    static void printHelp() {
        System.out.println("Usage: jbang JaiClawDocker.java [command]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  gateway          Start gateway via Docker Compose (default)");
        System.out.println("  cli              Start interactive CLI shell (Docker)");
        System.out.println("  telegram         Validate Telegram bot token and start gateway (Docker)");
        System.out.println("  stop             Stop Docker Compose stack");
        System.out.println("  logs             Tail gateway container logs");
        System.out.println();
        System.out.println("Configuration is loaded from docker-compose/.env");
        System.out.println("Edit that file to set API keys, provider, and channel tokens.");
        System.out.println();
        System.out.println("For local (no Docker) deployment, use: jbang JaiClaw.java");
    }

    // ── Environment loading ─────────────────────────────────────────────────

    static void loadEnv() throws IOException {
        if (!Files.exists(envFile)) {
            Path example = composeDir.resolve(".env.example");
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
                // Only load if not already set in environment
                if (System.getenv(key) == null) {
                    loadedEnv.put(key, value);
                }
            });
        }

        ok("Loaded configuration from " + envFile);
    }

    static String getEnv(String key) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) return value;
        return loadedEnv.get(key);
    }

    // ── Checks ──────────────────────────────────────────────────────────────

    static void ensureDocker() throws Exception {
        try {
            Process process = new ProcessBuilder("docker", "info")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                err("Docker daemon is not running. Start Docker Desktop and try again.");
                System.exit(1);
            }
        } catch (IOException e) {
            err("Docker is not installed. Install Docker Desktop: https://docs.docker.com/desktop/");
            System.exit(1);
        }
    }

    static void ensureImage(String module) throws Exception {
        String image = "io.jaiclaw/" + module + ":0.1.0-SNAPSHOT";
        Process process = new ProcessBuilder("docker", "image", "inspect", image)
                .redirectErrorStream(true)
                .start();
        process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            warn("Docker image for " + module + " not found. Building...");
            ensureJava();
            info("Running: ./mvnw package k8s:build -pl " + module + " -am -Pk8s -DskipTests");
            exec(projectRoot, projectRoot.resolve("mvnw").toString(),
                    "package", "k8s:build", "-pl", module, "-am", "-Pk8s", "-DskipTests");
            ok("Docker image built: " + image);
        }
    }

    static void ensureJava() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            Path javaBin = Path.of(javaHome, "bin", "java");
            if (Files.isExecutable(javaBin)) {
                int version = getJavaVersion(javaBin.toString());
                if (version >= 21) return;
            }
        }

        // Try system java
        try {
            int version = getJavaVersion("java");
            if (version >= 21) return;
        } catch (Exception ignored) {}

        err("Java 21+ is required to build Docker images. Install with: sdk install java 21.0.9-oracle");
        System.exit(1);
    }

    static int getJavaVersion(String javaBin) {
        try {
            ProcessBuilder pb = new ProcessBuilder(javaBin, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();

            int start = output.indexOf('"');
            if (start >= 0) {
                int end = output.indexOf('.', start);
                if (end >= 0) {
                    String major = output.substring(start + 1, end);
                    return Integer.parseInt(major);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    // ── Process execution ───────────────────────────────────────────────────

    static void exec(Path workDir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.inheritIO();

        // Pass loaded env vars to subprocess
        Map<String, String> env = pb.environment();
        for (Map.Entry<String, String> entry : loadedEnv.entrySet()) {
            if (!env.containsKey(entry.getKey())) {
                env.put(entry.getKey(), entry.getValue());
            }
        }

        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
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

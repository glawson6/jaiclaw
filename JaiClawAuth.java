///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//REPOS mavenLocal,mavenCentral
//DEPS io.jaiclaw:jaiclaw-identity:0.1.0-SNAPSHOT
//DEPS io.jaiclaw:jaiclaw-core:0.1.0-SNAPSHOT
//DEPS tools.jackson.core:jackson-databind:2.17.2
//DEPS org.slf4j:slf4j-simple:2.0.16

import io.jaiclaw.core.auth.*;
import io.jaiclaw.identity.auth.AuthProfileStoreManager;
import io.jaiclaw.identity.auth.AuthProfileStoreSerializer;
import io.jaiclaw.identity.oauth.*;
import io.jaiclaw.identity.oauth.provider.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/**
 * JBang script for JaiClaw OAuth operations outside the Spring Shell REPL.
 *
 * Usage:
 *   jbang JaiClawAuth.java login <provider>     — run OAuth flow
 *   jbang JaiClawAuth.java login --list         — list providers
 *   jbang JaiClawAuth.java status               — show auth profile status
 *   jbang JaiClawAuth.java logout <profileId>   — remove a profile
 */
public class JaiClawAuth {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public static void main(String... args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (command) {
            case "login" -> handleLogin(rest);
            case "status" -> handleStatus();
            case "logout" -> handleLogout(rest);
            case "--help", "-h", "help" -> printUsage();
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
                System.exit(1);
            }
        }
    }

    private static void handleLogin(String[] args) {
        if (args.length == 0 || "--list".equals(args[0])) {
            printProviders();
            return;
        }

        String providerId = args[0];
        Map<String, OAuthProviderConfig> providers = buildProviderMap();

        if (!providers.containsKey(providerId)) {
            System.err.println("Unknown provider: " + providerId);
            System.err.println();
            printProviders();
            System.exit(1);
        }

        Path stateDir = resolveStateDir();
        AuthProfileStoreManager storeManager = new AuthProfileStoreManager(stateDir);
        OAuthFlowManager flowManager = new OAuthFlowManager(providers, storeManager);
        Path agentDir = storeManager.resolveMainAgentDir();

        Scanner scanner = new Scanner(System.in);
        Supplier<String> inputHandler = scanner::nextLine;

        try {
            OAuthFlowResult result = flowManager.login(
                    providerId, agentDir, System.out::println, inputHandler);
            System.out.println();
            System.out.println("Login successful!");
            if (result.email() != null) {
                System.out.println("  Email: " + result.email());
            }
            System.out.println("  Expires: " + DATE_FMT.format(Instant.ofEpochMilli(result.expiresAt())));
        } catch (OAuthFlowException e) {
            System.err.println("Login failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void handleStatus() {
        Path stateDir = resolveStateDir();
        AuthProfileStoreManager storeManager = new AuthProfileStoreManager(stateDir, true);
        AuthProfileStore store = storeManager.loadMainStore();
        long now = System.currentTimeMillis();

        System.out.println();
        System.out.println("── JaiClaw Auth Profiles ──");
        System.out.println();

        if (store.profiles().isEmpty()) {
            System.out.println("  (no profiles)");
            System.out.println();
            System.out.println("  Run 'jbang JaiClawAuth.java login <provider>' to authenticate.");
            return;
        }

        System.out.printf("  %-35s %-10s %-12s %s%n", "PROFILE ID", "TYPE", "STATE", "EXPIRES");
        System.out.println("  " + "─".repeat(75));

        for (Map.Entry<String, AuthProfileCredential> entry : store.profiles().entrySet()) {
            String profileId = entry.getKey();
            AuthProfileCredential cred = entry.getValue();

            String type;
            String state;
            String expiresStr = "-";

            switch (cred) {
                case OAuthCredential oauth -> {
                    type = "oauth";
                    long expires = oauth.expires();
                    if (expires <= 0) {
                        state = "MISSING";
                    } else if (expires <= now) {
                        state = "EXPIRED";
                    } else if ((expires - now) < 3600000) {
                        state = "EXPIRING";
                    } else {
                        state = "OK";
                    }
                    if (expires > 0) {
                        expiresStr = DATE_FMT.format(Instant.ofEpochMilli(expires))
                                + " (" + formatRemaining(expires - now) + ")";
                    }
                }
                case ApiKeyCredential apiKey -> {
                    type = "api_key";
                    state = "ok";
                }
                case TokenCredential token -> {
                    type = "token";
                    state = "ok";
                }
            }

            System.out.printf("  %-35s %-10s %-12s %s%n", profileId, type, state, expiresStr);
        }
        System.out.println();
    }

    private static void handleLogout(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: jbang JaiClawAuth.java logout <profileId>");
            System.exit(1);
        }

        String profileId = args[0];
        Path stateDir = resolveStateDir();
        AuthProfileStoreManager storeManager = new AuthProfileStoreManager(stateDir);
        Path agentDir = storeManager.resolveMainAgentDir();

        AuthProfileStore store = storeManager.loadMainStore();
        if (!store.profiles().containsKey(profileId)) {
            System.err.println("Profile not found: " + profileId);
            System.err.println("Available profiles:");
            store.profiles().keySet().forEach(id -> System.err.println("  " + id));
            System.exit(1);
        }

        storeManager.removeProfile(agentDir, profileId);
        System.out.println("Removed profile: " + profileId);
    }

    private static Map<String, OAuthProviderConfig> buildProviderMap() {
        Map<String, OAuthProviderConfig> providers = new LinkedHashMap<>();
        OAuthProviderConfig chutes = ChutesOAuthProvider.config();
        providers.put(chutes.providerId(), chutes);
        OAuthProviderConfig codex = OpenAiCodexOAuthProvider.config();
        providers.put(codex.providerId(), codex);
        OAuthProviderConfig gemini = GoogleGeminiOAuthProvider.config();
        providers.put(gemini.providerId(), gemini);
        OAuthProviderConfig qwen = QwenDeviceCodeProvider.config();
        providers.put(qwen.providerId(), qwen);
        OAuthProviderConfig minimax = MiniMaxDeviceCodeProvider.config();
        providers.put(minimax.providerId(), minimax);
        return providers;
    }

    private static void printProviders() {
        System.out.println();
        System.out.println("Available OAuth providers:");
        System.out.println();
        System.out.println("  chutes             Chutes AI (browser OAuth)");
        System.out.println("  openai-codex       OpenAI Codex (browser OAuth)");
        System.out.println("  google-gemini-cli  Google Gemini CLI (browser OAuth)");
        System.out.println("  qwen-portal        Qwen Portal (device code)");
        System.out.println("  minimax-portal     MiniMax Portal (device code)");
        System.out.println();
        System.out.println("Usage: jbang JaiClawAuth.java login <provider>");
        System.out.println();
    }

    private static Path resolveStateDir() {
        String envDir = System.getenv("JAICLAW_STATE_DIR");
        if (envDir != null && !envDir.isBlank()) {
            return Path.of(envDir);
        }
        return Path.of(System.getProperty("user.home"), ".jaiclaw");
    }

    private static String formatRemaining(long remainingMs) {
        if (remainingMs <= 0) return "expired";
        long totalSeconds = remainingMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private static void printUsage() {
        System.out.println("JaiClaw Auth — OAuth operations outside the Spring Shell REPL");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  jbang JaiClawAuth.java login <provider>   Run OAuth login flow");
        System.out.println("  jbang JaiClawAuth.java login --list       List available providers");
        System.out.println("  jbang JaiClawAuth.java status             Show auth profile status");
        System.out.println("  jbang JaiClawAuth.java logout <profileId> Remove a profile");
        System.out.println();
    }
}

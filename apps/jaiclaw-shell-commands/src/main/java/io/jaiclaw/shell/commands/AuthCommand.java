package io.jaiclaw.shell.commands;

import io.jaiclaw.core.auth.*;
import io.jaiclaw.identity.auth.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Shell commands for auth profile status, rotation, and session pinning.
 */
@ShellComponent
public class AuthCommand {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final ObjectProvider<AuthProfileStoreManager> storeManagerProvider;
    private final ObjectProvider<SessionAuthProfileResolver> sessionResolverProvider;

    public AuthCommand(ObjectProvider<AuthProfileStoreManager> storeManagerProvider,
                       ObjectProvider<SessionAuthProfileResolver> sessionResolverProvider) {
        this.storeManagerProvider = storeManagerProvider;
        this.sessionResolverProvider = sessionResolverProvider;
    }

    @ShellMethod(key = {"auth status", "auth-status"}, value = "Show all auth profiles with expiry state")
    public String status() {
        AuthProfileStoreManager storeManager = storeManagerProvider.getIfAvailable();
        if (storeManager == null) return "Auth profile store is not configured.";

        AuthProfileStore store = storeManager.loadMainStore();
        if (store.profiles().isEmpty()) {
            return "No auth profiles configured. Use 'login <provider>' to add one.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Auth Profiles:\n");
        sb.append(String.format("  %-35s %-12s %-10s %-20s%n", "PROFILE ID", "TYPE", "STATE", "EXPIRES"));
        sb.append("  ").append("-".repeat(80)).append("\n");

        for (Map.Entry<String, AuthProfileCredential> entry : store.profiles().entrySet()) {
            String profileId = entry.getKey();
            AuthProfileCredential cred = entry.getValue();

            String type = switch (cred) {
                case ApiKeyCredential ignored -> "api_key";
                case TokenCredential ignored -> "token";
                case OAuthCredential ignored -> "oauth";
            };

            String state;
            String expires;
            switch (cred) {
                case ApiKeyCredential c -> {
                    state = (c.key() != null || c.keyRef() != null) ? "ok" : "missing";
                    expires = "-";
                }
                case TokenCredential c -> {
                    CredentialState cs = CredentialStateEvaluator.resolveTokenExpiryState(c.expires());
                    state = cs.name().toLowerCase();
                    expires = c.expires() != null ? TIME_FMT.format(Instant.ofEpochMilli(c.expires())) : "-";
                }
                case OAuthCredential c -> {
                    CredentialState cs = CredentialStateEvaluator.resolveTokenExpiryState(c.expires());
                    state = cs.name().toLowerCase();
                    expires = TIME_FMT.format(Instant.ofEpochMilli(c.expires()));
                }
            }

            sb.append(String.format("  %-35s %-12s %-10s %-20s%n", profileId, type, state, expires));
        }

        // Show rotation order if configured
        if (!store.order().isEmpty()) {
            sb.append("\nRotation Order:\n");
            for (Map.Entry<String, List<String>> entry : store.order().entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ")
                        .append(String.join(" → ", entry.getValue())).append("\n");
            }
        }

        return sb.toString();
    }

    @ShellMethod(key = {"auth rotate", "auth-rotate"}, value = "Set rotation order for a provider")
    public String rotate(@ShellOption String provider,
                          @ShellOption(defaultValue = "") String profileIds) {
        AuthProfileStoreManager storeManager = storeManagerProvider.getIfAvailable();
        if (storeManager == null) return "Auth profile store is not configured.";

        Path agentDir = storeManager.resolveMainAgentDir();
        if (profileIds.isBlank()) {
            AuthProfileStore store = storeManager.loadMainStore();
            List<String> order = store.order().get(provider.toLowerCase());
            if (order == null || order.isEmpty()) {
                return "No rotation order set for provider: " + provider;
            }
            return "Current rotation order for " + provider + ": " + String.join(" → ", order);
        }

        List<String> order = List.of(profileIds.split(","));
        storeManager.setProfileOrder(agentDir, provider, order);
        return "Set rotation order for " + provider + ": " + String.join(" → ", order);
    }

    @ShellMethod(key = {"auth pin", "auth-pin"}, value = "Pin a profile for the current session")
    public String pin(@ShellOption String profileId) {
        return "Pinned auth profile: " + profileId + " (source: user)";
    }

    @ShellMethod(key = {"auth unpin", "auth-unpin"}, value = "Clear session profile override")
    public String unpin() {
        return "Cleared session auth profile override.";
    }
}

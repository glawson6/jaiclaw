package io.jaiclaw.shell.commands;

import io.jaiclaw.identity.auth.AuthProfileStoreManager;
import io.jaiclaw.identity.oauth.OAuthFlowException;
import io.jaiclaw.identity.oauth.OAuthFlowManager;
import io.jaiclaw.identity.oauth.OAuthFlowResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.Set;

/**
 * Shell commands for OAuth login and credential management.
 */
@ShellComponent
public class LoginCommand {

    private final ObjectProvider<OAuthFlowManager> flowManagerProvider;
    private final ObjectProvider<AuthProfileStoreManager> storeManagerProvider;

    public LoginCommand(ObjectProvider<OAuthFlowManager> flowManagerProvider,
                        ObjectProvider<AuthProfileStoreManager> storeManagerProvider) {
        this.flowManagerProvider = flowManagerProvider;
        this.storeManagerProvider = storeManagerProvider;
    }

    @ShellMethod(key = {"login"}, value = "Authenticate with an OAuth provider")
    public String login(
            @ShellOption(defaultValue = "") String provider,
            @ShellOption(value = "--list", defaultValue = "false") boolean listProviders) {

        OAuthFlowManager flowManager = flowManagerProvider.getIfAvailable();
        if (flowManager == null) {
            return "OAuth is not configured. Add jaiclaw-identity to your dependencies.";
        }

        if (listProviders || provider.isBlank()) {
            Set<String> providers = flowManager.listProviders();
            if (providers.isEmpty()) {
                return "No OAuth providers configured. Configure providers in jaiclaw.oauth.providers.";
            }
            StringBuilder sb = new StringBuilder("Available OAuth providers:\n");
            for (String p : providers) {
                sb.append("  - ").append(p).append("\n");
            }
            sb.append("\nUsage: login <provider>");
            return sb.toString();
        }

        if (flowManager.getProvider(provider).isEmpty()) {
            return "Unknown provider: " + provider + ". Use 'login --list' to see available providers.";
        }

        AuthProfileStoreManager storeManager = storeManagerProvider.getIfAvailable();
        if (storeManager == null) {
            return "Auth profile store is not configured.";
        }

        Path agentDir = storeManager.resolveMainAgentDir();
        PrintWriter out = new PrintWriter(System.out, true);
        Scanner scanner = new Scanner(System.in);

        try {
            OAuthFlowResult result = flowManager.login(
                    provider,
                    agentDir,
                    out::println,
                    () -> {
                        System.out.print("> ");
                        return scanner.nextLine().trim();
                    }
            );
            String identity = result.email() != null ? result.email() : "user";
            return "Logged in as " + identity + " (" + provider + ")";
        } catch (OAuthFlowException e) {
            return "Login failed: " + e.getMessage();
        }
    }

    @ShellMethod(key = {"logout"}, value = "Remove stored credentials for a profile")
    public String logout(@ShellOption String profileId) {
        AuthProfileStoreManager storeManager = storeManagerProvider.getIfAvailable();
        if (storeManager == null) {
            return "Auth profile store is not configured.";
        }

        Path agentDir = storeManager.resolveMainAgentDir();
        storeManager.removeProfile(agentDir, profileId);
        return "Removed credentials for profile: " + profileId;
    }
}

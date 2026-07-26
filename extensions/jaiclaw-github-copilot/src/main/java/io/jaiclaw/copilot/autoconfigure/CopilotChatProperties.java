package io.jaiclaw.copilot.autoconfigure;

import io.jaiclaw.copilot.CopilotChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Spring Boot configuration properties for the GitHub Copilot ChatModel
 * integration.
 *
 * <p>Bound to {@code jaiclaw.copilot.*}. The default {@link #enabled} value is
 * {@code false} — Copilot is <b>opt-in</b>, matching every other Spring AI
 * provider integration and reflecting its single-user auth model (see
 * {@code docs/user/COPILOT-INTEGRATION.md}).
 */
@ConfigurationProperties(prefix = "jaiclaw.copilot")
public class CopilotChatProperties {

    /**
     * Master switch. When {@code false} (default) no Copilot beans are
     * instantiated and the {@code gh copilot} CLI is not probed at startup.
     */
    private boolean enabled = false;

    @NestedConfigurationProperty
    private Auth auth = new Auth();

    @NestedConfigurationProperty
    private Session session = new Session();

    @NestedConfigurationProperty
    private Cli cli = new Cli();

    @NestedConfigurationProperty
    private Provider provider = new Provider();

    @NestedConfigurationProperty
    private Chat chat = new Chat();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Cli getCli() {
        return cli;
    }

    public void setCli(Cli cli) {
        this.cli = cli;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    /**
     * GitHub authentication settings.
     *
     * <p>Copilot's Java SDK delegates auth to the {@code gh copilot} CLI by
     * default — the CLI holds an OAuth token from {@code gh auth login} and
     * hands it to the SDK on demand. That path requires no configuration
     * here and is the recommended flow for interactive developer machines.
     *
     * <p>For headless / containerized deployments where an interactive
     * {@code gh auth login} isn't practical, set {@link #githubToken} to a
     * Personal Access Token (PAT) with the {@code copilot} scope. The SDK
     * bypasses the CLI's credential store and uses the PAT directly via
     * {@code CopilotClientOptions.setGithubToken(String)}.
     *
     * <p>For GitHub Enterprise Server (GHE)-hosted Copilot subscriptions,
     * set {@link #githubHost} to your GHE hostname. The value is injected
     * as the {@code GH_HOST} environment variable when the SDK spawns the
     * {@code gh} CLI child process — the CLI then targets that instance's
     * API rather than {@code github.com}.
     *
     * <p><b>No {@code github-user} field.</b> A PAT identifies its owner
     * server-side; the SDK does not accept a separate user identifier.
     * Setting one would be misleading. If you need to see which GitHub
     * identity is resolved at runtime, enable the CLI startup probe
     * ({@code jaiclaw.copilot.cli.check-on-startup=true} — the default) and
     * check the log for the {@code Copilot auth resolved} INFO line.
     */
    public static class Auth {
        /**
         * Optional GitHub PAT. When set, bypasses {@code gh auth login} and
         * hands the token directly to the SDK. Env-friendly:
         * {@code ${GITHUB_TOKEN:}} in {@code application.yml} pulls from
         * the process environment with a blank default. Leave {@code null}
         * (or blank) to use the CLI's stored credential.
         */
        private String githubToken;

        /**
         * Optional GitHub host — for GitHub Enterprise Server. Defaults to
         * {@code github.com}. Set to your GHE hostname (e.g.
         * {@code github.mycorp.example}) when using a GHE-hosted Copilot
         * subscription. Injected via the {@code GH_HOST} environment
         * variable in the CLI's spawn environment.
         */
        private String githubHost = "github.com";

        public String getGithubToken() { return githubToken; }
        public void setGithubToken(String githubToken) { this.githubToken = githubToken; }
        public String getGithubHost() { return githubHost; }
        public void setGithubHost(String githubHost) { this.githubHost = githubHost; }

        /**
         * True when a PAT is present and non-blank.
         */
        public boolean hasToken() {
            return githubToken != null && !githubToken.isBlank();
        }

        /**
         * True when a GHE (non-{@code github.com}) host is configured.
         */
        public boolean isEnterpriseHost() {
            return githubHost != null && !githubHost.isBlank()
                    && !"github.com".equalsIgnoreCase(githubHost);
        }
    }

    /**
     * Session lifecycle strategy. Per-call is the default and only
     * implementation in the initial cut; pool support is a follow-up.
     */
    public static class Session {
        /** {@code per-call} (default) or {@code pooled} (not yet implemented). */
        private String strategy = "per-call";
        /** Pooled strategy only — evict idle sessions after this many seconds. */
        private int maxIdleSeconds = 300;
        /** Pooled strategy only — cap on concurrent live sessions. */
        private int maxSessions = 32;

        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        public int getMaxIdleSeconds() { return maxIdleSeconds; }
        public void setMaxIdleSeconds(int maxIdleSeconds) { this.maxIdleSeconds = maxIdleSeconds; }
        public int getMaxSessions() { return maxSessions; }
        public void setMaxSessions(int maxSessions) { this.maxSessions = maxSessions; }
    }

    /**
     * Copilot CLI ({@code gh copilot}) settings. The SDK spawns the CLI as a
     * child process; these fields let operators point at a non-default binary
     * or turn off the startup probe.
     */
    public static class Cli {
        /** Path to the {@code gh} CLI binary; {@code null} → SDK default (PATH resolution). */
        private String binary;
        /**
         * Download URL for the {@code gh copilot} CLI. Maps to
         * {@code CopilotClientOptions.setCliUrl(String)}. Used only when the
         * SDK needs to bootstrap the CLI itself — if {@link #binary} points
         * at an existing installation, this field is ignored. {@code null}
         * → SDK default (a GitHub-hosted URL for the pinned CLI version).
         * Useful for air-gapped or internally-mirrored deployments.
         */
        private String url;
        /** Probe {@code gh copilot} + list entitled models at startup. Log a WARN if missing. */
        private boolean checkOnStartup = true;

        public String getBinary() { return binary; }
        public void setBinary(String binary) { this.binary = binary; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public boolean isCheckOnStartup() { return checkOnStartup; }
        public void setCheckOnStartup(boolean checkOnStartup) { this.checkOnStartup = checkOnStartup; }
    }

    /**
     * Provider-endpoint override, attached to every session's
     * {@code SessionConfig.setProvider(...)}. Maps to
     * {@link com.github.copilot.rpc.ProviderConfig}.
     *
     * <p>The intended use is pointing Copilot at an alternative backend
     * endpoint — an internal proxy, an OpenAI-compatible portal, an
     * Anthropic-compatible endpoint (e.g. MiniMax's
     * {@code api.minimax.io/anthropic}), or an Azure OpenAI deployment.
     * When all fields are {@code null} (the default) the module attaches
     * <b>no</b> ProviderConfig at all and Copilot uses its normal server-side
     * provider routing — which is what 99% of users want.
     *
     * <p><b>API keys and bearer tokens are deliberately NOT exposed here.</b>
     * The whole point of using Copilot as an integration is delegating auth
     * to the {@code gh copilot} CLI's OAuth token. Adding a second auth path
     * would defeat that design. If you need an API-keyed provider,
     * {@code jaiclaw-starter-anthropic} or {@code jaiclaw-starter-openai} is
     * the more direct route.
     */
    public static class Provider {
        /**
         * Provider type identifier (free-form string; see the SDK's
         * {@code ProviderConfig.setType(String)}). Common values:
         * {@code openai}, {@code anthropic}, {@code azure}, {@code google}.
         * {@code null} → SDK default (Copilot routes based on the selected
         * model).
         */
        private String type;
        /**
         * Backend endpoint URL. Maps to
         * {@code ProviderConfig.setBaseUrl(String)}. {@code null} → SDK
         * default (the provider's canonical endpoint).
         */
        private String baseUrl;
        /**
         * Wire-protocol override (e.g. {@code responses} or {@code chat}
         * on the OpenAI family, {@code messages} on Anthropic). Maps to
         * {@code ProviderConfig.setWireApi(String)}. {@code null} → SDK
         * default.
         */
        private String wireApi;
        /**
         * Transport override (e.g. {@code http}). Maps to
         * {@code ProviderConfig.setTransport(String)}. {@code null} → SDK
         * default.
         */
        private String transport;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getWireApi() { return wireApi; }
        public void setWireApi(String wireApi) { this.wireApi = wireApi; }
        public String getTransport() { return transport; }
        public void setTransport(String transport) { this.transport = transport; }

        /**
         * True when at least one field is set to a non-blank value — used by
         * the auto-config to decide whether to attach a {@code ProviderConfig}
         * to the session. Blank strings are treated as "unconfigured" so a
         * YAML {@code base-url: ""} doesn't accidentally attach an empty
         * override.
         */
        public boolean hasAnyValue() {
            return isSet(type) || isSet(baseUrl) || isSet(wireApi) || isSet(transport);
        }

        private static boolean isSet(String s) {
            return s != null && !s.isBlank();
        }
    }

    /**
     * ChatModel defaults. The {@code options} sub-block binds directly to
     * {@link CopilotChatOptions}; per-call {@code Prompt} options override.
     */
    public static class Chat {
        @NestedConfigurationProperty
        private CopilotChatOptions options = new CopilotChatOptions();

        public CopilotChatOptions getOptions() { return options; }
        public void setOptions(CopilotChatOptions options) { this.options = options; }
    }
}

package io.jaiclaw.copilot.autoconfigure;

import com.github.copilot.CopilotClient;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.ModelInfo;
import com.github.copilot.rpc.ProviderConfig;
import io.jaiclaw.copilot.CopilotApi;
import io.jaiclaw.copilot.CopilotChatModel;
import io.jaiclaw.copilot.CopilotChatOptions;
import io.jaiclaw.copilot.tool.CopilotToolMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Boot auto-configuration for the GitHub Copilot ChatModel.
 *
 * <p>Gated on:
 * <ol>
 *   <li>{@code jaiclaw.copilot.enabled=true} — opt-in (default false).</li>
 *   <li>{@link CopilotClient} on the classpath — provided by the module's own
 *       {@code com.github:copilot-sdk-java} dependency, so this is really an
 *       "SDK didn't get exclude'd" check.</li>
 * </ol>
 *
 * <p>Wires four beans: {@link CopilotChatProperties} (via
 * {@link EnableConfigurationProperties}), {@link CopilotApi} (starts the
 * underlying SDK client eagerly), {@link CopilotToolMapper}, and
 * {@link CopilotChatModel}.
 *
 * <p><b>Multi-tenancy note.</b> Copilot's auth is a single-user, CLI-hosted
 * token. In a JaiClaw deployment configured with
 * {@code jaiclaw.tenant.mode=multi}, this configuration logs a WARN (not an
 * ERROR — the operator may want it for a specific bounded use case) telling
 * the operator to review before promoting to production.
 */
@AutoConfiguration
@ConditionalOnClass(CopilotClient.class)
@ConditionalOnProperty(prefix = "jaiclaw.copilot", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CopilotChatProperties.class)
public class CopilotChatAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CopilotChatAutoConfiguration.class);

    /**
     * Builds and starts the {@link CopilotApi} wrapper. Runs the CLI/model
     * startup probe if {@code jaiclaw.copilot.cli.check-on-startup=true}
     * (the default) — this both surfaces missing-CLI errors early and logs
     * the entitled model matrix so operators can see what they can actually
     * use.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public CopilotApi copilotApi(CopilotChatProperties props, Environment env) {
        warnIfMultiTenant(env);

        CopilotApi api = buildApi(props);
        api.start();

        if (props.getCli().isCheckOnStartup()) {
            probeAndLog(api, props);
        }
        return api;
    }

    @Bean
    @ConditionalOnMissingBean
    public CopilotToolMapper copilotToolMapper() {
        return new CopilotToolMapper();
    }

    /**
     * The {@link CopilotChatModel} bean. Uses whatever
     * {@link CopilotChatOptions} the operator has configured under
     * {@code jaiclaw.copilot.chat.options.*} as its default; per-call
     * {@code Prompt} options override.
     */
    @Bean
    @ConditionalOnMissingBean
    public CopilotChatModel copilotChatModel(CopilotApi api, CopilotChatProperties props,
                                             CopilotToolMapper toolMapper) {
        CopilotChatOptions defaults = props.getChat().getOptions() == null
                ? new CopilotChatOptions()
                : props.getChat().getOptions();
        ProviderConfig providerOverride = buildProviderOverride(props.getProvider());
        log.info("Copilot ChatModel initialized — default model: {}, provider override: {}",
                defaults.getModel() == null ? "<sdk-default>" : defaults.getModel(),
                providerOverride == null ? "<none>" : describeProvider(providerOverride));
        return new CopilotChatModel(api, defaults, toolMapper, providerOverride);
    }

    /**
     * Builds a {@link ProviderConfig} from the operator's
     * {@link CopilotChatProperties.Provider} block, or returns {@code null}
     * when nothing is configured. Null tells {@link CopilotChatModel} to
     * skip attaching a ProviderConfig entirely and use Copilot's default
     * server-side routing.
     */
    ProviderConfig buildProviderOverride(CopilotChatProperties.Provider p) {
        if (p == null || !p.hasAnyValue()) {
            return null;
        }
        ProviderConfig cfg = new ProviderConfig();
        if (p.getType() != null && !p.getType().isBlank()) {
            cfg.setType(p.getType());
        }
        if (p.getBaseUrl() != null && !p.getBaseUrl().isBlank()) {
            cfg.setBaseUrl(p.getBaseUrl());
        }
        if (p.getWireApi() != null && !p.getWireApi().isBlank()) {
            cfg.setWireApi(p.getWireApi());
        }
        if (p.getTransport() != null && !p.getTransport().isBlank()) {
            cfg.setTransport(p.getTransport());
        }
        return cfg;
    }

    private String describeProvider(ProviderConfig cfg) {
        StringBuilder sb = new StringBuilder();
        if (cfg.getType() != null) sb.append("type=").append(cfg.getType()).append(" ");
        if (cfg.getBaseUrl() != null) sb.append("baseUrl=").append(cfg.getBaseUrl()).append(" ");
        if (cfg.getWireApi() != null) sb.append("wireApi=").append(cfg.getWireApi()).append(" ");
        if (cfg.getTransport() != null) sb.append("transport=").append(cfg.getTransport());
        return sb.toString().trim();
    }

    // --- internals ---

    private CopilotApi buildApi(CopilotChatProperties props) {
        String binary = props.getCli().getBinary();
        String url = props.getCli().getUrl();
        CopilotChatProperties.Auth auth = props.getAuth();
        boolean hasToken = auth != null && auth.hasToken();
        boolean isGhe = auth != null && auth.isEnterpriseHost();

        // Only build a CopilotClientOptions when at least one field is set.
        // The SDK's default constructor is well-tuned for the common case
        // (gh on PATH, canonical CLI download URL, CLI-hosted token,
        // github.com host).
        boolean anyCli = (binary != null && !binary.isBlank())
                || (url != null && !url.isBlank());
        if (!anyCli && !hasToken && !isGhe) {
            return new CopilotApi();
        }

        CopilotClientOptions opts = new CopilotClientOptions();
        if (binary != null && !binary.isBlank()) {
            opts.setCliPath(binary);
        }
        if (url != null && !url.isBlank()) {
            opts.setCliUrl(url);
        }
        if (hasToken) {
            // Bypass the CLI's credential store — SDK will use this PAT
            // directly.
            opts.setGithubToken(auth.getGithubToken());
            log.info("Copilot auth: PAT provided (github-token) — bypassing gh CLI credential store");
        }
        if (isGhe) {
            // GHE routing: the SDK spawns the `gh` CLI as a child process,
            // and gh reads GH_HOST from its environment to decide which
            // instance to authenticate against. Inject it here alongside
            // the process's own env (starting from a copy so we don't
            // clobber PATH etc.).
            java.util.Map<String, String> env = new java.util.LinkedHashMap<>(System.getenv());
            env.put("GH_HOST", auth.getGithubHost());
            opts.setEnvironment(env);
            log.info("Copilot auth: GH_HOST={} — GitHub Enterprise Server routing enabled",
                    auth.getGithubHost());
        }
        return new CopilotApi(opts);
    }

    private void probeAndLog(CopilotApi api, CopilotChatProperties props) {
        try {
            var authStatus = api.getAuthStatus();
            if (authStatus == null || !authStatus.isAuthenticated()) {
                log.warn("Copilot auth resolved as UNAUTHENTICATED. Fix: `gh auth login` (or set jaiclaw.copilot.auth.github-token). Status: {}",
                        authStatus == null ? "<null>" : authStatus.getStatusMessage());
                return;
            }
            log.info("Copilot auth resolved: user={} host={} type={}",
                    authStatus.getLogin(), authStatus.getHost(), authStatus.getAuthType());
        } catch (Exception e) {
            log.warn("Copilot auth-status probe failed (continuing): {}", e.toString());
        }

        try {
            List<ModelInfo> models = api.listModels();
            if (models == null || models.isEmpty()) {
                log.warn("Copilot returned no entitled models. Is `gh auth login` current + `gh extension install github/gh-copilot` installed?");
                return;
            }
            String names = models.stream()
                    .map(m -> tryGetString(m, "getId", "getName"))
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(", "));
            log.info("Copilot entitled models ({}): {}", models.size(), names);

            String configured = props.getChat().getOptions() == null
                    ? null : props.getChat().getOptions().getModel();
            if (configured != null && !configured.isBlank()
                    && !names.contains(configured)) {
                log.warn("Configured default model '{}' is not in the entitled list. "
                        + "Requests may fail. Entitled: {}", configured, names);
            }
        } catch (Exception e) {
            log.warn("Copilot startup probe failed (client started OK, but model list unavailable): {}",
                    e.toString());
        }
    }

    /**
     * Best-effort field extraction on {@link ModelInfo} — the SDK's record
     * fields evolve; try a couple of well-known accessors and fall through
     * quietly if the shape doesn't match. Used only for log formatting.
     */
    private String tryGetString(Object obj, String... methodNames) {
        if (obj == null) return null;
        for (String name : methodNames) {
            try {
                Object v = obj.getClass().getMethod(name).invoke(obj);
                if (v instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (Exception ignored) {
                // try next
            }
        }
        return obj.toString();
    }

    private void warnIfMultiTenant(Environment env) {
        String mode = env.getProperty("jaiclaw.tenant.mode", "single");
        if ("multi".equalsIgnoreCase(mode)) {
            log.warn("jaiclaw.copilot.enabled=true detected with jaiclaw.tenant.mode=multi. "
                    + "Copilot auth is a single-user CLI-hosted token — every tenant will share "
                    + "the same GitHub identity. Review before promoting to production.");
        }
    }
}

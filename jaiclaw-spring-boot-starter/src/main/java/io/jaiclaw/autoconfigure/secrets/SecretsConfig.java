package io.jaiclaw.autoconfigure.secrets;

import io.jaiclaw.core.secrets.EnvironmentSecretsProvider;
import io.jaiclaw.core.secrets.FileSecretsProvider;
import io.jaiclaw.core.secrets.SecretsProvider;
import io.jaiclaw.core.secrets.SecretsResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper that builds a {@link SecretsResolver} from {@code jaiclaw.secrets.*}
 * properties.
 *
 * <p>Used by both {@link SecretsEnvironmentPostProcessor} (which runs
 * before {@code @ConfigurationProperties} binding so it can't use a
 * typed record) and {@code JaiClawSecretsAutoConfiguration} (which
 * exposes the resolver as a bean). Keeping the construction in one
 * place ensures the early and late wirings agree.
 *
 * <p>0.9.2 secrets baseline.
 */
public final class SecretsConfig {

    private static final Logger log = LoggerFactory.getLogger(SecretsConfig.class);

    static final String PROP_PROVIDER = "jaiclaw.secrets.provider";
    static final String PROP_CHAIN = "jaiclaw.secrets.chain";
    static final String PROP_CHAIN_ON_ERROR = "jaiclaw.secrets.chain-on-error";
    static final String PROP_FILE_PATH = "jaiclaw.secrets.file.path";
    static final String PROP_OP_VAULT = "jaiclaw.secrets.onepassword.vault";
    static final String PROP_OP_BINARY = "jaiclaw.secrets.onepassword.op-binary";
    static final String PROP_OP_TOKEN = "jaiclaw.secrets.onepassword.service-account-token";
    static final String PROP_OP_TIMEOUT = "jaiclaw.secrets.onepassword.timeout";

    private SecretsConfig() {}

    /**
     * Build a resolver from the configured {@code jaiclaw.secrets.*}
     * properties, or {@code null} if no provider is configured.
     *
     * <p>When called early (from an {@link
     * org.springframework.boot.env.EnvironmentPostProcessor}), the
     * 1Password provider is constructed reflectively to avoid a
     * compile-time dependency on the optional extension module.
     */
    public static SecretsResolver build(Environment env, ProviderFactory providerFactory) {
        String provider = env.getProperty(PROP_PROVIDER, "").trim();
        if (provider.isEmpty()) {
            return null;
        }
        List<String> chainNames = switch (provider) {
            case "composite" -> {
                String raw = env.getProperty(PROP_CHAIN, "env,file");
                yield Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            }
            default -> List.of(provider);
        };

        List<SecretsProvider> chain = new ArrayList<>();
        for (String name : chainNames) {
            SecretsProvider p = providerFactory.create(name, env);
            if (p != null) {
                chain.add(p);
            } else {
                log.warn("jaiclaw.secrets: unknown provider '{}', skipping", name);
            }
        }
        if (chain.isEmpty()) {
            log.warn("jaiclaw.secrets: chain resolved to empty, no resolver will be installed");
            return null;
        }

        SecretsResolver.OnError onError = "fail".equalsIgnoreCase(
                env.getProperty(PROP_CHAIN_ON_ERROR, "continue"))
                ? SecretsResolver.OnError.FAIL
                : SecretsResolver.OnError.CONTINUE;
        return new SecretsResolver(chain, onError);
    }

    /**
     * Strategy for constructing providers by name. Lets the env-pp and
     * the auto-config share construction logic while differing in how
     * they handle the optional 1Password extension.
     */
    public interface ProviderFactory {
        SecretsProvider create(String name, Environment env);
    }

    /** Build the default env-pp factory (1Password via reflection). */
    public static ProviderFactory defaultProviderFactory() {
        return (name, env) -> switch (name) {
            case "env" -> new EnvironmentSecretsProvider();
            case "file" -> {
                String path = env.getProperty(PROP_FILE_PATH);
                if (path == null || path.isBlank()) {
                    log.warn("jaiclaw.secrets.file.path not set, file provider disabled");
                    yield null;
                }
                yield new FileSecretsProvider(Paths.get(path));
            }
            case "onepassword" -> buildOnePasswordReflectively(env);
            default -> null;
        };
    }

    /**
     * Build the 1Password provider reflectively. Returns {@code null}
     * if the extension isn't on the classpath (this is fine — the
     * extension is optional).
     */
    private static SecretsProvider buildOnePasswordReflectively(Environment env) {
        String vault = env.getProperty(PROP_OP_VAULT);
        if (vault == null || vault.isBlank()) {
            log.warn("jaiclaw.secrets.onepassword.vault not set, 1password provider disabled");
            return null;
        }
        try {
            Class<?> clazz = Class.forName(
                    "io.jaiclaw.secrets.onepassword.OnePasswordSecretsProvider");
            Class<?> configClass = Class.forName(
                    "io.jaiclaw.secrets.onepassword.OnePasswordSecretsProvider$Config");
            String opBinary = env.getProperty(PROP_OP_BINARY, "op");
            String token = env.getProperty(PROP_OP_TOKEN);
            String timeoutStr = env.getProperty(PROP_OP_TIMEOUT, "10s");
            java.time.Duration timeout = parseDuration(timeoutStr);
            Object config = configClass.getDeclaredConstructors()[0]
                    .newInstance(opBinary, vault, token, timeout);
            return (SecretsProvider) clazz.getDeclaredConstructor(configClass).newInstance(config);
        } catch (ClassNotFoundException e) {
            log.warn("jaiclaw.secrets.provider=onepassword but jaiclaw-secrets-1password is not on "
                    + "classpath; add 'io.jaiclaw:jaiclaw-secrets-1password' to enable it");
            return null;
        } catch (ReflectiveOperationException e) {
            log.error("Failed to construct OnePasswordSecretsProvider", e);
            return null;
        }
    }

    /** Parse Spring-style duration: "10s", "500ms", "1m". */
    static java.time.Duration parseDuration(String s) {
        String t = s.trim();
        if (t.endsWith("ms")) {
            return java.time.Duration.ofMillis(Long.parseLong(t.substring(0, t.length() - 2).trim()));
        }
        if (t.endsWith("s")) {
            return java.time.Duration.ofSeconds(Long.parseLong(t.substring(0, t.length() - 1).trim()));
        }
        if (t.endsWith("m")) {
            return java.time.Duration.ofMinutes(Long.parseLong(t.substring(0, t.length() - 1).trim()));
        }
        return java.time.Duration.ofSeconds(Long.parseLong(t));
    }

    /** Convenience: build with the default provider factory. */
    public static SecretsResolver build(Environment env) {
        return build(env, defaultProviderFactory());
    }

    /** Capture all jaiclaw.secrets.* properties for diagnostics. */
    public static Map<String, String> debugSnapshot(Environment env) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String key : List.of(PROP_PROVIDER, PROP_CHAIN, PROP_CHAIN_ON_ERROR,
                PROP_FILE_PATH, PROP_OP_VAULT, PROP_OP_BINARY)) {
            String v = env.getProperty(key);
            if (v != null) {
                snapshot.put(key, v);
            }
        }
        return snapshot;
    }
}

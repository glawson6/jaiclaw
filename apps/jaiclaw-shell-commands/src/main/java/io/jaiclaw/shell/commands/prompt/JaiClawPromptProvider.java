package io.jaiclaw.shell.commands.prompt;

import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import org.jline.utils.AttributedString;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.shell.jline.PromptProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the interactive Spring Shell prompt from
 * {@link PromptProperties#format()}. Resolved per-render (not cached) so
 * {@code prompt set ...} takes effect in the same session.
 *
 * <p>Supported placeholders:
 * <ul>
 *   <li>{@code ${identity}} — {@code jaiclaw.identity.name}</li>
 *   <li>{@code ${agent}} — {@code jaiclaw.agent.default-agent}</li>
 *   <li>{@code ${profile}} — basename of {@code JAICLAW_PROFILE_DIR} (env), or
 *       {@code default}</li>
 *   <li>{@code ${model}} — best-effort from {@code spring.ai.*.chat.options.model};
 *       literal {@code ${model}} when not resolvable</li>
 *   <li>{@code ${tenant}} — {@link TenantContextHolder#get()} tenant id, empty
 *       string in SINGLE-mode</li>
 *   <li>{@code ${version}} — jaiclaw-cli Maven version read from
 *       {@code META-INF/maven/io.jaiclaw/jaiclaw-cli/pom.properties} at
 *       classpath load (cached; resolved once per JVM). Useful for
 *       from-source installs where operators want to see the built
 *       version at a glance.</li>
 * </ul>
 *
 * <p>Unresolved placeholders render as literal {@code ${name}} so operators
 * see exactly which variable is missing.
 */
public class JaiClawPromptProvider implements PromptProvider {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9_.-]*)}");

    // Maven writes this file into every module jar. In the CLI fat jar, the
    // top-level entry is jaiclaw-cli's own pom.properties — the artifact whose
    // version we want in ${version}. Cached at classload time; if the file
    // isn't reachable, the placeholder renders as its literal form.
    private static final String VERSION = resolveJaiClawVersion();

    /** Package-private accessor so PromptCommands can render a faithful preview. */
    static String jaiClawVersion() {
        return VERSION;
    }

    private static String resolveJaiClawVersion() {
        String resource = "META-INF/maven/io.jaiclaw/jaiclaw-cli/pom.properties";
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = JaiClawPromptProvider.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) return null;
            Properties props = new Properties();
            props.load(in);
            String v = props.getProperty("version");
            return (v != null && !v.isBlank()) ? v : null;
        } catch (IOException e) {
            return null;
        }
    }

    private final PromptProperties properties;
    private final ObjectProvider<JaiClawProperties> jaiClawProperties;
    private final Environment environment;

    public JaiClawPromptProvider(PromptProperties properties,
                                  ObjectProvider<JaiClawProperties> jaiClawProperties,
                                  Environment environment) {
        this.properties = properties;
        this.jaiClawProperties = jaiClawProperties;
        this.environment = environment;
    }

    @Override
    public AttributedString getPrompt() {
        String format = currentFormat();
        String rendered = substitute(format, variables());
        return new AttributedString(rendered);
    }

    private String currentFormat() {
        String live = rawProperty(environment, "jaiclaw.shell.prompt.format");
        if (live != null && !live.isBlank()) return live;
        return properties.format();
    }

    /**
     * Read a property WITHOUT triggering Spring's placeholder resolution.
     * {@link Environment#getProperty(String)} resolves {@code ${...}} inside
     * the returned value, which would corrupt our format string (the whole
     * point of the format is that it contains literal {@code ${...}}
     * placeholders we expand ourselves).
     */
    static String rawProperty(Environment env, String key) {
        if (env instanceof ConfigurableEnvironment configurable) {
            for (PropertySource<?> source : configurable.getPropertySources()) {
                Object value = source.getProperty(key);
                if (value != null) return value.toString();
            }
            return null;
        }
        return env.getProperty(key);
    }

    private Map<String, Supplier<String>> variables() {
        Map<String, Supplier<String>> vars = new HashMap<>();
        vars.put("identity", () -> {
            JaiClawProperties props = jaiClawProperties.getIfAvailable();
            if (props != null && props.identity() != null && props.identity().name() != null) {
                return props.identity().name();
            }
            return null;
        });
        vars.put("agent", () -> {
            JaiClawProperties props = jaiClawProperties.getIfAvailable();
            if (props != null && props.agent() != null && props.agent().defaultAgent() != null) {
                return props.agent().defaultAgent();
            }
            return null;
        });
        vars.put("profile", JaiClawPromptProvider::resolveProfile);
        vars.put("model", () -> {
            String m = rawProperty(environment, "spring.ai.anthropic.chat.options.model");
            if (m != null) return m;
            m = rawProperty(environment, "spring.ai.openai.chat.options.model");
            if (m != null) return m;
            return rawProperty(environment, "spring.ai.ollama.chat.options.model");
        });
        vars.put("tenant", JaiClawPromptProvider::resolveTenant);
        vars.put("version", () -> VERSION);
        return vars;
    }

    private static String resolveProfile() {
        String dir = System.getenv("JAICLAW_PROFILE_DIR");
        if (dir != null && !dir.isBlank()) {
            Path p = Paths.get(dir).getFileName();
            return p != null ? p.toString() : "default";
        }
        return "default";
    }

    private static String resolveTenant() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) return "";
        return ctx.getTenantId();
    }

    static String substitute(String format, Map<String, Supplier<String>> vars) {
        Matcher m = PLACEHOLDER.matcher(format);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Supplier<String> sup = vars.get(key);
            String value = sup != null ? sup.get() : null;
            // null → leave the placeholder literal so operators see the typo.
            // empty string is a valid value (e.g. ${tenant} in SINGLE mode).
            String replacement = (value != null) ? value : m.group(0);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}

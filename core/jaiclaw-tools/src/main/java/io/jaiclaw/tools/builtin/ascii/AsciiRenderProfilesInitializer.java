package io.jaiclaw.tools.builtin.ascii;

import io.jaiclaw.asciirender.profile.AsciiRenderProfile;
import io.jaiclaw.asciirender.profile.AsciiRenderProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeSet;

/**
 * Bridge between {@link AsciiRenderProperties} (Spring-binding) and the
 * static {@link AsciiRenderProfiles} registry (pure Java). Constructed
 * by {@code JaiClawToolsAutoConfiguration} at startup; its constructor
 * applies all operator-supplied profile overrides + the default-profile
 * setting into the registry as a side effect, then logs the final
 * registry shape at INFO so operators can verify what the renderer
 * sees.
 */
public final class AsciiRenderProfilesInitializer {

    private static final Logger log = LoggerFactory.getLogger(AsciiRenderProfilesInitializer.class);

    private final AsciiRenderProperties properties;

    public AsciiRenderProfilesInitializer(AsciiRenderProperties properties) {
        this.properties = properties;
        apply();
    }

    /**
     * Apply the configured properties to the registry. Public so tests
     * can re-trigger after mutating the static registry.
     */
    public void apply() {
        if (properties == null) {
            log.debug("AsciiRenderProfilesInitializer: no properties bound — using built-in defaults");
            return;
        }
        int registered = 0;
        if (properties.profiles() != null) {
            for (Map.Entry<String, AsciiRenderProperties.ProfileConfig> entry : properties.profiles().entrySet()) {
                String name = entry.getKey();
                AsciiRenderProperties.ProfileConfig cfg = entry.getValue();
                if (name == null || name.isBlank() || cfg == null) {
                    log.warn("Skipping malformed jaiclaw.ascii.profiles entry: name={}, cfg={}", name, cfg);
                    continue;
                }
                try {
                    AsciiRenderProfiles.register(new AsciiRenderProfile(name, cfg.width(), cfg.padding()));
                    registered++;
                } catch (IllegalArgumentException badProfile) {
                    log.warn("Skipping invalid jaiclaw.ascii.profile '{}' — {}", name, badProfile.getMessage());
                }
            }
        }

        // Apply the configured default profile after registration so an
        // operator-defined profile can be set as the default in one
        // application.yml pass.
        String configured = properties.defaultProfile();
        if (configured != null && !configured.isBlank()
                && !configured.equals(AsciiRenderProfiles.defaultName())) {
            try {
                AsciiRenderProfiles.setDefault(configured);
            } catch (IllegalArgumentException unknownDefault) {
                log.warn("jaiclaw.ascii.default-profile '{}' is not registered — keeping default '{}'. "
                                + "Known profiles: {}",
                        configured, AsciiRenderProfiles.defaultName(), AsciiRenderProfiles.names());
            }
        }

        log.info("AsciiRenderProfilesInitializer — registered {} operator profile(s); "
                        + "default '{}'; known profiles: {}",
                registered, AsciiRenderProfiles.defaultName(),
                new TreeSet<>(AsciiRenderProfiles.names()));
    }
}

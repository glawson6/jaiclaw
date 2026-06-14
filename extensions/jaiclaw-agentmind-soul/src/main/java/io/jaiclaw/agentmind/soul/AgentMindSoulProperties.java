package io.jaiclaw.agentmind.soul;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.List;

/**
 * Configuration surface for {@code jaiclaw-agentmind-soul}.
 *
 * <p>Two-axis opt-in per plan §6 cross-cutting checklist:
 * <ul>
 *   <li>{@link #enabled()} — pillar-level toggle. Default {@code false} —
 *       no Soul beans are wired until set to {@code true}.</li>
 *   <li>{@link Tenant#enabled()} — gates the tenant-scope Soul surface
 *       (operator-only REST + MCP write). Independent of pillar-level
 *       toggle; both must be {@code true} to activate.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "jaiclaw.agentmind.soul")
public record AgentMindSoulProperties(
        boolean enabled,
        String rootDir,
        Rest rest,
        Tenant tenant
) {
    @ConstructorBinding
    public AgentMindSoulProperties {
        if (rootDir == null || rootDir.isBlank()) {
            rootDir = System.getProperty("user.home") + "/.jaiclaw/agentmind/soul";
        }
        if (rest == null) rest = new Rest(false);
        if (tenant == null) tenant = new Tenant(false, List.of("ADMIN", "OPERATOR"));
    }

    public AgentMindSoulProperties() {
        this(false, null, null, null);
    }

    /**
     * Debug read endpoint at {@code GET /api/agentmind/soul}. Defaults OFF;
     * intended for ops use only, not for runtime consumers.
     */
    public record Rest(boolean enabled) {}

    /**
     * Tenant-scope sub-configuration. Both {@link AgentMindSoulProperties#enabled()}
     * AND {@link #enabled()} must be {@code true} for the tenant Soul beans to
     * activate.
     */
    public record Tenant(
            boolean enabled,
            List<String> writeRoles
    ) {
        public Tenant {
            if (writeRoles == null || writeRoles.isEmpty()) {
                writeRoles = List.of("ADMIN", "OPERATOR");
            }
        }
    }
}

package io.jaiclaw.agentmind.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.List;

/**
 * Configuration surface for {@code jaiclaw-agentmind-memory}.
 *
 * <p>Plan §6 cross-cutting checklist: pillar-level toggle defaults OFF;
 * tenant-scope sub-config is an independent opt-in atop the pillar gate.
 *
 * <p>Char budget defaults (analysis §4.2 + §10 open question, resolved
 * 2026-06-14):
 * <ul>
 *   <li>TENANT: 4,096 chars — shared institutional knowledge needs room</li>
 *   <li>AGENT:  2,200 chars — matches hermes MEMORY.md default</li>
 *   <li>PEER:   1,375 chars — matches hermes USER.md default</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "jaiclaw.agentmind.memory")
public record AgentMindMemoryProperties(
        boolean enabled,
        String rootDir,
        Budgets budgets,
        Rest rest,
        Tenant tenant
) {
    @ConstructorBinding
    public AgentMindMemoryProperties {
        if (rootDir == null || rootDir.isBlank()) {
            rootDir = System.getProperty("user.home") + "/.jaiclaw/agentmind/memory";
        }
        if (budgets == null) budgets = new Budgets(4096, 2200, 1375);
        if (rest == null) rest = new Rest(false);
        if (tenant == null) tenant = new Tenant(false, false, List.of("ADMIN", "OPERATOR"));
    }

    public AgentMindMemoryProperties() {
        this(false, null, null, null, null);
    }

    /** Per-scope char budgets used by the agent tool overflow check. */
    public record Budgets(int tenantChars, int agentChars, int peerChars) {
        public Budgets {
            if (tenantChars <= 0) tenantChars = 4096;
            if (agentChars <= 0) agentChars = 2200;
            if (peerChars <= 0) peerChars = 1375;
        }
    }

    /** Debug read endpoint at {@code GET /api/agentmind/memory/*}. Defaults OFF. */
    public record Rest(boolean enabled) {}

    /**
     * Tenant-scope sub-configuration. Both {@link AgentMindMemoryProperties#enabled()}
     * AND {@link #enabled()} must be {@code true} for the tenant Memory beans to
     * activate.
     *
     * @param enabled              gates the tenant-scope REST + MCP write surface
     * @param agentWriteEnabled    allows the agent tool to write to TENANT scope
     *                             when {@code true}. Default {@code false} — most
     *                             deployments will only want operator-curated
     *                             tenant Memory to prevent institutional poisoning.
     * @param writeRoles           HTTP role list for the operator write path
     */
    public record Tenant(
            boolean enabled,
            boolean agentWriteEnabled,
            List<String> writeRoles
    ) {
        public Tenant {
            if (writeRoles == null || writeRoles.isEmpty()) {
                writeRoles = List.of("ADMIN", "OPERATOR");
            }
        }
    }
}

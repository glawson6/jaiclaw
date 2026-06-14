package io.jaiclaw.agentmind.tendencies;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;
import java.util.List;

/**
 * Configuration surface for {@code jaiclaw-agentmind-tendencies}.
 *
 * <p>Plan §8 cross-cutting checklist: pillar-level toggle defaults OFF;
 * tenant-scope sub-config is an independent opt-in atop the pillar gate
 * (Phase 5 will wire it).
 *
 * <p>Defaults reflect plan §6 SPI inventory + plan §8.2.1:
 * <ul>
 *   <li>Cadence: min-interval 15m, min-turns 5</li>
 *   <li>Daily token cap: 100k per tenant (analysis §9 risk 1 mitigation)</li>
 *   <li>Striped executor queue: 4 entries per (tenant,user)</li>
 *   <li>Provider: {@code deterministic} (no LLM cost) — the LLM-driven
 *       provider is opt-in via {@code provider=local-llm}</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "jaiclaw.agentmind.tendencies")
public record AgentMindTendenciesProperties(
        boolean enabled,
        String rootDir,
        String provider,
        Cadence cadence,
        Executor executor,
        Cost cost,
        Rest rest,
        Tenant tenant
) {
    @ConstructorBinding
    public AgentMindTendenciesProperties {
        if (rootDir == null || rootDir.isBlank()) {
            rootDir = System.getProperty("user.home") + "/.jaiclaw/agentmind/tendencies";
        }
        if (provider == null || provider.isBlank()) provider = "deterministic";
        if (cadence == null) cadence = new Cadence(Duration.ofMinutes(15), 5);
        if (executor == null) executor = new Executor(4);
        if (cost == null) cost = new Cost(100_000);
        if (rest == null) rest = new Rest(false);
        if (tenant == null) tenant = new Tenant(false, Duration.ofHours(24), 3,
                Duration.ofHours(168), "majority", List.of("ADMIN", "OPERATOR"));
    }

    public AgentMindTendenciesProperties() {
        this(false, null, null, null, null, null, null, null);
    }

    /** Cadence-gate parameters. A pass runs only when BOTH thresholds are met. */
    public record Cadence(Duration minInterval, int minTurns) {
        public Cadence {
            if (minInterval == null) minInterval = Duration.ofMinutes(15);
            if (minTurns < 1) minTurns = 5;
        }
    }

    /** Striped per-(tenant,user) executor queue capacity. */
    public record Executor(int queueDepth) {
        public Executor {
            if (queueDepth < 1) queueDepth = 4;
        }
    }

    /** Per-tenant daily token cap + circuit breaker. */
    public record Cost(long dailyTokenCap) {
        public Cost {
            if (dailyTokenCap < 1) dailyTokenCap = 100_000;
        }
    }

    /** REST debug read endpoint. Defaults OFF. */
    public record Rest(boolean enabled) {}

    /**
     * Tenant-scope sub-configuration (Phase 5). Pillar AND tenant must both
     * be true for the tenant rollup pipeline to activate.
     */
    public record Tenant(
            boolean enabled,
            Duration rollupCadence,
            int rollupMinActiveUsers,
            Duration activeWindow,
            String rollupProvider,
            List<String> writeRoles
    ) {
        public Tenant {
            if (rollupCadence == null) rollupCadence = Duration.ofHours(24);
            if (rollupMinActiveUsers < 1) rollupMinActiveUsers = 3;
            if (activeWindow == null) activeWindow = Duration.ofHours(168);
            if (rollupProvider == null || rollupProvider.isBlank()) rollupProvider = "majority";
            if (writeRoles == null || writeRoles.isEmpty()) {
                writeRoles = List.of("ADMIN", "OPERATOR");
            }
        }
    }
}

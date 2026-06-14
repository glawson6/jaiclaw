package io.jaiclaw.agentmind.tendencies.hook;

import io.jaiclaw.agentmind.tendencies.cadence.TendenciesCadenceGate;
import io.jaiclaw.agentmind.tendencies.executor.StripedDialecticExecutor;
import io.jaiclaw.agentmind.tendencies.learning.TendenciesLearningProvider;
import io.jaiclaw.agentmind.tendencies.transcript.TranscriptSource;
import io.jaiclaw.core.agent.TendenciesStoreProvider;
import io.jaiclaw.core.hook.event.SessionEndedEvent;
import io.jaiclaw.core.model.Tendencies;
import io.jaiclaw.core.model.TendenciesScope;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Listens for {@link SessionEndedEvent} (void hook, priority 500), consults
 * the {@link TendenciesCadenceGate}, and dispatches a dialectic pass to
 * the {@link StripedDialecticExecutor} when the gate is green.
 *
 * <p>The dispatched work:
 * <ol>
 *   <li>Loads the current per-(tenant, user) Tendencies record</li>
 *   <li>Asks the {@link TendenciesLearningProvider} to compute an updated
 *       record from the recent transcript window</li>
 *   <li>Persists via {@link TendenciesStoreProvider#saveTendencies}
 *       (optimistic CAS)</li>
 *   <li>Calls {@link TendenciesCadenceGate#recordRun} so the cool-down
 *       starts</li>
 * </ol>
 *
 * <p>Plan §8 task 3.9.
 */
public class TendenciesDialecticTrigger implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(TendenciesDialecticTrigger.class);

    private static final int PRIORITY = 500;

    private final TranscriptSource transcriptSource;
    private final TendenciesCadenceGate cadenceGate;
    private final StripedDialecticExecutor executor;
    private final TendenciesStoreProvider store;
    private final TendenciesLearningProvider learningProvider;
    private final TenantGuard tenantGuard;

    public TendenciesDialecticTrigger(TranscriptSource transcriptSource,
                                      TendenciesCadenceGate cadenceGate,
                                      StripedDialecticExecutor executor,
                                      TendenciesStoreProvider store,
                                      TendenciesLearningProvider learningProvider,
                                      TenantGuard tenantGuard) {
        this.transcriptSource = transcriptSource;
        this.cadenceGate = cadenceGate;
        this.executor = executor;
        this.store = store;
        this.learningProvider = learningProvider;
        this.tenantGuard = tenantGuard;
    }

    @Override
    public PluginDefinition definition() {
        return PluginDefinition.builder()
                .id("agentmind-tendencies-dialectic-trigger")
                .name("AgentMind Tendencies Dialectic Trigger")
                .description("Schedules a cadence-gated dialectic pass on SessionEndedEvent.")
                .version("1.0.0")
                .kind(PluginKind.GENERAL)
                .build();
    }

    @Override
    public void register(PluginApi api) {
        api.on(SessionEndedEvent.class, event -> {
            onSessionEnded(event);
            return null;
        }, PRIORITY);
    }

    void onSessionEnded(SessionEndedEvent event) {
        String userKey = userKeyFromSessionKey(event.sessionKey());
        if (userKey == null) {
            log.debug("SessionEndedEvent without a parseable peer in sessionKey={} — skip", event.sessionKey());
            return;
        }
        String tenantId = resolveTenantId();
        List<String> transcript = transcriptSource.recentMessages(event.sessionKey());

        if (!cadenceGate.shouldRun(tenantId, userKey, transcript.size())) {
            return;
        }

        executor.submit(tenantId, userKey, () -> runDialectic(tenantId, userKey, transcript));
    }

    void runDialectic(String tenantId, String userKey, List<String> transcript) {
        try {
            Tendencies current = store.findTendencies(tenantId, TendenciesScope.USER, userKey)
                    .orElseGet(() -> Tendencies.forUser(tenantId, userKey, "", java.util.Map.of()));
            Optional<Tendencies> updated = learningProvider.learn(current, transcript);
            if (updated.isEmpty()) {
                log.debug("Dialectic pass produced no change for {}:{} — skipping write", tenantId, userKey);
                return;
            }
            store.saveTendencies(updated.get());
            cadenceGate.recordRun(tenantId, userKey);
            log.debug("Dialectic pass complete for {}:{} — version now {}", tenantId, userKey,
                    updated.get().version());
        } catch (RuntimeException e) {
            log.warn("Dialectic pass failed for {}:{}: {}", tenantId, userKey, e.getMessage());
        }
    }

    private String resolveTenantId() {
        if (tenantGuard == null) return "default";
        if (!tenantGuard.isMultiTenant()) return "default";
        return tenantGuard.requireTenantIfMulti();
    }

    /**
     * Session keys are of the shape {@code agentId:channel:accountId:peerId}.
     * The trigger derives the user key from the {@code (channel, peerId)}
     * pair using the same deterministic hash the
     * {@code TendenciesUserMessageInjector} uses, so write keys and read
     * keys agree.
     */
    static String userKeyFromSessionKey(String sessionKey) {
        if (sessionKey == null) return null;
        String[] parts = sessionKey.split(":", -1);
        if (parts.length < 4) return null;
        String channel = parts[1];
        String peer = parts[3];
        if (channel.isBlank() || peer.isBlank()) return null;
        return TendenciesUserMessageInjector.userKeyFor(channel, peer);
    }
}

package io.jaiclaw.pipeline.authoring;

import io.jaiclaw.pipeline.OutputDefinition;
import io.jaiclaw.pipeline.OutputType;
import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.TriggerDefinition;
import io.jaiclaw.pipeline.TriggerType;
import io.jaiclaw.pipeline.gateway.PipelineExecutionResult;
import io.jaiclaw.pipeline.gateway.PipelineGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;

/**
 * Deploys a draft to a sandboxed pipeline id, triggers it once with a
 * caller-supplied input payload, waits for completion, and undeploys.
 * The one-shot sandbox never overlaps with the draft's real id — even
 * if the same draft is currently deployed, its test-run runs
 * side-by-side on a scratch id.
 *
 * <p>Origin trace: test-runs deploy with origin {@code STUDIO} so the
 * URI-scheme allowlist applies — a test-run of a Studio-authored draft
 * cannot escape into a disallowed Camel URI.
 *
 * <p>Sandbox mutations vs. the source draft:
 * <ul>
 *   <li>The id is replaced with {@code __draft__{id}__{uuid8}} so the
 *       real pipeline (if deployed) is untouched.</li>
 *   <li>The trigger is forced to {@code MANUAL} — the test-run always
 *       submits via {@link PipelineGateway#triggerAndAwait} regardless
 *       of the draft's real trigger type.</li>
 *   <li>The output is forced to {@code LOG} — CHANNEL / CAMEL_URI
 *       side effects don't fire in a test run.</li>
 * </ul>
 */
public class PipelineTestRunService {

    private static final Logger log = LoggerFactory.getLogger(PipelineTestRunService.class);

    private final PipelineLifecycleManager lifecycleManager;
    private final PipelineGateway gateway;
    private final Duration defaultTimeout;

    public PipelineTestRunService(PipelineLifecycleManager lifecycleManager,
                                   PipelineGateway gateway,
                                   Duration defaultTimeout) {
        this.lifecycleManager = lifecycleManager;
        this.gateway = gateway;
        this.defaultTimeout = defaultTimeout == null ? Duration.ofMinutes(1) : defaultTimeout;
    }

    /**
     * Run one test invocation of a draft.
     *
     * @param draft  the draft to test
     * @param input  the input payload
     * @param tenant tenant to run under (nullable in single-tenant mode)
     * @param timeout blocking wait (defaults to the service's configured default when null)
     */
    public TestRunResult run(PipelineDraft draft, String input, String tenant, Duration timeout) {
        Duration wait = timeout != null ? timeout : defaultTimeout;
        String sandboxId = sandboxIdFor(draft.id());
        PipelineDefinition sandbox = sandboxDefinition(draft.definition(), sandboxId);

        lifecycleManager.deploy(sandbox, draft.origin() == null
                ? PipelineDraft.Origin.STUDIO.name()
                : draft.origin().name());
        try {
            PipelineExecutionResult result = gateway.triggerAndAwait(sandboxId, input, tenant, null, wait);
            return new TestRunResult(
                    result.executionId(),
                    result.status() == null ? "UNKNOWN" : result.status().name(),
                    result.stageOutputs() == null ? java.util.Map.of() : result.stageOutputs(),
                    result.totalDuration() == null ? 0L : result.totalDuration().toMillis(),
                    result.failureReason());
        } catch (Exception e) {
            log.warn("Test run for draft '{}' failed: {}", draft.id(), e.getMessage());
            return new TestRunResult(sandboxId, "FAILED", java.util.Map.of(), 0L, e.getMessage());
        } finally {
            try {
                lifecycleManager.undeploy(sandboxId, "test-run-cleanup");
            } catch (Exception e) {
                log.warn("Failed to undeploy test-run sandbox '{}': {}", sandboxId, e.getMessage());
            }
        }
    }

    // ── helpers ─────────────────────────────────────

    private static String sandboxIdFor(String draftId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return "__draft__" + draftId + "__" + suffix;
    }

    private static PipelineDefinition sandboxDefinition(PipelineDefinition source, String sandboxId) {
        TriggerDefinition manual = new TriggerDefinition(TriggerType.MANUAL, null, null, null);
        OutputDefinition log = new OutputDefinition(OutputType.LOG, null, null, source.output() == null
                ? null : source.output().template());
        return new PipelineDefinition(
                sandboxId,
                source.name() == null ? sandboxId : source.name() + " (test-run)",
                source.description(),
                source.tenantIds(),
                true,                                // ensure enabled
                manual,
                source.errorStrategy(),
                source.maxRetries(),
                source.deadLetterUri(),
                source.stages(),
                log,
                source.security(),
                source.resultTemplate());
    }

    /**
     * Return payload for {@link #run}. Carries the sandbox execution
     * id, the terminal status, the per-stage output map, wall-clock
     * duration in ms, and the failure reason on failure.
     */
    public record TestRunResult(
            String executionId,
            String status,
            java.util.Map<String, String> stageOutputs,
            long totalDurationMs,
            String failureReason
    ) {
    }
}

package io.jaiclaw.pipeline;

/**
 * A pipeline author's contract for the terminal, caller-visible result
 * of an execution — the human-readable string a downstream UI or channel
 * shows after {@code POST /api/pipelines/{id}/trigger} completes.
 *
 * <p>There are three author-facing ways to declare a result. In
 * resolution priority (first non-blank wins):
 *
 * <ol>
 *   <li><b>Stage bean writes {@link #RESULT_KEY} on the Camel exchange
 *       property.</b> The runtime copies it into {@code
 *       StageOutput.metadata} for that stage; the pipeline-completion
 *       path walks {@code stageOutputs} in reverse insertion order and
 *       picks the first stage that set it.</li>
 *   <li><b>Author declares {@code .resultTemplate(String)} on the
 *       {@code PipelineBuilder}.</b> Resolved via {@code TemplateResolver}
 *       against the completed context — supports {@code
 *       {{stages.<name>.output}}}, {@code {{stages.<name>.metadata.<key>}}},
 *       {@code {{input}}}, and {@code {{pipeline.*}}}. Persists to YAML.</li>
 *   <li><b>Nothing declared.</b> Falls back to {@code
 *       status.name()} — every successful execution renders as
 *       {@code "SUCCESS"} so callers never see a {@code null} on the
 *       happy path.</li>
 * </ol>
 *
 * <p>Implementations should be side-effect free — {@link #render()} may
 * be called by multiple observers (status endpoint, SSE broadcaster,
 * audit log) and must return the same string every time. The runtime
 * truncates the returned string to 4 KB (see
 * {@code PipelineExecutionSummary.MAX_RESULT_BYTES}).
 *
 * @see TextResult
 */
public interface PipelineResult {

    /**
     * Well-known key under which the runtime looks for a result — used
     * both as the {@code exchange.setProperty(...)} key that stage beans
     * write to and as the metadata key on {@link PipelineContext.StageOutput}
     * that the completion path reads from.
     */
    String RESULT_KEY = "__result__";

    /** Return the caller-visible summary string. */
    String render();
}

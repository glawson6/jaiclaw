package io.jaiclaw.pipeline.render;

/**
 * Per-stage status used by every {@code Pipeline*Renderer}. Derived from
 * the tracker's {@link io.jaiclaw.pipeline.tracking.ExecutionStatus} plus
 * the current-stage marker on
 * {@link io.jaiclaw.pipeline.tracking.PipelineExecutionSummary}.
 *
 * <p>The {@code glyph} column is what appears in ASCII renders next to
 * the stage name (single character). The {@code label} column is the
 * short uppercase word used in the ASCII table view's STATUS column
 * ({@code DONE}, {@code RUN}, etc.).
 */
public enum StageStatus {

    /** Stage has not yet been reached in this execution. */
    PENDING("·", "PEND"),
    /** Stage is currently executing. */
    RUNNING("~", "RUN"),
    /** Stage completed successfully. */
    DONE("✓", "DONE"),
    /** Stage threw an unhandled exception. */
    FAILED("✗", "FAIL"),
    /** Stage was skipped (currently unused — reserved for conditional-stage support). */
    SKIPPED("-", "SKIP");

    private final String glyph;
    private final String label;

    StageStatus(String glyph, String label) {
        this.glyph = glyph;
        this.label = label;
    }

    /** Single-character ASCII glyph rendered next to the stage name. */
    public String glyph() {
        return glyph;
    }

    /** Short uppercase label for the ASCII table STATUS column. */
    public String label() {
        return label;
    }

    /**
     * Lowercase name suitable for CSS class-name modifiers
     * (e.g. {@code jaiclaw-pipeline-stage--done}) and {@code data-*}
     * attributes.
     */
    public String cssModifier() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}

package io.jaiclaw.pipeline;

/**
 * Trivial {@link PipelineResult} carrying a plain string. Enough for the
 * common case where a stage bean computes a summary sentence and hands
 * it to the runtime unchanged.
 *
 * <p>Null input renders as the empty string — never {@code null}.
 */
public record TextResult(String text) implements PipelineResult {
    @Override
    public String render() {
        return text == null ? "" : text;
    }
}

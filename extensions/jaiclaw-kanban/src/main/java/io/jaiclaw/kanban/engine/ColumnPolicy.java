package io.jaiclaw.kanban.engine;

import io.jaiclaw.kanban.model.ColumnDefinition;
import io.jaiclaw.kanban.model.ProcessorDefinition;

/**
 * Resolved processor-policy view of a column: the parsed
 * {@link RestartPolicy}, {@code maxAttempts}, {@code idempotent}, and the
 * onSuccess / onFailure event names. Built once per column at processor
 * dispatch time so callers don't re-parse the raw strings each time.
 */
public record ColumnPolicy(
        String state,
        boolean hasProcessor,
        String processorType,
        String promptTemplate,
        boolean idempotent,
        RestartPolicy restartPolicy,
        int maxAttempts,
        String onSuccessEvent,
        String onFailureEvent
) {
    public static ColumnPolicy of(ColumnDefinition column) {
        ProcessorDefinition p = column.processor();
        if (p == null) {
            return new ColumnPolicy(column.state(), false, null, null, false,
                    RestartPolicy.FAIL, 0, null, null);
        }
        return new ColumnPolicy(
                column.state(), true,
                p.type(), p.promptTemplate(),
                p.idempotent(), RestartPolicy.parse(p.restartPolicy()),
                p.maxAttempts(), p.onSuccess(), p.onFailure());
    }
}

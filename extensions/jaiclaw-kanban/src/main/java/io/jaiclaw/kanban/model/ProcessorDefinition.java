package io.jaiclaw.kanban.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Optional processor wired to a column. When a card enters a column with a
 * processor configured, {@code ColumnProcessorManager} (Phase 3) submits
 * the card to the configured handler and fires {@code onSuccess} or
 * {@code onFailure} into the state engine on completion.
 *
 * <p>Phase 1 ships the data shape; the processor manager itself is Phase 3.
 *
 * @param type            handler type (e.g. {@code agent}, {@code bean})
 * @param promptTemplate  prompt template for {@code agent} processors —
 *                        supports {@code {{name}} {{description}} {{attempt}} {{idempotencyKey}}}
 * @param onSuccess       transition event fired on handler success
 * @param onFailure       transition event fired on handler exception
 * @param idempotent      whether re-execution of this processor is safe
 *                        (required for {@code requeue} restart policy)
 * @param restartPolicy   how the recovery manager treats a card found
 *                        RUNNING in this column at startup
 * @param maxAttempts     cap for {@code requeue} retries; 0 = unlimited
 * @param config          arbitrary handler-specific config
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessorDefinition(
        String type,
        String promptTemplate,
        String onSuccess,
        String onFailure,
        boolean idempotent,
        String restartPolicy,
        int maxAttempts,
        Map<String, String> config
) {
    public ProcessorDefinition {
        if (config == null) config = Map.of();
        if (restartPolicy == null || restartPolicy.isBlank()) restartPolicy = "fail";
    }
}

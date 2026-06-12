package io.jaiclaw.kanban.engine;

import io.jaiclaw.kanban.idempotency.EffectLedger;
import io.jaiclaw.kanban.idempotency.IdempotencyKeyBuilder;
import io.jaiclaw.tasks.TaskRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One concrete kind of column processor: an LLM agent run.
 *
 * <p>The processor never compiles against {@code AgentRuntime} — instead
 * it accepts a {@link Function} that the wiring app provides at
 * autoconfig time (same indirection {@code CronJobExecutor} uses). The
 * function is "given this card, return the agent's text reply"; how that
 * reply is produced (singleton agent, tenant agent, stub, etc.) is the
 * app's choice.
 *
 * <p>Compute-dedupe (analysis §6.8): before the function fires, the
 * processor checks the {@link EffectLedger} for a recorded result under
 * the card's current
 * {@link IdempotencyKeyBuilder} key. If present, the recorded value is
 * returned without invoking the runner — this is the safe-replay path
 * after a crash. After a successful run, the result is recorded in the
 * ledger keyed by the same idempotency key.
 *
 * <p>Prompt template variables: {@code {{name}}, {{description}},
 * {{attempt}}, {{idempotencyKey}}}.
 */
public class AgentColumnProcessor {

    private static final Logger log = LoggerFactory.getLogger(AgentColumnProcessor.class);

    static final String ATTEMPT_META_KEY = "kanban.attempts";

    private final Function<TaskRecord, String> agentRunner;
    private final IdempotencyKeyBuilder keyBuilder;
    private final EffectLedger ledger;

    public AgentColumnProcessor(Function<TaskRecord, String> agentRunner,
                                IdempotencyKeyBuilder keyBuilder,
                                EffectLedger ledger) {
        this.agentRunner = agentRunner;
        this.keyBuilder = keyBuilder;
        this.ledger = ledger;
    }

    /**
     * Render the prompt template, replay from ledger if possible, otherwise
     * call the agent runner and record the result. Throws whatever the
     * runner throws — the column-processor manager catches the exception
     * and fires {@code onFailure}.
     */
    public String process(TaskRecord card, ColumnPolicy policy) {
        if (!policy.hasProcessor() || policy.promptTemplate() == null) {
            return null;
        }
        String key = keyBuilder.build(card);

        // 1) Compute-dedupe: a prior attempt may have completed and recorded
        //    its result before a crash flipped the card status. Replay.
        Optional<String> recorded = ledger.lookup(key);
        if (recorded.isPresent()) {
            log.info("Replaying recorded result for {} (key={})", card.id(), key);
            return recorded.get();
        }

        int attempt = currentAttempt(card);
        Map<String, String> vars = new HashMap<>();
        vars.put("name", nullToEmpty(card.name()));
        vars.put("description", nullToEmpty(card.description()));
        vars.put("attempt", Integer.toString(attempt));
        vars.put("idempotencyKey", key);
        String prompt = renderTemplate(policy.promptTemplate(), vars);

        // Hand the renderer-resolved prompt to the runner via a transient
        // TaskRecord copy. The runner sees the rendered text in `description`
        // (the field most apps already read) and the original name unchanged.
        TaskRecord forRunner = card
                .withIdempotencyKey(key);
        TaskRecord asInvoked = withDescription(forRunner, prompt);

        String result = agentRunner.apply(asInvoked);
        ledger.record(key, result);
        return result;
    }

    private static int currentAttempt(TaskRecord card) {
        String raw = card.metadata().get(ATTEMPT_META_KEY);
        if (raw == null) return 1;
        try { return Math.max(1, Integer.parseInt(raw)); }
        catch (NumberFormatException e) { return 1; }
    }

    private static TaskRecord withDescription(TaskRecord card, String description) {
        return new TaskRecord(card.id(), card.name(), description,
                card.status(), card.deliveryState(), card.result(), card.error(),
                card.flowId(), card.metadata(),
                card.createdAt(), card.startedAt(), card.completedAt(), card.tenantId(),
                card.boardId(), card.state(), card.assignee(),
                card.version(), card.orderIndex(), card.idempotencyKey());
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /** Minimal {{var}} replacement — no escaping, no nested expressions. */
    static String renderTemplate(String template, Map<String, String> vars) {
        if (template == null) return "";
        Matcher m = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}").matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String replacement = vars.getOrDefault(m.group(1), "");
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}

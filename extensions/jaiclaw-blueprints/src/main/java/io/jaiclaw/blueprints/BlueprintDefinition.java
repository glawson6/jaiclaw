package io.jaiclaw.blueprints;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An automation blueprint — a parameterized scheduling template that renders
 * itself into four surfaces: dashboard form, CLI slash-command, docs card,
 * and agent seed prompt.
 *
 * <p>The blueprint has two templates:
 * <ul>
 *   <li>{@link #scheduleTemplate} — how the recurrence is described (e.g.
 *       {@code "{minute} {hour} * * {dow}"} — a partially-filled cron
 *       expression that becomes valid once slots are filled).</li>
 *   <li>{@link #promptTemplate} — the natural-language instruction the
 *       agent will run (e.g. {@code "Every {schedule_human}, scan the last
 *       {window} of $stripe events…"}).</li>
 * </ul>
 *
 * <p>Both templates use {@code {slotKey}} placeholders that match the
 * {@link BlueprintSlot#key} values. {@link #render} substitutes the values
 * a caller supplies.
 *
 * <p>Blueprint authors ship a static, code-defined dataclass (in Java: a
 * {@code @Bean} that returns a {@code BlueprintDefinition}) or a YAML
 * file loaded from {@code classpath:blueprints/} — both routes end up in
 * the {@link BlueprintRegistry}.
 *
 * @param id             unique kebab-case identifier
 * @param title          short human-facing name for cards and lists
 * @param description    2–3 sentence card body
 * @param category       broad grouping ("devops", "research", "github")
 * @param tags           short searchable tags
 * @param scheduleTemplate template that renders to the cron / iCal / event
 *                          trigger string once slots are filled
 * @param scheduleHuman  friendly human-readable version of the schedule
 *                       ("Daily at 9 AM"). Also templated on slots.
 * @param promptTemplate agent-facing prompt template with slot placeholders
 * @param slots          user-fillable slots
 * @param deepLinkPath   optional deep-link path (e.g.
 *                       {@code /blueprints/daily-security-audit}) that a
 *                       dashboard renderer can turn into a full URL
 */
public record BlueprintDefinition(
        String id,
        String title,
        String description,
        String category,
        List<String> tags,
        String scheduleTemplate,
        String scheduleHuman,
        String promptTemplate,
        List<BlueprintSlot> slots,
        String deepLinkPath
) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_.-]*)}");

    public BlueprintDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("blueprint.id must not be blank");
        }
        if (title == null || title.isBlank()) title = id;
        if (description == null) description = "";
        if (category == null || category.isBlank()) category = "uncategorized";
        if (tags == null) tags = List.of();
        if (scheduleTemplate == null) scheduleTemplate = "";
        if (scheduleHuman == null || scheduleHuman.isBlank()) scheduleHuman = scheduleTemplate;
        if (promptTemplate == null) promptTemplate = "";
        if (slots == null) slots = List.of();
        if (deepLinkPath == null || deepLinkPath.isBlank()) deepLinkPath = "/blueprints/" + id;
    }

    /**
     * Substitute each {@code {key}} in the template with {@code values.get(key)}.
     * Missing values leave the placeholder in place so operators see which
     * slot they still need to fill.
     */
    public static String render(String template, Map<String, String> values) {
        if (template == null || template.isEmpty()) return template;
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = values != null ? values.get(key) : null;
            m.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : m.group(0)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Convenience — {@link #render} against {@link #scheduleTemplate}.
     */
    public String renderSchedule(Map<String, String> values) {
        return render(scheduleTemplate, values);
    }

    /**
     * Convenience — {@link #render} against {@link #promptTemplate}.
     */
    public String renderPrompt(Map<String, String> values) {
        return render(promptTemplate, values);
    }

    /**
     * Convenience — {@link #render} against {@link #scheduleHuman}.
     */
    public String renderScheduleHuman(Map<String, String> values) {
        return render(scheduleHuman, values);
    }

    /**
     * The set of slot keys that are still unfilled given a supplied value map.
     * Useful for validation and for the agent-seed-prompt renderer that asks
     * the user for whichever slots the caller left blank.
     */
    public List<String> unfilledSlots(Map<String, String> values) {
        if (slots.isEmpty()) return List.of();
        return slots.stream()
                .filter(s -> s.required())
                .filter(s -> values == null || !values.containsKey(s.key())
                        || values.get(s.key()) == null
                        || values.get(s.key()).isBlank())
                .map(BlueprintSlot::key)
                .toList();
    }
}

package io.jaiclaw.blueprints;

import java.util.List;

/**
 * SPI for supplying blueprints from Java code (a {@code @Bean} implementing
 * this interface). Analogous to {@code JaiClawPipeline} for the pipeline
 * module — the "code source of truth" alongside YAML-file sources.
 *
 * <p>Enterprises building their own blueprints define a class that returns
 * a list of {@link BlueprintDefinition}, register it as a Spring bean, and
 * the auto-config picks it up alongside YAML-file blueprints in the
 * {@link BlueprintRegistry}.
 *
 * <p>Example:
 * <pre>{@code
 * @Configuration
 * class MyBlueprints implements Blueprints {
 *     @Override
 *     public List<BlueprintDefinition> define() {
 *         return List.of(
 *             new BlueprintDefinition(
 *                 "daily-standup-nudge",
 *                 "Daily standup nudge",
 *                 "Post to #team-eng every weekday at 9:15 AM…",
 *                 "team-ops",
 *                 List.of("standup", "slack"),
 *                 "15 9 * * MON-FRI",
 *                 "Weekdays at 9:15 AM",
 *                 "Post to the {channel} channel asking for standup updates.",
 *                 List.of(BlueprintSlot.text("channel", "Channel",
 *                         "#team-eng", "Slack channel to post to")),
 *                 null)
 *         );
 *     }
 * }
 * }</pre>
 */
public interface Blueprints {

    /**
     * Return the list of blueprints this provider contributes. Called once at
     * registry-init time; the returned list is stored by reference so
     * implementations should return an immutable list.
     */
    List<BlueprintDefinition> define();
}

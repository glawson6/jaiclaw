package io.jaiclaw.blueprints.pipeline;

import io.jaiclaw.blueprints.BlueprintDefinition;
import io.jaiclaw.blueprints.BlueprintSlot;
import io.jaiclaw.blueprints.Blueprints;

import java.util.List;

/**
 * Blueprint entry that points authoring agents at the {@code pipeline-author}
 * skill. Not a scheduled job — this blueprint has no {@code scheduleTemplate}.
 * Its {@link BlueprintDefinition#promptTemplate} tells an agent which slots
 * the user supplied and delegates the actual playbook to
 * {@code .claude/skills/pipeline-author/SKILL.md}.
 *
 * <p>Discovered by {@link io.jaiclaw.blueprints.BlueprintAutoConfiguration}
 * through the {@code ObjectProvider<Blueprints>} collection; the additional
 * {@code @Bean} factory in {@link PipelineAuthorBlueprintsConfiguration}
 * (this package) makes it visible without adopters wiring anything.
 */
public class PipelineAuthorBlueprints implements Blueprints {

    static final String BLUEPRINT_ID = "pipeline-author";

    @Override
    public List<BlueprintDefinition> define() {
        return List.of(new BlueprintDefinition(
                BLUEPRINT_ID,
                "Author a JaiClaw Pipeline",
                "Scaffold, validate, and wire a new JaiClaw pipeline from an "
                        + "inline YAML, per-file YAML, or Java DSL definition. Full "
                        + "step-by-step playbook lives in the Claude Code skill at "
                        + ".claude/skills/pipeline-author/SKILL.md.",
                "authoring",
                List.of("pipeline", "authoring", "scaffold"),
                null,
                null,
                "Author a JaiClaw pipeline named {pipeline_id} using {source_type} "
                        + "(one of: inline, per-file, java-dsl). Kick off with trigger "
                        + "{trigger_type} and first stage of type {stage_type} pointing at "
                        + "{stage_ref}. Follow the .claude/skills/pipeline-author playbook "
                        + "for the full sequence: scaffold → validate → wire trigger → "
                        + "verify via /actuator/pipelines. Do NOT deploy or trigger the "
                        + "pipeline in this pass — this blueprint is scaffold + "
                        + "static-validation only.",
                List.of(
                        new BlueprintSlot(
                                "pipeline_id",
                                "Pipeline id",
                                BlueprintSlot.SlotType.TEXT,
                                true,
                                null,
                                "Kebab-case identifier (e.g. 'invoice-processor')."),
                        new BlueprintSlot(
                                "source_type",
                                "Definition source",
                                BlueprintSlot.SlotType.CHOICE,
                                true,
                                "per-file",
                                "One of: inline, per-file, java-dsl. Per-file is the "
                                        + "most portable and matches what the Pipeline Studio exports."),
                        new BlueprintSlot(
                                "trigger_type",
                                "Trigger type",
                                BlueprintSlot.SlotType.CHOICE,
                                true,
                                "MANUAL",
                                "One of: MANUAL, HTTP, CRON, FILE, CAMEL_URI."),
                        new BlueprintSlot(
                                "stage_type",
                                "First stage type",
                                BlueprintSlot.SlotType.CHOICE,
                                true,
                                "PROCESSOR",
                                "One of: AGENT, PROCESSOR, CAMEL."),
                        new BlueprintSlot(
                                "stage_ref",
                                "Stage reference",
                                BlueprintSlot.SlotType.TEXT,
                                true,
                                null,
                                "For AGENT: the agentId. For PROCESSOR: the Spring bean name "
                                        + "(bare Function<String,String> or a "
                                        + "@PipelineProcessor-annotated ConfigurableStageProcessor). "
                                        + "For CAMEL: a Camel URI subject to the security allowlist.")),
                "/mcp/pipeline-authoring"));
    }
}

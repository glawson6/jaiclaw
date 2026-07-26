package io.jaiclaw.blueprints.pipeline

import io.jaiclaw.blueprints.BlueprintDefinition
import io.jaiclaw.blueprints.BlueprintSlot
import spock.lang.Specification

class PipelineAuthorBlueprintsSpec extends Specification {

    PipelineAuthorBlueprints provider = new PipelineAuthorBlueprints()

    def "define() returns exactly one blueprint with the pipeline-author id"() {
        when:
        List<BlueprintDefinition> defs = provider.define()

        then:
        defs.size() == 1
        defs[0].id() == "pipeline-author"
        defs[0].title() == "Author a JaiClaw Pipeline"
        defs[0].category() == "authoring"
        defs[0].tags() == ["pipeline", "authoring", "scaffold"]
    }

    def "description points callers at the SKILL.md playbook"() {
        when:
        BlueprintDefinition def0 = provider.define()[0]

        then:
        def0.description().contains(".claude/skills/pipeline-author/SKILL.md")
    }

    def "promptTemplate names every slot key so unfilled-slot reporting works"() {
        when:
        BlueprintDefinition def0 = provider.define()[0]
        String prompt = def0.promptTemplate()

        then:
        prompt.contains("{pipeline_id}")
        prompt.contains("{source_type}")
        prompt.contains("{trigger_type}")
        prompt.contains("{stage_type}")
        prompt.contains("{stage_ref}")
        // The prompt must be explicit that this blueprint stops at
        // static validation — deployment/trigger is out of scope.
        prompt.contains("Do NOT deploy or trigger")
    }

    def "slots list matches the five parameters the skill expects"() {
        when:
        List<BlueprintSlot> slots = provider.define()[0].slots()

        then:
        slots*.key() == ["pipeline_id", "source_type", "trigger_type", "stage_type", "stage_ref"]
        slots.every { it.required() }
        slots.every { it.description() != null && !it.description().isBlank() }
    }

    def "CHOICE slots ship the default value the skill will assume when the caller doesn't override"() {
        when:
        Map<String, BlueprintSlot> byKey = provider.define()[0].slots().collectEntries { [it.key(), it] }

        then:
        byKey["source_type"].type() == BlueprintSlot.SlotType.CHOICE
        byKey["source_type"].defaultValue() == "per-file"
        byKey["trigger_type"].type() == BlueprintSlot.SlotType.CHOICE
        byKey["trigger_type"].defaultValue() == "MANUAL"
        byKey["stage_type"].type() == BlueprintSlot.SlotType.CHOICE
        byKey["stage_type"].defaultValue() == "PROCESSOR"
    }

    def "unfilledSlots reports every required slot when nothing is supplied"() {
        when:
        BlueprintDefinition def0 = provider.define()[0]

        then:
        // BlueprintDefinition.unfilledSlots reports slots that lack a
        // *supplied* value — defaultValue on the slot is a UI hint,
        // not a substitute for the caller passing a value. All five
        // slots are required, so all five surface when nothing is filled.
        def0.unfilledSlots([:]) as Set ==
                ["pipeline_id", "source_type", "trigger_type", "stage_type", "stage_ref"] as Set
    }

    def "unfilledSlots narrows to the missing-required ones once some values are supplied"() {
        when:
        BlueprintDefinition def0 = provider.define()[0]
        Map<String, String> partial = [
                pipeline_id: "invoice-processor",
                source_type: "per-file",
                stage_ref: "upperCase"
        ]

        then:
        def0.unfilledSlots(partial) as Set == ["trigger_type", "stage_type"] as Set
    }

    def "PipelineAuthorBlueprintsConfiguration exposes a bean of the provider"() {
        given:
        PipelineAuthorBlueprintsConfiguration cfg = new PipelineAuthorBlueprintsConfiguration()

        when:
        PipelineAuthorBlueprints bean = cfg.pipelineAuthorBlueprints()

        then:
        bean != null
        bean.define()[0].id() == "pipeline-author"
    }
}

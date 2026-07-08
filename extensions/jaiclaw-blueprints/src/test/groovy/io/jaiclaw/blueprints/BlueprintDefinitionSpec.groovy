package io.jaiclaw.blueprints

import spock.lang.Specification

class BlueprintDefinitionSpec extends Specification {

    def "render substitutes filled slots"() {
        expect:
        BlueprintDefinition.render("Hello {name}, at {hour}", [name: "Alice", hour: "09"]) ==
                "Hello Alice, at 09"
    }

    def "render leaves unfilled placeholders literal so authors see missing slots"() {
        expect:
        BlueprintDefinition.render("Hello {name}, at {hour}", [name: "Alice"]) ==
                "Hello Alice, at {hour}"
    }

    def "render is a no-op on null / empty templates"() {
        expect:
        BlueprintDefinition.render(null, [:]) == null
        BlueprintDefinition.render("", [:]) == ""
    }

    def "renderSchedule and renderPrompt use the templates"() {
        given:
        def bp = new BlueprintDefinition(
                "b", "T", "d", "cat", ["t"],
                "0 {hour} * * *",
                "Daily at {hour}:00",
                "Do X at {hour}",
                [BlueprintSlot.integer("hour", "Hour", 9, "hour of day")],
                "/blueprints/b")

        expect:
        bp.renderSchedule([hour: "6"]) == "0 6 * * *"
        bp.renderPrompt([hour: "6"]) == "Do X at 6"
        bp.renderScheduleHuman([hour: "6"]) == "Daily at 6:00"
    }

    def "unfilledSlots returns required keys with missing values"() {
        given:
        def bp = new BlueprintDefinition(
                "b", "T", "d", "cat", [],
                "{a}", "{a}", "prompt {a} {b} {c}",
                [BlueprintSlot.text("a", "A", null, "required text"),
                 BlueprintSlot.text("b", "B", null, "required text"),
                 new BlueprintSlot("c", "C", BlueprintSlot.SlotType.TEXT, false, "default", "optional")],
                null)

        expect:
        bp.unfilledSlots([:]) == ["a", "b"]
        bp.unfilledSlots([a: "value"]) == ["b"]
        bp.unfilledSlots([a: "value", b: "value"]).isEmpty()
        // c is not required — it shouldn't appear even if missing
        bp.unfilledSlots([a: "1", b: "2"]).isEmpty()
    }

    def "compact ctor fills defaults for missing fields"() {
        when:
        def bp = new BlueprintDefinition("only-id-required", null, null, null, null,
                null, null, null, null, null)

        then:
        bp.id() == "only-id-required"
        bp.title() == "only-id-required"      // falls back to id
        bp.category() == "uncategorized"
        bp.tags() == []
        bp.slots() == []
        bp.deepLinkPath() == "/blueprints/only-id-required"
    }

    def "compact ctor rejects blank id"() {
        when:
        new BlueprintDefinition("", "t", "d", "c", [], "", "", "", [], null)

        then:
        thrown(IllegalArgumentException)
    }
}

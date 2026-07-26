package io.jaiclaw.asciirender.skill

import spock.lang.Specification

class RenderableTemplateRegistrySpec extends Specification {

    def "empty registry returns empty lookups"() {
        when:
        def registry = new RenderableTemplateRegistry([])

        then:
        registry.size() == 0
        !registry.find("anything").present
        registry.names().empty
        registry.unionParameterNames().empty
    }

    def "null collection is treated as empty"() {
        when:
        def registry = new RenderableTemplateRegistry(null)

        then:
        registry.size() == 0
        registry.names().empty
    }

    def "two templates register in insertion order + are looked up by name"() {
        given:
        def a = stubTemplate("event_card", ["event_id", "detail_level"] as Set)
        def b = stubTemplate("task_kanban", ["task_id", "status"] as Set)

        when:
        def registry = new RenderableTemplateRegistry([a, b])

        then:
        registry.size() == 2
        registry.names() as List == ["event_card", "task_kanban"]
        registry.find("event_card").get() == a
        registry.find("task_kanban").get() == b
        !registry.find("unknown").present
    }

    def "unionParameterNames dedupes across templates + preserves insertion order"() {
        given:
        def a = stubTemplate("event_card", ["event_id", "detail_level"] as Set)
        def b = stubTemplate("event_grid", ["event_id", "date_range", "detail_level"] as Set)

        when:
        def registry = new RenderableTemplateRegistry([a, b])

        then: "union of both templates' params, in first-seen order"
        registry.unionParameterNames() as List == ["event_id", "detail_level", "date_range"]
    }

    def "duplicate names: first wins, second is dropped"() {
        given:
        def first = stubTemplate("event_card", ["event_id"] as Set)
        def second = stubTemplate("event_card", ["different"] as Set)

        when:
        def registry = new RenderableTemplateRegistry([first, second])

        then:
        registry.size() == 1
        registry.find("event_card").get() == first
        // Second's params NOT reflected in the union (it was rejected).
        registry.unionParameterNames() as List == ["event_id"]
    }

    def "templates with blank names are skipped"() {
        given:
        def valid = stubTemplate("event_card", ["id"] as Set)
        def bad = stubTemplate("", ["nope"] as Set)
        def alsoBad = stubTemplate(null, ["nope"] as Set)

        when:
        def registry = new RenderableTemplateRegistry([bad, valid, alsoBad])

        then:
        registry.size() == 1
        registry.names() as List == ["event_card"]
    }

    private static RenderableTemplate stubTemplate(String name, Set<String> params) {
        return new RenderableTemplate() {
            @Override String name() { name }
            @Override String description() { "stub for $name" }
            @Override Set<String> parameterNames() { params }
            @Override String render(Map<String, Object> p) { "rendered:$name" }
        }
    }
}

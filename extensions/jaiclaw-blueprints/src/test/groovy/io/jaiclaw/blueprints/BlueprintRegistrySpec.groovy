package io.jaiclaw.blueprints

import spock.lang.Specification

class BlueprintRegistrySpec extends Specification {

    BlueprintRegistry registry = new BlueprintRegistry()

    private static BlueprintDefinition bp(String id, String category) {
        return new BlueprintDefinition(id, id, "desc", category, [],
                "0 0 * * *", "Daily", "Do things", [], null)
    }

    def "register + all + byId happy path"() {
        given:
        registry.register([bp("a", "devops"), bp("b", "research")], "test")

        expect:
        registry.size() == 2
        registry.all()*.id() == ["a", "b"]
        registry.byId("a").isPresent()
        registry.byId("missing").isEmpty()
    }

    def "byCategory returns just the matching subset"() {
        given:
        registry.register([bp("a", "devops"), bp("b", "research"), bp("c", "devops")], "test")

        expect:
        registry.byCategory("devops")*.id() == ["a", "c"]
        registry.byCategory("research")*.id() == ["b"]
        registry.byCategory("none-such") == []
        registry.byCategory(null) == []
    }

    def "categories returns sorted set of categories present"() {
        given:
        registry.register([bp("a", "zeta"), bp("b", "alpha"), bp("c", "alpha")], "test")

        expect:
        registry.categories() as List == ["alpha", "zeta"]
    }

    def "duplicate id is skipped — first-writer-wins"() {
        given:
        registry.register([bp("a", "cat1")], "code")

        when:
        registry.register([bp("a", "cat2")], "yaml")

        then:
        registry.size() == 1
        registry.byId("a").get().category() == "cat1"
    }

    def "clear empties the registry"() {
        given:
        registry.register([bp("a", "c"), bp("b", "c")], "test")

        when:
        registry.clear()

        then:
        registry.size() == 0
    }

    def "register tolerates null defs"() {
        when:
        registry.register(null, "test")

        then:
        noExceptionThrown()
        registry.size() == 0
    }
}

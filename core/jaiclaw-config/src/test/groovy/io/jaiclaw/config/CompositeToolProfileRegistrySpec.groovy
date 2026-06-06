package io.jaiclaw.config

import io.jaiclaw.core.tool.CompositeToolProfile
import io.jaiclaw.core.tool.ToolProfile
import spock.lang.Specification

class CompositeToolProfileRegistrySpec extends Specification {

    CompositeToolProfileRegistry registry = new CompositeToolProfileRegistry()

    def "register and resolve a composite profile"() {
        given:
        def profile = CompositeToolProfile.builder("devops")
                .profiles(ToolProfile.CODING, ToolProfile.MESSAGING)
                .deny("shell_exec")
                .build()

        when:
        registry.register(profile)

        then:
        registry.resolve("devops").isPresent()
        registry.resolve("devops").get() == profile
    }

    def "resolve returns empty for unknown name"() {
        expect:
        registry.resolve("nonexistent").isEmpty()
    }

    def "enum name collision is rejected"() {
        given:
        def profile = CompositeToolProfile.builder("full")
                .profiles(ToolProfile.CODING)
                .build()

        when:
        registry.register(profile)

        then:
        thrown(IllegalArgumentException)
    }

    def "enum name collision is case-insensitive"() {
        given:
        def profile = CompositeToolProfile.builder("CODING")
                .profiles(ToolProfile.MINIMAL)
                .build()

        when:
        registry.register(profile)

        then:
        thrown(IllegalArgumentException)
    }

    def "all() returns a snapshot"() {
        given:
        def p1 = CompositeToolProfile.builder("devops")
                .profiles(ToolProfile.CODING, ToolProfile.MESSAGING)
                .build()
        def p2 = CompositeToolProfile.builder("helpdesk")
                .profiles(ToolProfile.MINIMAL)
                .build()

        when:
        registry.register(p1)
        registry.register(p2)
        Map<String, CompositeToolProfile> snapshot = registry.all()

        then:
        snapshot.size() == 2
        snapshot.containsKey("devops")
        snapshot.containsKey("helpdesk")
    }

    def "all() snapshot is not affected by later registrations"() {
        given:
        def p1 = CompositeToolProfile.builder("first")
                .profiles(ToolProfile.CODING)
                .build()
        registry.register(p1)
        Map<String, CompositeToolProfile> snapshot = registry.all()

        when:
        registry.register(CompositeToolProfile.builder("second")
                .profiles(ToolProfile.MINIMAL)
                .build())

        then:
        snapshot.size() == 1
        !snapshot.containsKey("second")
    }
}

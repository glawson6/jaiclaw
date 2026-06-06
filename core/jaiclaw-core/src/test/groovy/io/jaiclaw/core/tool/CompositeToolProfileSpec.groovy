package io.jaiclaw.core.tool

import spock.lang.Specification

class CompositeToolProfileSpec extends Specification {

    def "valid construction with all fields"() {
        when:
        def profile = new CompositeToolProfile(
                "devops",
                Set.of(ToolProfile.CODING, ToolProfile.MESSAGING),
                List.of("file_read"),
                List.of("shell_exec")
        )

        then:
        profile.name() == "devops"
        profile.profiles() == Set.of(ToolProfile.CODING, ToolProfile.MESSAGING)
        profile.allow() == ["file_read"]
        profile.deny() == ["shell_exec"]
    }

    def "blank name is rejected"() {
        when:
        new CompositeToolProfile("  ", Set.of(ToolProfile.CODING), List.of(), List.of())

        then:
        thrown(IllegalArgumentException)
    }

    def "null name is rejected"() {
        when:
        new CompositeToolProfile(null, Set.of(ToolProfile.CODING), List.of(), List.of())

        then:
        thrown(IllegalArgumentException)
    }

    def "empty profiles set is rejected"() {
        when:
        new CompositeToolProfile("empty", Set.of(), List.of(), List.of())

        then:
        thrown(IllegalArgumentException)
    }

    def "null profiles set is rejected"() {
        when:
        new CompositeToolProfile("nullp", null, List.of(), List.of())

        then:
        thrown(IllegalArgumentException)
    }

    def "null allow defaults to empty list"() {
        when:
        def profile = new CompositeToolProfile("test", Set.of(ToolProfile.MINIMAL), null, List.of())

        then:
        profile.allow().isEmpty()
    }

    def "null deny defaults to empty list"() {
        when:
        def profile = new CompositeToolProfile("test", Set.of(ToolProfile.MINIMAL), List.of(), null)

        then:
        profile.deny().isEmpty()
    }

    def "profiles set is immutable"() {
        given:
        def profile = new CompositeToolProfile(
                "immut", Set.of(ToolProfile.CODING), List.of(), List.of()
        )

        when:
        profile.profiles().add(ToolProfile.MESSAGING)

        then:
        thrown(UnsupportedOperationException)
    }

    def "allow list is immutable"() {
        given:
        def profile = new CompositeToolProfile(
                "immut", Set.of(ToolProfile.CODING), List.of("a"), List.of()
        )

        when:
        profile.allow().add("b")

        then:
        thrown(UnsupportedOperationException)
    }

    def "deny list is immutable"() {
        given:
        def profile = new CompositeToolProfile(
                "immut", Set.of(ToolProfile.CODING), List.of(), List.of("x")
        )

        when:
        profile.deny().add("y")

        then:
        thrown(UnsupportedOperationException)
    }

    def "builder produces equivalent record"() {
        when:
        def profile = CompositeToolProfile.builder("devops")
                .profiles(ToolProfile.CODING, ToolProfile.MESSAGING)
                .allow("file_read", "web_search")
                .deny("shell_exec")
                .build()

        then:
        profile.name() == "devops"
        profile.profiles().containsAll([ToolProfile.CODING, ToolProfile.MESSAGING])
        profile.allow() == ["file_read", "web_search"]
        profile.deny() == ["shell_exec"]
    }

    def "builder with collection overloads"() {
        when:
        def profile = CompositeToolProfile.builder("col")
                .profiles([ToolProfile.MINIMAL, ToolProfile.CODING])
                .allow(["a", "b"])
                .deny(["c"])
                .build()

        then:
        profile.profiles().size() == 2
        profile.allow() == ["a", "b"]
        profile.deny() == ["c"]
    }
}

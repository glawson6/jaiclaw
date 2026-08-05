package io.jaiclaw.cli.github.slashcmd

import spock.lang.Specification

class SlashCommandRegistrySpec extends Specification {

    def chatCmd = stub("chat", "chat about it")
    def helpCmd = stub("help", "list commands")
    def registry = new SlashCommandRegistry([chatCmd, helpCmd])

    def "indexes commands by name"() {
        expect:
        registry.resolve("chat").get().is(chatCmd)
        registry.resolve("help").get().is(helpCmd)
    }

    def "resolve is case-insensitive"() {
        expect:
        registry.resolve("CHAT").get().is(chatCmd)
        registry.resolve("Chat").get().is(chatCmd)
    }

    def "unknown command returns empty optional"() {
        expect:
        !registry.resolve("nope").isPresent()
        !registry.resolve(null).isPresent()
        !registry.resolve("").isPresent()
    }

    def "all() returns every registered command"() {
        expect:
        registry.all().size() == 2
    }

    def "throws on duplicate command name"() {
        given:
        def a = stub("dup", "one")
        def b = stub("dup", "two")

        when:
        new SlashCommandRegistry([a, b])

        then:
        thrown(IllegalStateException)
    }

    def "throws on blank command name"() {
        given:
        def bad = stub("", "empty")

        when:
        new SlashCommandRegistry([bad])

        then:
        thrown(IllegalStateException)
    }

    def "parse strips leading /command and returns the args"() {
        when:
        def parsed = registry.parse("/chat what does this do?")

        then:
        parsed.handler().is(chatCmd)
        parsed.args() == "what does this do?"
    }

    def "parse returns null when body doesn't start with a slash"() {
        expect:
        registry.parse("plain comment") == null
        registry.parse("hey /chat inside") == null
        registry.parse("") == null
        registry.parse(null) == null
    }

    def "parse returns null for unknown slash command"() {
        expect:
        registry.parse("/unknown foo") == null
    }

    def "parse handles command with no args"() {
        when:
        def parsed = registry.parse("/help")

        then:
        parsed.handler().is(helpCmd)
        parsed.args() == ""
    }

    def "parse handles leading whitespace"() {
        when:
        def parsed = registry.parse("   /chat foo bar")

        then:
        parsed.handler().is(chatCmd)
        parsed.args() == "foo bar"
    }

    private SlashCommand stub(String name, String description) {
        return new SlashCommand() {
            String name() { name }
            String description() { description }
            CommandResult handle(SlashContext c) { CommandResult.ok("stub") }
        }
    }
}

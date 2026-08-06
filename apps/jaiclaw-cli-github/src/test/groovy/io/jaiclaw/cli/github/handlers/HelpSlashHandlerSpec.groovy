package io.jaiclaw.cli.github.handlers

import io.jaiclaw.cli.github.slashcmd.CommandResult
import io.jaiclaw.cli.github.slashcmd.SlashCommand
import io.jaiclaw.cli.github.slashcmd.SlashCommandRegistry
import io.jaiclaw.cli.github.slashcmd.SlashContext
import org.springframework.beans.factory.ObjectProvider
import spock.lang.Specification

class HelpSlashHandlerSpec extends Specification {

    def "help renders every registered command sorted by name"() {
        given:
        def chatCmd = stub("chat", "chat with the bot")
        def faqCmd = stub("faq", "answer an FAQ")
        def helpCmd = stub("help", "list commands")
        def registry = new SlashCommandRegistry([chatCmd, faqCmd, helpCmd])
        def provider = Mock(ObjectProvider) { getObject() >> registry }
        def handler = new HelpSlashHandler(provider)
        def context = new SlashContext("owner/name", 1, null, "/help", "", -1L)

        when:
        def result = handler.handle(context)

        then:
        result instanceof CommandResult
        result.ok()
        def out = result.reply()
        out.contains("/chat")
        out.contains("/faq")
        out.contains("/help")
        // sorted alphabetically
        out.indexOf("/chat") < out.indexOf("/faq")
        out.indexOf("/faq") < out.indexOf("/help")
    }

    def "handler name and description"() {
        given:
        def handler = new HelpSlashHandler(Mock(ObjectProvider))

        expect:
        handler.name() == "help"
        !handler.description().isBlank()
    }

    private SlashCommand stub(String name, String description) {
        return new SlashCommand() {
            String name() { name }
            String description() { description }
            CommandResult handle(SlashContext c) { CommandResult.ok("stub") }
        }
    }
}

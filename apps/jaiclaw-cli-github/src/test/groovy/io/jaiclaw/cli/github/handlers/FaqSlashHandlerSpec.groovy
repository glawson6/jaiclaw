package io.jaiclaw.cli.github.handlers

import io.jaiclaw.cli.github.AgentInvoker
import io.jaiclaw.cli.github.SystemPromptLoader
import io.jaiclaw.cli.github.slashcmd.CommandResult
import io.jaiclaw.cli.github.slashcmd.SlashContext
import io.jaiclaw.docs.DocsEntry
import io.jaiclaw.docs.DocsRepository
import io.jaiclaw.docs.DocsSearchResult
import org.springframework.beans.factory.ObjectProvider
import spock.lang.Specification

class FaqSlashHandlerSpec extends Specification {

    def agentInvoker = Mock(AgentInvoker)
    def promptLoader = Mock(SystemPromptLoader)
    def docsRepo = Mock(DocsRepository)
    def docsProvider = Mock(ObjectProvider) { getIfAvailable() >> docsRepo }
    def handler = new FaqSlashHandler(agentInvoker, promptLoader, docsProvider)

    def setup() {
        promptLoader.load() >> "PROMPT"
    }

    def "no-args mode summarises top docs"() {
        given:
        def entry = new DocsEntry("docs://a", "Architecture", "text/markdown", "Some architecture content", [])
        docsRepo.findAll() >> [entry]
        def context = new SlashContext("owner/name", 1, null, "/faq", "", -1L)

        when:
        def result = handler.handle(context)

        then:
        1 * agentInvoker.invoke("github-faq:owner/name#1", { String msg ->
            msg.contains("FAQ digest") && msg.contains("Architecture") && msg.contains("Some architecture content")
        }, "PROMPT") >> "5-item FAQ"
        result.ok()
        result.reply() == "5-item FAQ"
    }

    def "topic mode searches docs and includes hits"() {
        given:
        def hit = new DocsSearchResult("docs://gateway", "Gateway", "gateway snippet", 0.9d)
        docsRepo.search("gateway", 5) >> [hit]
        def context = new SlashContext("owner/name", 1, null, "/faq gateway", "gateway", -1L)

        when:
        def result = handler.handle(context)

        then:
        1 * agentInvoker.invoke("github-faq:owner/name#1", { String msg ->
            msg.contains("gateway") && msg.contains("Gateway") && msg.contains("gateway snippet")
        }, "PROMPT") >> "Gateway answer"
        result.ok()
        result.reply() == "Gateway answer"
    }

    def "no matches returns friendly message"() {
        given:
        docsRepo.search("nothing", 5) >> []
        def context = new SlashContext("owner/name", 1, null, "/faq nothing", "nothing", -1L)

        when:
        def result = handler.handle(context)

        then:
        result.ok()
        result.reply().contains("No documentation matches")
        0 * agentInvoker.invoke(_, _, _)
    }

    def "returns error when jaiclaw-docs is not on the classpath"() {
        given:
        def emptyProvider = Mock(ObjectProvider) { getIfAvailable() >> null }
        def h = new FaqSlashHandler(agentInvoker, promptLoader, emptyProvider)
        def context = new SlashContext("owner/name", 1, null, "/faq", "", -1L)

        when:
        def result = h.handle(context)

        then:
        !result.ok()
        result.reply().contains("jaiclaw-docs")
    }

    def "handler name and description"() {
        expect:
        handler.name() == "faq"
        !handler.description().isBlank()
    }
}

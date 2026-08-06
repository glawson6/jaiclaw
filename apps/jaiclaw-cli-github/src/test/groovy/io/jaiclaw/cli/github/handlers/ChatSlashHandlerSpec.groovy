package io.jaiclaw.cli.github.handlers

import io.jaiclaw.cli.github.AgentInvoker
import io.jaiclaw.cli.github.CliGithubProperties
import io.jaiclaw.cli.github.SystemPromptLoader
import io.jaiclaw.cli.github.slashcmd.CommandResult
import io.jaiclaw.cli.github.slashcmd.SlashContext
import io.jaiclaw.tools.github.client.GithubClientProvider
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueComment
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GHUser
import org.kohsuke.github.GitHub
import spock.lang.Specification

class ChatSlashHandlerSpec extends Specification {

    def agentInvoker = Mock(AgentInvoker)
    def promptLoader = Mock(SystemPromptLoader)
    def properties = CliGithubProperties.defaults()
    def clientProvider = Mock(GithubClientProvider)
    def client = Mock(GitHub)
    def repo = Mock(GHRepository)
    def issue = Mock(GHIssue)
    def user = Mock(GHUser)
    def handler = new ChatSlashHandler(agentInvoker, promptLoader, properties, clientProvider)

    def setup() {
        clientProvider.getClient() >> client
        client.getRepository("owner/name") >> repo
        promptLoader.load() >> "SYSTEM PROMPT"
    }

    def "returns error on blank args"() {
        given:
        def context = new SlashContext("owner/name", 1, null, "/chat", "", -1L)

        when:
        def result = handler.handle(context)

        then:
        result instanceof CommandResult
        !result.ok()
        result.reply().contains("Usage")
    }

    def "invokes the agent with question when no prior comments"() {
        given:
        repo.getIssue(1) >> issue
        issue.getComments() >> []
        def context = new SlashContext("owner/name", 1, null, "/chat what is this?", "what is this?", -1L)

        when:
        def result = handler.handle(context)

        then:
        1 * agentInvoker.invoke("github:owner/name#1", "what is this?", "SYSTEM PROMPT") >> "Answer text"
        result.ok()
        result.reply() == "Answer text"
    }

    def "prepends thread history to the question when comments exist"() {
        given:
        def priorComment = Mock(GHIssueComment)
        priorComment.getBody() >> "earlier message"
        priorComment.getUser() >> user
        user.getLogin() >> "alice"
        repo.getIssue(2) >> issue
        issue.getComments() >> [priorComment]
        def context = new SlashContext("owner/name", 2, null, "/chat next question", "next question", -1L)

        when:
        def result = handler.handle(context)

        then:
        1 * agentInvoker.invoke("github:owner/name#2", { String msg ->
            msg.contains("Prior thread comments") &&
                    msg.contains("earlier message") &&
                    msg.contains("Current question") &&
                    msg.contains("next question")
        }, "SYSTEM PROMPT") >> "Contextual answer"
        result.ok()
        result.reply() == "Contextual answer"
    }

    def "handler name and description"() {
        expect:
        handler.name() == "chat"
        !handler.description().isBlank()
    }
}

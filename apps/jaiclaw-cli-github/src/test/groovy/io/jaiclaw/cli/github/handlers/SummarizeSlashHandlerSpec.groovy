package io.jaiclaw.cli.github.handlers

import io.jaiclaw.cli.github.AgentInvoker
import io.jaiclaw.cli.github.SystemPromptLoader
import io.jaiclaw.cli.github.slashcmd.CommandResult
import io.jaiclaw.cli.github.slashcmd.SlashContext
import io.jaiclaw.tools.github.client.GithubClientProvider
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHPullRequestFileDetail
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import org.kohsuke.github.PagedIterable
import spock.lang.Specification

class SummarizeSlashHandlerSpec extends Specification {

    def agentInvoker = Mock(AgentInvoker)
    def promptLoader = Mock(SystemPromptLoader)
    def clientProvider = Mock(GithubClientProvider)
    def client = Mock(GitHub)
    def repo = Mock(GHRepository)
    def pr = Mock(GHPullRequest)
    def handler = new SummarizeSlashHandler(agentInvoker, promptLoader, clientProvider)

    def setup() {
        clientProvider.getClient() >> client
        client.getRepository("owner/name") >> repo
        promptLoader.load() >> "PROMPT"
    }

    def "returns error when issue is 0"() {
        given:
        def context = new SlashContext("owner/name", 0, "abcdef", "/summarize", "", -1L)

        when:
        def result = handler.handle(context)

        then:
        !result.ok()
        result.reply().contains("pull-request")
    }

    def "returns error when the issue is not a PR"() {
        given:
        repo.getPullRequest(5) >> { throw new RuntimeException("not a PR") }
        def context = new SlashContext("owner/name", 5, null, "/summarize", "", -1L)

        when:
        def result = handler.handle(context)

        then:
        !result.ok()
        result.reply().contains("only works on pull requests")
    }

    def "invokes the agent with title, body, and diff"() {
        given:
        def file = Mock(GHPullRequestFileDetail)
        def paged = Mock(PagedIterable); paged.toList() >> [file]
        repo.getPullRequest(3) >> pr
        pr.getTitle() >> "Add new feature"
        pr.getBody() >> "This adds X"
        pr.listFiles() >> paged
        file.getFilename() >> "src/A.java"
        file.getPatch() >> "@@ -1 +1 @@\n-old\n+new"
        def context = new SlashContext("owner/name", 3, null, "/summarize", "", -1L)

        when:
        def result = handler.handle(context)

        then:
        1 * agentInvoker.invoke("github-summarize:owner/name#3", { String msg ->
            msg.contains("Add new feature") &&
                    msg.contains("This adds X") &&
                    msg.contains("src/A.java") &&
                    msg.contains("+new")
        }, "PROMPT") >> "PR summary body"
        result.ok()
        result.reply() == "PR summary body"
    }

    def "handler name and description"() {
        expect:
        handler.name() == "summarize"
        !handler.description().isBlank()
    }
}

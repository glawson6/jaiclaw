package io.jaiclaw.tools.github.tools

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.tools.github.client.GithubClientProvider
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueComment
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import spock.lang.Specification

class GithubCommentToolSpec extends Specification {

    def clientProvider = Mock(GithubClientProvider)
    def client = Mock(GitHub)
    def repo = Mock(GHRepository)
    def issue = Mock(GHIssue)
    def comment = Mock(GHIssueComment)
    // Spy the tool so we can stub the extracted postComment(issue, body) helper —
    // GHIssue.comment(String) has two public overloads (void + GHIssueComment) with
    // identical erased signatures; Spock's proxy dispatches between them non-
    // deterministically. Stubbing postComment sidesteps the ambiguous surface.
    def tool = Spy(GithubCommentTool, constructorArgs: [clientProvider])
    def context = new ToolContext("agent1", "sess1", "sid1", "/tmp")

    def setup() {
        clientProvider.getClient() >> client
        client.getRepository("owner/name") >> repo
    }

    def "tool name is github_comment and section is GitHub"() {
        expect:
        tool.definition().name() == "github_comment"
        tool.definition().section() == "GitHub"
    }

    def "input schema requires repo, issue, body"() {
        expect:
        tool.definition().inputSchema().contains('"repo"')
        tool.definition().inputSchema().contains('"issue"')
        tool.definition().inputSchema().contains('"body"')
        tool.definition().inputSchema().contains('"required": ["repo", "issue", "body"]')
    }

    def "posts the comment and returns a confirmation message"() {
        given:
        repo.getIssue(42) >> issue
        tool.postComment(issue, _ as String) >> comment
        comment.getHtmlUrl() >> new URL("https://github.com/owner/name/issues/42#issuecomment-999")

        when:
        def result = tool.execute([repo: "owner/name", issue: 42, body: "hello world"], context)

        then:
        result instanceof ToolResult.Success
        def out = (result as ToolResult.Success).content()
        out.contains("Posted comment")
        out.contains("owner/name #42")
        out.contains("issuecomment-999")
    }

    def "surfaces missing parameters as an Error result"() {
        when:
        def result = tool.execute([repo: "owner/name"], context)

        then:
        result instanceof ToolResult.Error
    }

    def "accepts issue param as a String number"() {
        given:
        repo.getIssue(7) >> issue
        tool.postComment(issue, _ as String) >> comment
        comment.getHtmlUrl() >> new URL("https://github.com/owner/name/issues/7#issuecomment-1")

        when:
        def result = tool.execute([repo: "owner/name", issue: "7", body: "x"], context)

        then:
        result instanceof ToolResult.Success
    }
}

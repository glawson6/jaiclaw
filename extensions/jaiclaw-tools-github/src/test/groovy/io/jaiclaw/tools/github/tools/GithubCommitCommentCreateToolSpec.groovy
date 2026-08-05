package io.jaiclaw.tools.github.tools

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.tools.github.client.GithubClientProvider
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHCommitComment
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import spock.lang.Specification

class GithubCommitCommentCreateToolSpec extends Specification {

    def clientProvider = Mock(GithubClientProvider)
    def client = Mock(GitHub)
    def repo = Mock(GHRepository)
    def commit = Mock(GHCommit)
    def comment = Mock(GHCommitComment)
    def tool = new GithubCommitCommentCreateTool(clientProvider)
    def context = new ToolContext("agent1", "sess1", "sid1", "/tmp")

    def setup() {
        clientProvider.getClient() >> client
        client.getRepository("owner/name") >> repo
        repo.getCommit("deadbeef") >> commit
    }

    def "posts a plain body when no path is given"() {
        given:
        commit.createComment("looks good") >> comment
        comment.getHtmlUrl() >> new URL("https://github.com/owner/name/commit/deadbeef#commitcomment-100")

        when:
        def result = tool.execute([repo: "owner/name", sha: "deadbeef", body: "looks good"], context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("Posted commit comment")
        (result as ToolResult.Success).content().contains("deadbeef")
    }

    def "posts an inline body when path + line are given"() {
        given:
        commit.createComment("nit here", "src/A.java", 42, null) >> comment
        comment.getHtmlUrl() >> new URL("https://github.com/owner/name/commit/deadbeef#commitcomment-101")

        when:
        def result = tool.execute([
                repo: "owner/name", sha: "deadbeef",
                body: "nit here", path: "src/A.java", line: 42
        ], context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("Posted commit comment")
    }

    def "tool name and schema"() {
        expect:
        tool.definition().name() == "github_commit_comment_create"
        tool.definition().inputSchema().contains('"required": ["repo", "sha", "body"]')
    }
}

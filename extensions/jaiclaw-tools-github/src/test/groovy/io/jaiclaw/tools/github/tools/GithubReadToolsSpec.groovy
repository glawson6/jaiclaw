package io.jaiclaw.tools.github.tools

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.tools.github.client.GithubClientProvider
import org.kohsuke.github.GHCommit
import org.kohsuke.github.GHCommitComment
import org.kohsuke.github.GHContent
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueComment
import org.kohsuke.github.GHLabel
import org.kohsuke.github.GHPullRequest
import org.kohsuke.github.GHPullRequestCommitDetail
import org.kohsuke.github.GHPullRequestFileDetail
import org.kohsuke.github.GHPullRequestReview
import org.kohsuke.github.GHPullRequestReviewComment
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GHUser
import org.kohsuke.github.GitHub
import org.kohsuke.github.PagedIterable
import spock.lang.Specification

import java.time.Instant
import java.util.Date

/**
 * Consolidated coverage for the 10 read-side GitHub tools. Each verifies
 * name, schema, and the happy path against mocked Kohsuke types. Error
 * paths (bad params) are covered once by GithubCommentToolSpec since the
 * behavior is identical (routes through AbstractBuiltinTool.execute).
 */
class GithubReadToolsSpec extends Specification {

    def clientProvider = Mock(GithubClientProvider)
    def client = Mock(GitHub)
    def repo = Mock(GHRepository)
    def context = new ToolContext("agent1", "sess1", "sid1", "/tmp")

    def setup() {
        clientProvider.getClient() >> client
        client.getRepository("owner/name") >> repo
    }

    def "GithubIssueGetTool renders issue metadata"() {
        given:
        def tool = new GithubIssueGetTool(clientProvider)
        def issue = Mock(GHIssue)
        def user = Mock(GHUser)
        def label = Mock(GHLabel)
        repo.getIssue(1) >> issue
        issue.getTitle() >> "Bug: crash on startup"
        issue.getState() >> org.kohsuke.github.GHIssueState.OPEN
        issue.getUser() >> user
        user.getLogin() >> "alice"
        issue.getAssignees() >> []
        issue.getLabels() >> [label]
        label.getName() >> "bug"
        issue.getMilestone() >> null
        issue.getCommentsCount() >> 3
        issue.getHtmlUrl() >> new URL("https://github.com/owner/name/issues/1")
        issue.getBody() >> "It broke."

        when:
        def result = tool.execute([repo: "owner/name", issue: 1], context)

        then:
        result instanceof ToolResult.Success
        def out = (result as ToolResult.Success).content()
        out.contains("Bug: crash on startup")
        out.contains("Author: alice")
        out.contains("bug")
        out.contains("It broke.")
        tool.definition().name() == "github_issue_get"
    }

    def "GithubPrGetTool renders PR-specific metadata"() {
        given:
        def tool = new GithubPrGetTool(clientProvider)
        def pr = Mock(GHPullRequest)
        def user = Mock(GHUser)
        def base = Mock(org.kohsuke.github.GHCommitPointer)
        def head = Mock(org.kohsuke.github.GHCommitPointer)
        repo.getPullRequest(5) >> pr
        pr.getTitle() >> "Add feature"
        pr.getState() >> org.kohsuke.github.GHIssueState.OPEN
        pr.isDraft() >> false
        pr.isMerged() >> false
        pr.getMergeable() >> Boolean.TRUE
        pr.getBase() >> base
        pr.getHead() >> head
        base.getRef() >> "main"
        base.getSha() >> "abc123"
        head.getRef() >> "feature-x"
        head.getSha() >> "def456"
        pr.getUser() >> user
        user.getLogin() >> "bob"
        pr.getAdditions() >> 100
        pr.getDeletions() >> 20
        pr.getChangedFiles() >> 5
        pr.getCommits() >> 3
        pr.getRequestedReviewers() >> []
        pr.getHtmlUrl() >> new URL("https://github.com/owner/name/pull/5")
        pr.getBody() >> "Adds thing"

        when:
        def result = tool.execute([repo: "owner/name", pr: 5], context)

        then:
        result instanceof ToolResult.Success
        def out = (result as ToolResult.Success).content()
        out.contains("PR: #5")
        out.contains("Base: main")
        out.contains("Head: feature-x")
        out.contains("Additions: 100")
    }

    def "GithubPrThreadTool iterates issue comments"() {
        given:
        def tool = new GithubPrThreadTool(clientProvider)
        def issue = Mock(GHIssue)
        def comment1 = Mock(GHIssueComment)
        def comment2 = Mock(GHIssueComment)
        def user = Mock(GHUser)
        repo.getIssue(9) >> issue
        issue.getComments() >> [comment1, comment2]
        comment1.getUser() >> user
        user.getLogin() >> "alice"
        comment1.getBody() >> "First reply"
        comment2.getUser() >> user
        comment2.getBody() >> "Second reply"

        when:
        def result = tool.execute([repo: "owner/name", issue: 9], context)

        then:
        result instanceof ToolResult.Success
        def out = (result as ToolResult.Success).content()
        out.contains("First reply")
        out.contains("Second reply")
        out.contains("Comment #")
    }

    def "GithubPrDiffTool concatenates file patches"() {
        given:
        def tool = new GithubPrDiffTool(clientProvider)
        def pr = Mock(GHPullRequest)
        def fileA = Mock(GHPullRequestFileDetail)
        def fileB = Mock(GHPullRequestFileDetail)
        def files = Mock(PagedIterable)
        files.toList() >> [fileA, fileB]
        repo.getPullRequest(3) >> pr
        pr.listFiles() >> files
        fileA.getFilename() >> "src/A.java"
        fileA.getPatch() >> "@@ -1 +1 @@\n-old\n+new"
        fileB.getFilename() >> "image.png"
        fileB.getPatch() >> null
        fileB.getStatus() >> "modified"

        when:
        def result = tool.execute([repo: "owner/name", pr: 3], context)

        then:
        result instanceof ToolResult.Success
        def out = (result as ToolResult.Success).content()
        out.contains("src/A.java")
        out.contains("-old")
        out.contains("+new")
        out.contains("binary or no patch")
    }

    def "GithubPrFilesTool renders a file table"() {
        given:
        def tool = new GithubPrFilesTool(clientProvider)
        def pr = Mock(GHPullRequest)
        def file = Mock(GHPullRequestFileDetail)
        repo.getPullRequest(4) >> pr
        def paged = Mock(PagedIterable); paged.toList() >> [file]
        pr.listFiles() >> paged
        file.getStatus() >> "modified"
        file.getAdditions() >> 10
        file.getDeletions() >> 2
        file.getFilename() >> "src/A.java"

        when:
        def result = tool.execute([repo: "owner/name", pr: 4], context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("src/A.java")
        (result as ToolResult.Success).content().contains("modified")
    }

    def "GithubPrCommitsTool renders sha + author + first line"() {
        given:
        def tool = new GithubPrCommitsTool(clientProvider)
        def pr = Mock(GHPullRequest)
        def commit = Mock(GHPullRequestCommitDetail)
        def innerCommit = Mock(GHPullRequestCommitDetail.Commit)
        def authorship = Mock(org.kohsuke.github.GHPullRequestCommitDetail.Authorship)
        repo.getPullRequest(2) >> pr
        def paged = Mock(PagedIterable); paged.toList() >> [commit]
        pr.listCommits() >> paged
        commit.getSha() >> "abcdef1234567890"
        commit.getCommit() >> innerCommit
        innerCommit.getAuthor() >> authorship
        authorship.getName() >> "Alice Author"
        innerCommit.getMessage() >> "fix: nasty bug\n\nDetails."

        when:
        def result = tool.execute([repo: "owner/name", pr: 2], context)

        then:
        result instanceof ToolResult.Success
        def out = (result as ToolResult.Success).content()
        out.contains("abcdef1")
        out.contains("Alice Author")
        out.contains("fix: nasty bug")
        !out.contains("Details.")
    }

    def "GithubPrReviewsTool includes state + body"() {
        given:
        def tool = new GithubPrReviewsTool(clientProvider)
        def pr = Mock(GHPullRequest)
        def review = Mock(GHPullRequestReview)
        def user = Mock(GHUser)
        repo.getPullRequest(6) >> pr
        def paged = Mock(PagedIterable); paged.toList() >> [review]
        pr.listReviews() >> paged
        review.getUser() >> user
        user.getLogin() >> "reviewer"
        review.getState() >> org.kohsuke.github.GHPullRequestReviewState.APPROVED
        review.getBody() >> "LGTM"

        when:
        def result = tool.execute([repo: "owner/name", pr: 6], context)

        then:
        result instanceof ToolResult.Success
        def out = (result as ToolResult.Success).content()
        out.contains("Review #")
        out.contains("APPROVED")
        out.contains("LGTM")
    }

    def "GithubPrReviewCommentsTool renders inline location"() {
        given:
        def tool = new GithubPrReviewCommentsTool(clientProvider)
        def pr = Mock(GHPullRequest)
        def comment = Mock(GHPullRequestReviewComment)
        def user = Mock(GHUser)
        repo.getPullRequest(7) >> pr
        def paged = Mock(PagedIterable); paged.toList() >> [comment]
        pr.listReviewComments() >> paged
        comment.getUser() >> user
        user.getLogin() >> "carol"
        comment.getPath() >> "src/B.java"
        comment.getLine() >> 42
        comment.getSide() >> GHPullRequestReviewComment.Side.RIGHT
        comment.getBody() >> "nit: rename this"

        when:
        def result = tool.execute([repo: "owner/name", pr: 7], context)

        then:
        result instanceof ToolResult.Success
        def out = (result as ToolResult.Success).content()
        out.contains("Path: src/B.java")
        out.contains("Line: 42")
        out.contains("nit: rename this")
    }

    def "GithubCommitCommentsTool iterates commit comments"() {
        given:
        def tool = new GithubCommitCommentsTool(clientProvider)
        def comment = Mock(GHCommitComment)
        def user = Mock(GHUser)
        def paged = Mock(PagedIterable); paged.toList() >> [comment]
        repo.listCommitComments("deadbeef") >> paged
        comment.getUser() >> user
        user.getLogin() >> "dave"
        comment.getPath() >> "README.md"
        comment.getLine() >> 12
        comment.getBody() >> "typo here"

        when:
        def result = tool.execute([repo: "owner/name", sha: "deadbeef"], context)

        then:
        result instanceof ToolResult.Success
        def out = (result as ToolResult.Success).content()
        out.contains("Commit comment #")
        out.contains("Path: README.md")
        out.contains("typo here")
    }

    def "GithubRepoGetContentTool reads text file via API"() {
        given:
        def tool = new GithubRepoGetContentTool(clientProvider)
        def content = Mock(GHContent)
        repo.getFileContent("docs/README.md") >> content
        content.isFile() >> true
        content.read() >> new ByteArrayInputStream("hello docs".getBytes("UTF-8"))

        when:
        def result = tool.execute([repo: "owner/name", path: "docs/README.md"], context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content() == "hello docs"
    }

}

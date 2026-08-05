package io.jaiclaw.cli.github.commands

import io.jaiclaw.cli.github.slashcmd.CommandResult
import io.jaiclaw.cli.github.slashcmd.SlashCommand
import io.jaiclaw.cli.github.slashcmd.SlashCommandRegistry
import io.jaiclaw.cli.github.slashcmd.SlashContext
import io.jaiclaw.tools.github.client.GithubClientProvider
import org.kohsuke.github.GHIssue
import org.kohsuke.github.GHIssueComment
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import spock.lang.Specification

class HandleCommentCommandSpec extends Specification {

    def registry
    def clientProvider = Mock(GithubClientProvider)
    def client = Mock(GitHub)
    def repo = Mock(GHRepository)
    def issue = Mock(GHIssue)
    def posted = Mock(GHIssueComment)
    def cmd
    String postedBody   // captured by the issue.comment stub below

    def setup() {
        clientProvider.getClient() >> client
        client.getRepository("owner/name") >> repo
        repo.getIssue(_ as Integer) >> issue
        // Capture-and-return. Every test that invokes `handle-comment` on
        // a repo/issue will end up here; assertions can inspect postedBody
        // to verify what got posted (or leave it null to verify no post).
        issue.comment(_ as String) >> { String body -> postedBody = body; return posted }
    }

    def "dispatches to matching slash command and posts reply"() {
        given:
        def handler = capturingHandler("chat", { ctx -> CommandResult.ok("assistant reply") })
        registry = new SlashCommandRegistry([handler])
        cmd = new HandleCommentCommand(registry, clientProvider)

        when:
        String result = cmd.handleComment("owner/name", 42, "/chat hi bot", "", -1L)

        then:
        handler.lastArgs == "hi bot"
        result.contains("Posted reply")
    }

    def "returns friendly message when body has no slash command"() {
        given:
        registry = new SlashCommandRegistry([])
        cmd = new HandleCommentCommand(registry, clientProvider)

        when:
        String result = cmd.handleComment("owner/name", 1, "plain comment", "", -1L)

        then:
        result.contains("nothing to do")
    }

    def "returns friendly message when slash command is unknown"() {
        given:
        registry = new SlashCommandRegistry([capturingHandler("chat", { CommandResult.ok("x") })])
        cmd = new HandleCommentCommand(registry, clientProvider)

        when:
        String result = cmd.handleComment("owner/name", 1, "/unknown x", "", -1L)

        then:
        result.contains("nothing to do")
    }

    def "wraps error reply with warning banner"() {
        given:
        def handler = capturingHandler("faq", { CommandResult.error("no docs found") })
        registry = new SlashCommandRegistry([handler])
        cmd = new HandleCommentCommand(registry, clientProvider)

        when:
        cmd.handleComment("owner/name", 7, "/faq x", "", -1L)

        then:
        postedBody != null
        postedBody.contains("Error running")
        postedBody.contains("no docs found")
    }

    def "handles exception in the slash-command handler"() {
        given:
        def handler = capturingHandler("chat", { throw new RuntimeException("boom") })
        registry = new SlashCommandRegistry([handler])
        cmd = new HandleCommentCommand(registry, clientProvider)

        when:
        String result = cmd.handleComment("owner/name", 1, "/chat x", "", -1L)

        then:
        postedBody != null
        postedBody.contains("Error running")
        postedBody.contains("boom")
        result.contains("Posted reply")
    }

    def "skips posting when handler returns empty reply"() {
        given:
        def handler = capturingHandler("chat", { CommandResult.noReply() })
        registry = new SlashCommandRegistry([handler])
        cmd = new HandleCommentCommand(registry, clientProvider)

        when:
        String result = cmd.handleComment("owner/name", 1, "/chat x", "", -1L)

        then:
        postedBody == null
        result.contains("no reply")
    }

    /** A capturing handler that records the args it was invoked with. */
    private CapturingHandler capturingHandler(String name, Closure impl) {
        return new CapturingHandler(name: name, impl: impl)
    }

    static class CapturingHandler implements SlashCommand {
        String name
        String description = "test handler"
        String lastArgs
        Closure impl

        String name() { name }
        String description() { description }
        CommandResult handle(SlashContext c) {
            lastArgs = c.args()
            return impl.call(c) as CommandResult
        }
    }
}

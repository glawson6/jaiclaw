package io.jaiclaw.cli.github.commands;

import io.jaiclaw.cli.github.slashcmd.CommandResult;
import io.jaiclaw.cli.github.slashcmd.SlashCommand;
import io.jaiclaw.cli.github.slashcmd.SlashCommandRegistry;
import io.jaiclaw.cli.github.slashcmd.SlashContext;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

/**
 * Entry-point shell command invoked by the GitHub Actions workflow on
 * every {@code /}-prefixed comment.
 *
 * <p>Args:
 * <ul>
 *   <li>{@code --repo owner/name} — required</li>
 *   <li>{@code --issue N} — the issue or PR number; {@code 0} for
 *       {@code commit_comment} events with no associated issue</li>
 *   <li>{@code --body "/chat ..."} — the raw comment body</li>
 *   <li>{@code --sha SHA} — optional commit SHA (from
 *       {@code commit_comment} events); empty when triggered by
 *       {@code issue_comment} or {@code pull_request_review_comment}</li>
 *   <li>{@code --comment-id ID} — optional numeric ID of the triggering
 *       comment; used by {@code /chat} to skip its own trigger comment
 *       when building thread history</li>
 * </ul>
 *
 * <p>Behaviour: parse the body, resolve the slash command, dispatch,
 * post the reply back to the same thread. Silently succeeds on comments
 * that don't start with a slash (workflow's {@code if:} guard should
 * have prevented us from getting here, but be safe).
 */
@Component
public class HandleCommentCommand {

    private static final Logger log = LoggerFactory.getLogger(HandleCommentCommand.class);

    private final SlashCommandRegistry registry;
    private final GithubClientProvider githubClientProvider;

    public HandleCommentCommand(SlashCommandRegistry registry,
                                GithubClientProvider githubClientProvider) {
        this.registry = registry;
        this.githubClientProvider = githubClientProvider;
    }

    @Command(name = "handle-comment",
            description = "Parse and dispatch a GitHub PR/issue/commit comment as a slash command.")
    public String handleComment(
            @Option(longName ="repo", required = true,
                    description = "Repository in owner/name format") String repo,
            @Option(longName ="issue", defaultValue = "0",
                    description = "Issue or PR number (0 for commit-only events)") int issue,
            @Option(longName ="body", required = true,
                    description = "Raw comment body") String body,
            @Option(longName ="sha", defaultValue = "",
                    description = "Commit SHA (populated for commit_comment events)") String sha,
            @Option(longName ="comment-id", defaultValue = "-1",
                    description = "Numeric ID of the triggering comment") long commentId
    ) {
        log.info("handle-comment: repo={} issue={} sha={} commentId={} bodyLen={}",
                repo, issue, sha, commentId, body == null ? 0 : body.length());

        SlashCommandRegistry.Parsed parsed = registry.parse(body);
        if (parsed == null) {
            String msg = "Body does not begin with a recognised slash command — nothing to do.";
            log.info(msg);
            return msg;
        }

        SlashContext context = new SlashContext(
                repo, issue,
                (sha == null || sha.isBlank()) ? null : sha,
                body,
                parsed.args(),
                commentId);
        SlashCommand handler = parsed.handler();
        log.info("Dispatching /{} to {}", handler.name(), handler.getClass().getSimpleName());

        CommandResult result;
        try {
            result = handler.handle(context);
        } catch (Exception e) {
            log.error("Slash command /{} failed: {}", handler.name(), e.getMessage(), e);
            result = CommandResult.error(
                    "Command `/" + handler.name() + "` failed: " + e.getMessage());
        }

        return postReply(repo, issue, result, handler.name());
    }

    private String postReply(String repo, int issue, CommandResult result, String cmdName) {
        String bodyToPost;
        if (result.reply() == null || result.reply().isBlank()) {
            return "Command `/" + cmdName + "` completed with no reply body.";
        }
        if (result.ok()) {
            bodyToPost = result.reply();
        } else {
            bodyToPost = "> ⚠ Error running `/" + cmdName + "`\n\n" + result.reply();
        }

        if (issue <= 0) {
            log.warn("No issue/PR number — cannot post reply to {}. Result:\n{}", repo, bodyToPost);
            return bodyToPost;
        }
        try {
            githubClientProvider.getClient().getRepository(repo).getIssue(issue).comment(bodyToPost);
            return "Posted reply to " + repo + " #" + issue + " (" + bodyToPost.length() + " chars)";
        } catch (Exception e) {
            log.error("Failed to post reply to {} #{}: {}", repo, issue, e.getMessage(), e);
            return "Command succeeded but failed to post reply: " + e.getMessage()
                    + "\n\nReply body was:\n" + bodyToPost;
        }
    }
}

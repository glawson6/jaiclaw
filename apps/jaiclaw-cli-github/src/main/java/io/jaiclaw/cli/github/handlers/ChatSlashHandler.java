package io.jaiclaw.cli.github.handlers;

import io.jaiclaw.cli.github.AgentInvoker;
import io.jaiclaw.cli.github.CliGithubProperties;
import io.jaiclaw.cli.github.SystemPromptLoader;
import io.jaiclaw.cli.github.slashcmd.CommandResult;
import io.jaiclaw.cli.github.slashcmd.SlashCommand;
import io.jaiclaw.cli.github.slashcmd.SlashContext;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code /chat <text>} — LLM Q&A with prior thread context.
 *
 * <p>The PR/issue thread IS the session: on every invocation, this
 * handler fetches all prior issue-level comments via the GitHub API and
 * injects them (bounded by {@link CliGithubProperties#maxThreadHistory()})
 * as a lightweight transcript ahead of the current user message. That
 * gives multi-turn continuity without any JaiClaw-side session store.
 * When the process restarts, nothing is lost.
 *
 * <p>The dispatcher posts the assistant reply back to the same thread —
 * this handler just returns the reply body.
 */
@Component
public class ChatSlashHandler implements SlashCommand {

    private static final Logger log = LoggerFactory.getLogger(ChatSlashHandler.class);

    private final AgentInvoker agentInvoker;
    private final SystemPromptLoader promptLoader;
    private final CliGithubProperties properties;
    private final GithubClientProvider githubClientProvider;

    public ChatSlashHandler(AgentInvoker agentInvoker,
                            SystemPromptLoader promptLoader,
                            CliGithubProperties properties,
                            GithubClientProvider githubClientProvider) {
        this.agentInvoker = agentInvoker;
        this.promptLoader = promptLoader;
        this.properties = properties;
        this.githubClientProvider = githubClientProvider;
    }

    @Override
    public String name() {
        return "chat";
    }

    @Override
    public String description() {
        return "Ask the bot a question. Prior thread comments are included as context.";
    }

    @Override
    public CommandResult handle(SlashContext context) throws Exception {
        String question = context.args();
        if (question == null || question.isBlank()) {
            return CommandResult.error("Usage: `/chat <your question>`");
        }

        String threadTranscript = fetchThreadTranscript(context);

        String userMessage = threadTranscript.isEmpty()
                ? question
                : "Prior thread comments (oldest first):\n\n" + threadTranscript
                        + "\n\n---\n\nCurrent question:\n" + question;

        String sessionKey = sessionKeyFor(context);
        log.debug("Dispatching /chat for {} (thread history: {} chars, question: {} chars)",
                sessionKey, threadTranscript.length(), question.length());

        String reply = agentInvoker.invoke(sessionKey, userMessage, promptLoader.load());
        return CommandResult.ok(reply);
    }

    private String fetchThreadTranscript(SlashContext context) {
        if (context.issue() <= 0) {
            return "";
        }
        try {
            GHRepository repo = githubClientProvider.getClient().getRepository(context.repo());
            GHIssue issue = repo.getIssue(context.issue());
            List<GHIssueComment> comments = issue.getComments();
            if (comments.isEmpty()) {
                return "";
            }
            int limit = Math.min(properties.maxThreadHistory(), comments.size());
            // Include the earliest {limit} comments in insertion order.
            StringBuilder sb = new StringBuilder();
            int start = Math.max(0, comments.size() - limit);
            for (int i = start; i < comments.size(); i++) {
                GHIssueComment c = comments.get(i);
                // Skip the just-posted /chat trigger if it matches — we don't want
                // the LLM to see its own prompt as a prior "comment".
                if (c.getId() == context.commentId()) {
                    continue;
                }
                String author = safeLogin(c);
                sb.append("@").append(author).append(":\n").append(c.getBody()).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to fetch thread history for {} #{}: {} — continuing without it",
                    context.repo(), context.issue(), e.getMessage());
            return "";
        }
    }

    private String safeLogin(GHIssueComment c) {
        try {
            return c.getUser() == null ? "unknown" : c.getUser().getLogin();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String sessionKeyFor(SlashContext context) {
        return "github:" + context.repo() + "#" + context.issue();
    }
}

package io.jaiclaw.cli.github.handlers;

import io.jaiclaw.cli.github.AgentInvoker;
import io.jaiclaw.cli.github.SystemPromptLoader;
import io.jaiclaw.cli.github.slashcmd.CommandResult;
import io.jaiclaw.cli.github.slashcmd.SlashCommand;
import io.jaiclaw.cli.github.slashcmd.SlashContext;
import io.jaiclaw.tools.github.client.GithubClientProvider;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestFileDetail;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code /summarize} — one-shot PR summary. Only works when triggered
 * from a PR comment; issues without a PR return an error.
 *
 * <p>Fetches the PR title/body plus the concatenated diff, hands both to
 * the LLM with a summarisation instruction, posts the reply back.
 */
@Component
public class SummarizeSlashHandler implements SlashCommand {

    private static final Logger log = LoggerFactory.getLogger(SummarizeSlashHandler.class);
    private static final int MAX_DIFF_CHARS = 40_000; // keep well under LLM input budget

    private final AgentInvoker agentInvoker;
    private final SystemPromptLoader promptLoader;
    private final GithubClientProvider githubClientProvider;

    public SummarizeSlashHandler(AgentInvoker agentInvoker,
                                 SystemPromptLoader promptLoader,
                                 GithubClientProvider githubClientProvider) {
        this.agentInvoker = agentInvoker;
        this.promptLoader = promptLoader;
        this.githubClientProvider = githubClientProvider;
    }

    @Override
    public String name() {
        return "summarize";
    }

    @Override
    public String description() {
        return "Summarize a pull request (title, body, and full diff). PR-only.";
    }

    @Override
    public CommandResult handle(SlashContext context) throws Exception {
        if (context.issue() <= 0) {
            return CommandResult.error("`/summarize` needs a pull-request context.");
        }

        GHRepository repo = githubClientProvider.getClient().getRepository(context.repo());
        GHPullRequest pr;
        try {
            pr = repo.getPullRequest(context.issue());
        } catch (Exception e) {
            return CommandResult.error("`/summarize` only works on pull requests, not plain issues. "
                    + "(#" + context.issue() + " is not a PR.)");
        }

        StringBuilder diff = new StringBuilder();
        List<GHPullRequestFileDetail> files = pr.listFiles().toList();
        int truncatedFiles = 0;
        for (GHPullRequestFileDetail file : files) {
            String patch = file.getPatch();
            if (patch == null || patch.isEmpty()) {
                continue;
            }
            if (diff.length() + patch.length() > MAX_DIFF_CHARS) {
                truncatedFiles = files.size() - (files.indexOf(file));
                break;
            }
            diff.append("diff --git a/").append(file.getFilename())
                    .append(" b/").append(file.getFilename()).append('\n');
            diff.append(patch).append('\n');
        }
        String truncationNote = truncatedFiles > 0
                ? "\n\n[note: diff truncated at " + MAX_DIFF_CHARS + " chars; "
                        + truncatedFiles + " file(s) omitted]"
                : "";

        String userMessage = "Summarize this pull request. Focus on: purpose, key changes, "
                + "notable risks, and what a reviewer should double-check.\n\n"
                + "## PR title\n" + pr.getTitle() + "\n\n"
                + "## PR body\n" + (pr.getBody() == null ? "(empty)" : pr.getBody()) + "\n\n"
                + "## Diff\n```diff\n" + diff.toString() + "\n```" + truncationNote;

        log.debug("Dispatching /summarize for {} #{} (files: {}, diff chars: {})",
                context.repo(), context.issue(), files.size(), diff.length());

        String sessionKey = "github-summarize:" + context.repo() + "#" + context.issue();
        String reply = agentInvoker.invoke(sessionKey, userMessage, promptLoader.load());
        return CommandResult.ok(reply);
    }
}

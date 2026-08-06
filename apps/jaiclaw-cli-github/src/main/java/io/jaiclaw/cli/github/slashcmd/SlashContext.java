package io.jaiclaw.cli.github.slashcmd;

/**
 * The invocation context passed to a {@link SlashCommand} handler.
 *
 * @param repo       {@code owner/name}
 * @param issue      issue or PR number (0 when triggered by a commit_comment
 *                   with no associated issue)
 * @param sha        commit SHA — populated for {@code commit_comment}
 *                   events, null otherwise
 * @param body       full raw comment body (with the leading /command
 *                   still present — Splitting into {@code args} strips it)
 * @param args       everything after the {@code /command} token, trimmed;
 *                   may be blank if the user just typed {@code /command}
 * @param commentId  the ID of the comment that triggered this run;
 *                   -1 when unknown (e.g. testing from the CLI)
 */
public record SlashContext(
        String repo,
        int issue,
        String sha,
        String body,
        String args,
        long commentId
) {}

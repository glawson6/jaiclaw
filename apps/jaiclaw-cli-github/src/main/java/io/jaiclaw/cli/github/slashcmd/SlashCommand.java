package io.jaiclaw.cli.github.slashcmd;

/**
 * SPI for a GitHub slash-command handler. Every implementation is a Spring
 * {@code @Component} and gets auto-discovered by {@link SlashCommandRegistry}.
 *
 * <p>Naming convention: {@link #name()} is the token that follows the
 * leading slash in a PR/issue comment. For example, a comment body of
 * {@code "/chat what does this do?"} routes to the handler whose
 * {@code name()} returns {@code "chat"}.
 */
public interface SlashCommand {

    /** The command token (without the leading slash). Lower-case, no spaces. */
    String name();

    /** One-line description used by {@code /help}. */
    String description();

    /**
     * Execute the command. The dispatcher has already stripped the
     * {@code /command} prefix from the body; whatever remains is in
     * {@link SlashContext#args()}.
     *
     * <p>Return a {@link CommandResult}. If {@link CommandResult#reply()}
     * is non-blank, the dispatcher posts it as a comment on the same
     * thread. If blank, the command is treated as a no-op post (useful
     * when a command has already posted its own reply via the GitHub
     * tool set — e.g. writing inline review comments).
     */
    CommandResult handle(SlashContext context) throws Exception;
}

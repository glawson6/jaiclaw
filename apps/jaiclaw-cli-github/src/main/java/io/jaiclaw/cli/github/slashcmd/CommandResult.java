package io.jaiclaw.cli.github.slashcmd;

/**
 * The outcome of a slash-command execution.
 *
 * @param reply the Markdown body to post as a reply comment. Blank or null
 *              means the handler already posted its own output (or the
 *              command is a no-op post).
 * @param ok    true iff execution succeeded. False signals the dispatcher
 *              to add an "error" banner around the reply.
 */
public record CommandResult(String reply, boolean ok) {

    public static CommandResult ok(String reply) {
        return new CommandResult(reply, true);
    }

    public static CommandResult error(String message) {
        return new CommandResult(message, false);
    }

    public static CommandResult noReply() {
        return new CommandResult("", true);
    }
}

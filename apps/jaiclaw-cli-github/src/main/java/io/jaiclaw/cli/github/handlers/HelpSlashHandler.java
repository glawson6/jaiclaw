package io.jaiclaw.cli.github.handlers;

import io.jaiclaw.cli.github.slashcmd.CommandResult;
import io.jaiclaw.cli.github.slashcmd.SlashCommand;
import io.jaiclaw.cli.github.slashcmd.SlashCommandRegistry;
import io.jaiclaw.cli.github.slashcmd.SlashContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * {@code /help} — enumerate every registered slash command. Non-LLM,
 * runs in a few milliseconds. Read via ObjectProvider to avoid the
 * self-injection cycle at Registry construction time.
 */
@Component
public class HelpSlashHandler implements SlashCommand {

    private final ObjectProvider<SlashCommandRegistry> registryProvider;

    public HelpSlashHandler(ObjectProvider<SlashCommandRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "List available slash commands.";
    }

    @Override
    public CommandResult handle(SlashContext context) {
        SlashCommandRegistry registry = registryProvider.getObject();
        String body = registry.all().stream()
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .map(cmd -> "- `/" + cmd.name() + "` — " + cmd.description())
                .collect(Collectors.joining("\n"));

        String reply = "### Available commands\n\n" + body
                + "\n\n_Powered by [JaiClaw](https://github.com/glawson6/jaiclaw)._";
        return CommandResult.ok(reply);
    }
}

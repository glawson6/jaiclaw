package io.jaiclaw.cli.github.handlers;

import io.jaiclaw.cli.github.AgentInvoker;
import io.jaiclaw.cli.github.SystemPromptLoader;
import io.jaiclaw.cli.github.slashcmd.CommandResult;
import io.jaiclaw.cli.github.slashcmd.SlashCommand;
import io.jaiclaw.cli.github.slashcmd.SlashContext;
import io.jaiclaw.docs.DocsRepository;
import io.jaiclaw.docs.DocsSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /faq [topic]} — LLM-generated FAQ answer sourced from JaiClaw
 * project documentation via the {@code jaiclaw-docs} extension.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>With no args: hands the top ~5 documentation entries to the LLM
 *       and asks it to produce a short FAQ digest.</li>
 *   <li>With args: performs a keyword search on the docs corpus,
 *       feeds the top hits to the LLM, asks for an answer to the topic.</li>
 * </ul>
 *
 * <p>The {@link DocsRepository} bean is injected via {@link ObjectProvider}
 * so this handler still boots (returning a friendly error) when the
 * {@code jaiclaw-docs} extension isn't on the classpath.
 */
@Component
public class FaqSlashHandler implements SlashCommand {

    private static final Logger log = LoggerFactory.getLogger(FaqSlashHandler.class);
    private static final int MAX_HITS = 5;

    private final AgentInvoker agentInvoker;
    private final SystemPromptLoader promptLoader;
    private final ObjectProvider<DocsRepository> docsRepositoryProvider;

    public FaqSlashHandler(AgentInvoker agentInvoker,
                           SystemPromptLoader promptLoader,
                           ObjectProvider<DocsRepository> docsRepositoryProvider) {
        this.agentInvoker = agentInvoker;
        this.promptLoader = promptLoader;
        this.docsRepositoryProvider = docsRepositoryProvider;
    }

    @Override
    public String name() {
        return "faq";
    }

    @Override
    public String description() {
        return "Answer a project FAQ using JaiClaw documentation.";
    }

    @Override
    public CommandResult handle(SlashContext context) {
        DocsRepository docs = docsRepositoryProvider.getIfAvailable();
        if (docs == null) {
            return CommandResult.error(
                    "`/faq` needs the `jaiclaw-docs` extension on the classpath — not detected.");
        }

        String topic = context.args();
        String corpus;
        String userInstruction;
        if (topic == null || topic.isBlank()) {
            corpus = docs.findAll().stream()
                    .limit(MAX_HITS)
                    .map(e -> "### " + e.name() + "\n\n" + truncate(e.content()))
                    .collect(Collectors.joining("\n\n---\n\n"));
            userInstruction = "Produce a short 5-item FAQ digest from the following documentation. "
                    + "Each item is a question and a two-sentence answer.";
        } else {
            List<DocsSearchResult> hits = docs.search(topic, MAX_HITS);
            if (hits.isEmpty()) {
                return CommandResult.ok(
                        "_No documentation matches for `" + topic + "`. Try broader keywords._");
            }
            corpus = hits.stream()
                    .map(h -> "### " + h.name() + " (score " + h.score() + ")\n\n"
                            + truncate(h.snippet()))
                    .collect(Collectors.joining("\n\n---\n\n"));
            userInstruction = "Answer this question using the documentation excerpts below. "
                    + "Cite the source name when you draw from it.\n\nQuestion: " + topic;
        }

        String userMessage = userInstruction + "\n\n" + corpus;
        log.debug("Dispatching /faq (topic: '{}', corpus chars: {})",
                topic == null ? "" : topic, corpus.length());

        String sessionKey = "github-faq:" + context.repo() + "#" + context.issue();
        String reply = agentInvoker.invoke(sessionKey, userMessage, promptLoader.load());
        return CommandResult.ok(reply);
    }

    private String truncate(String content) {
        if (content == null) return "";
        int max = 4_000;
        return content.length() > max ? content.substring(0, max) + "\n...[truncated]" : content;
    }
}

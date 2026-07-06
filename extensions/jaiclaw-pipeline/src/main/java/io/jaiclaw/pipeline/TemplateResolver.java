package io.jaiclaw.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code {{stages.X.output}}}, {@code {{stages.X.metadata.key}}},
 * {@code {{pipeline.*}}}, and {@code {{input}}} placeholders against a
 * {@link PipelineContext}. Unresolved placeholders are left untouched and a
 * single WARN is logged listing the variables that <em>were</em> available so
 * authors can spot typos quickly.
 */
public final class TemplateResolver {

    private static final Logger log = LoggerFactory.getLogger(TemplateResolver.class);

    // Stage names may contain hyphens (e.g. "classify-and-sentiment"), so the
    // capture group accepts \w and '-'.
    private static final Pattern OUTPUT_PATTERN =
            Pattern.compile("\\{\\{stages\\.([\\w-]+)\\.output}}");

    private static final Pattern METADATA_PATTERN =
            Pattern.compile("\\{\\{stages\\.([\\w-]+)\\.metadata\\.([\\w-]+)}}");

    private static final Pattern PIPELINE_PATTERN =
            Pattern.compile("\\{\\{pipeline\\.(\\w+)}}");

    private static final Pattern INPUT_PATTERN =
            Pattern.compile("\\{\\{input}}");

    private static final Pattern ANY_REMAINING_PATTERN =
            Pattern.compile("\\{\\{[^{}]+}}");

    private TemplateResolver() {}

    /**
     * Resolve every supported placeholder against {@code ctx}. Logs a WARN for
     * any placeholder left unresolved (lists the available variable keys),
     * then returns the partially-resolved string unchanged at those spots.
     */
    public static String resolve(String template, PipelineContext ctx) {
        if (template == null || template.isEmpty() || ctx == null) return template;

        String result = template;
        result = resolveMetadata(result, ctx.stageOutputs());
        result = resolveStageOutputs(result, ctx.stageOutputs());
        result = resolvePipelineVars(result, ctx);
        result = resolveInput(result, ctx);
        warnOnRemaining(result, template, ctx);
        return result;
    }

    private static String resolveMetadata(String template, Map<String, PipelineContext.StageOutput> outputs) {
        if (outputs == null || outputs.isEmpty()) return template;
        Matcher m = METADATA_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            PipelineContext.StageOutput out = outputs.get(m.group(1));
            if (out != null && out.metadata().containsKey(m.group(2))) {
                m.appendReplacement(sb, Matcher.quoteReplacement(out.metadata().get(m.group(2))));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String resolveStageOutputs(String template, Map<String, PipelineContext.StageOutput> outputs) {
        if (outputs == null || outputs.isEmpty()) return template;
        Matcher m = OUTPUT_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            PipelineContext.StageOutput out = outputs.get(m.group(1));
            if (out != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(out.output()));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String resolvePipelineVars(String template, PipelineContext ctx) {
        Matcher m = PIPELINE_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = switch (key) {
                case "id" -> ctx.pipelineId();
                case "executionId" -> ctx.executionId();
                case "tenantId" -> ctx.tenantId();
                case "correlationId" -> ctx.correlationId();
                default -> null;
            };
            if (value != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String resolveInput(String template, PipelineContext ctx) {
        if (!template.contains("{{input}}")) return template;
        String input = ctx.metadata() == null ? null : ctx.metadata().get(PipelineContext.INPUT_METADATA_KEY);
        if (input == null) return template;
        return INPUT_PATTERN.matcher(template).replaceAll(Matcher.quoteReplacement(input));
    }

    private static void warnOnRemaining(String resolved, String original, PipelineContext ctx) {
        Matcher m = ANY_REMAINING_PATTERN.matcher(resolved);
        if (!m.find()) return;
        TreeSet<String> unresolved = new TreeSet<>();
        do {
            unresolved.add(m.group());
        } while (m.find());
        log.warn("Unresolved placeholder(s) {} in pipeline '{}' (executionId={}). Available variables: {}",
                unresolved, ctx.pipelineId(), ctx.executionId(),
                new TreeSet<>(ctx.availableVariables().keySet()));
    }
}

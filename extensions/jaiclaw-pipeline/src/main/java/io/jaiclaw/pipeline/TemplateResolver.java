package io.jaiclaw.pipeline;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code {{stages.X.output}}} and {@code {{stages.X.metadata.key}}} placeholders
 * from {@link PipelineContext#stageOutputs()}.
 */
public final class TemplateResolver {

    private static final Pattern OUTPUT_PATTERN =
            Pattern.compile("\\{\\{stages\\.(\\w+)\\.output}}");

    private static final Pattern METADATA_PATTERN =
            Pattern.compile("\\{\\{stages\\.(\\w+)\\.metadata\\.(\\w+)}}");

    private TemplateResolver() {}

    /**
     * Resolve all placeholders in the template using stage outputs from the context.
     * Unresolved placeholders are left as-is.
     *
     * @param template     the template string with placeholders
     * @param stageOutputs the accumulated stage outputs
     * @return the resolved template
     */
    public static String resolve(String template, Map<String, PipelineContext.StageOutput> stageOutputs) {
        if (template == null || template.isEmpty() || stageOutputs == null || stageOutputs.isEmpty()) {
            return template;
        }

        String result = template;

        // Resolve {{stages.X.metadata.key}} first (more specific pattern)
        Matcher metaMatcher = METADATA_PATTERN.matcher(result);
        StringBuilder metaBuilder = new StringBuilder();
        while (metaMatcher.find()) {
            String stageName = metaMatcher.group(1);
            String metaKey = metaMatcher.group(2);
            PipelineContext.StageOutput output = stageOutputs.get(stageName);
            if (output != null && output.metadata().containsKey(metaKey)) {
                metaMatcher.appendReplacement(metaBuilder, Matcher.quoteReplacement(output.metadata().get(metaKey)));
            } else {
                metaMatcher.appendReplacement(metaBuilder, Matcher.quoteReplacement(metaMatcher.group()));
            }
        }
        metaMatcher.appendTail(metaBuilder);
        result = metaBuilder.toString();

        // Resolve {{stages.X.output}}
        Matcher outputMatcher = OUTPUT_PATTERN.matcher(result);
        StringBuilder outputBuilder = new StringBuilder();
        while (outputMatcher.find()) {
            String stageName = outputMatcher.group(1);
            PipelineContext.StageOutput output = stageOutputs.get(stageName);
            if (output != null) {
                outputMatcher.appendReplacement(outputBuilder, Matcher.quoteReplacement(output.output()));
            } else {
                outputMatcher.appendReplacement(outputBuilder, Matcher.quoteReplacement(outputMatcher.group()));
            }
        }
        outputMatcher.appendTail(outputBuilder);

        return outputBuilder.toString();
    }
}

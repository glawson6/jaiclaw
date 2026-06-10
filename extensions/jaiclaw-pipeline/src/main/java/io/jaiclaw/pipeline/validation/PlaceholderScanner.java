package io.jaiclaw.pipeline.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@code {{...}}} placeholder tokens from template strings used by the
 * pipeline DSL. Mirrors the patterns understood by
 * {@link io.jaiclaw.pipeline.TemplateResolver}.
 */
final class PlaceholderScanner {

    // Stage names may include '-' (e.g. "classify-and-sentiment").
    private static final Pattern OUTPUT_PATTERN =
            Pattern.compile("\\{\\{stages\\.([\\w-]+)\\.output}}");

    private static final Pattern METADATA_PATTERN =
            Pattern.compile("\\{\\{stages\\.([\\w-]+)\\.metadata\\.([\\w-]+)}}");

    private static final Pattern PIPELINE_PATTERN =
            Pattern.compile("\\{\\{pipeline\\.(\\w+)}}");

    private static final Pattern INPUT_PATTERN =
            Pattern.compile("\\{\\{input}}");

    private static final Pattern ANY_PATTERN =
            Pattern.compile("\\{\\{([^{}]+)}}");

    /** A placeholder token found in a template. */
    record Placeholder(String raw, Kind kind, String stage, String key) {
        enum Kind { STAGE_OUTPUT, STAGE_METADATA, PIPELINE_VAR, INPUT, UNKNOWN }
    }

    private PlaceholderScanner() {}

    /**
     * Scan {@code template} and return all placeholder tokens it contains.
     * Returns an empty list for null / blank templates.
     */
    static List<Placeholder> scan(String template) {
        List<Placeholder> results = new ArrayList<>();
        if (template == null || template.isEmpty()) {
            return results;
        }
        Matcher any = ANY_PATTERN.matcher(template);
        while (any.find()) {
            String raw = any.group();
            Matcher m;
            if ((m = OUTPUT_PATTERN.matcher(raw)).matches()) {
                results.add(new Placeholder(raw, Placeholder.Kind.STAGE_OUTPUT, m.group(1), null));
            } else if ((m = METADATA_PATTERN.matcher(raw)).matches()) {
                results.add(new Placeholder(raw, Placeholder.Kind.STAGE_METADATA, m.group(1), m.group(2)));
            } else if ((m = PIPELINE_PATTERN.matcher(raw)).matches()) {
                results.add(new Placeholder(raw, Placeholder.Kind.PIPELINE_VAR, null, m.group(1)));
            } else if (INPUT_PATTERN.matcher(raw).matches()) {
                results.add(new Placeholder(raw, Placeholder.Kind.INPUT, null, null));
            } else {
                results.add(new Placeholder(raw, Placeholder.Kind.UNKNOWN, null, null));
            }
        }
        return results;
    }
}

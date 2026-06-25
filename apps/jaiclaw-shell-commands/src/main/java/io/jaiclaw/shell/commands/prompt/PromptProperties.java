package io.jaiclaw.shell.commands.prompt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to {@code jaiclaw.shell.prompt.*}. The {@code format} string supports
 * the Spring-style placeholders {@code ${identity}}, {@code ${profile}},
 * {@code ${agent}}, {@code ${model}}, {@code ${tenant}}. Unresolved placeholders
 * render as literal {@code ${name}} so operators can see what's wrong.
 */
@ConfigurationProperties(prefix = "jaiclaw.shell.prompt")
public record PromptProperties(
        String format,
        boolean colors
) {

    public static final String DEFAULT_FORMAT = "${identity} > ";

    public PromptProperties {
        if (format == null || format.isBlank()) format = DEFAULT_FORMAT;
    }

    public static PromptProperties defaults() {
        return new PromptProperties(DEFAULT_FORMAT, true);
    }
}

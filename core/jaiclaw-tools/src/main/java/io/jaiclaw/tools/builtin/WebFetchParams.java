package io.jaiclaw.tools.builtin;

import io.jaiclaw.core.tool.param.ToolParameter;

/**
 * Typed parameter record for {@link WebFetchTool}.
 *
 * <p>0.8.0 P3.2: replaces the pre-0.8.0 hand-written
 * {@code WebFetchTool.INPUT_SCHEMA} + {@code requireParam(...)}
 * extractions. Schema and binding both derive from this record.
 *
 * @param url             the URL to fetch content from (required)
 * @param timeout         timeout in seconds (default 30)
 * @param extractReadable extract clean readable text from HTML using Readability (default true)
 */
public record WebFetchParams(
        @ToolParameter(description = "The URL to fetch content from")
        String url,

        @ToolParameter(description = "Timeout in seconds (default 30)", required = false)
        Integer timeout,

        @ToolParameter(description = "Extract clean readable text from HTML using Readability (default true)",
                required = false)
        Boolean extractReadable
) {}

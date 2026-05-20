package io.jaiclaw.rules.engine.util;

import io.jaiclaw.rules.engine.model.RuleExecutionResponse;
import org.apache.commons.lang3.StringUtils;

import java.util.stream.Collectors;

/**
 * Utility class for formatting rule execution responses in LLM-friendly format.
 */
public final class RuleResponseFormatter {

    private RuleResponseFormatter() {}

    public static String formatAsSimpleString(RuleExecutionResponse response) {
        if (response == null) {
            return "No response available";
        }

        StringBuilder sb = new StringBuilder();

        if (response.isSuccess()) {
            sb.append("Rule Execution: SUCCESS\n");
            sb.append("Rule: ").append(response.getRuleName()).append("\n");
            sb.append("Rules Fired: ").append(response.getRulesFired()).append("\n");

            if (response.getMessages() != null && !response.getMessages().isEmpty()) {
                sb.append("Messages:\n");
                response.getMessages().forEach(msg -> sb.append("  - ").append(msg).append("\n"));
            }

            if (response.getResults() != null && !response.getResults().isEmpty()) {
                sb.append("Results:\n");
                response.getResults().forEach((key, value) ->
                    sb.append("  ").append(key).append(": ").append(value).append("\n"));
            }
        } else {
            sb.append("Rule Execution: FAILED\n");
            sb.append("Rule: ").append(response.getRuleName()).append("\n");
            sb.append("Error: ").append(StringUtils.defaultIfBlank(response.getError(), "Unknown error")).append("\n");
        }

        return sb.toString().trim();
    }

    public static String formatAsSummary(RuleExecutionResponse response) {
        if (response == null) {
            return "No response";
        }

        if (response.isSuccess()) {
            String messagesStr = response.getMessages() != null
                ? response.getMessages().stream().collect(Collectors.joining("; "))
                : "No messages";
            return String.format("Rule '%s' executed successfully. %d rules fired. %s",
                response.getRuleName(), response.getRulesFired(), messagesStr);
        } else {
            return String.format("Rule '%s' failed: %s",
                response.getRuleName(),
                StringUtils.defaultIfBlank(response.getError(), "Unknown error"));
        }
    }
}

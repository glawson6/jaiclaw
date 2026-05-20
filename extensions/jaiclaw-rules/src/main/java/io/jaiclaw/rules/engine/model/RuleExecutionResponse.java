package io.jaiclaw.rules.engine.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Response model for rule execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleExecutionResponse {

    private boolean success;
    private String ruleName;

    @Builder.Default
    private Instant executionTime = Instant.now();

    @Builder.Default
    private int rulesFired = 0;

    private Map<String, Object> results;

    @Builder.Default
    private List<String> messages = new ArrayList<>();

    private List<String> trace;
    private String error;

    public void addMessage(String message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
    }
}

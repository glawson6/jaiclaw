package io.jaiclaw.rules.engine.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request model for rule execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleExecutionRequest {

    @NotBlank(message = "Rule name must not be blank")
    @Size(min = 1, max = 100, message = "Rule name must be between 1 and 100 characters")
    private String ruleName;

    @NotNull(message = "Facts must not be null")
    private Map<String, Object> facts;

    private Map<String, String> context;

    @Builder.Default
    private boolean enableTrace = false;
}

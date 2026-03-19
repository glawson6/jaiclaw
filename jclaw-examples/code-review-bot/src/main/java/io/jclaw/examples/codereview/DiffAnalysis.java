package io.jclaw.examples.codereview;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Domain object representing an analyzed diff. Placed on the Embabel blackboard
 * after the analyzeDiff action completes. The GOAP planner sees this type as
 * a precondition for the generateReview action.
 */
@JsonClassDescription("Analysis of a code diff with identified issues and suggestions")
public record DiffAnalysis(
        @JsonProperty("summary")
        @JsonPropertyDescription("Brief summary of what the diff changes")
        String summary,

        @JsonProperty("issues")
        @JsonPropertyDescription("List of issues found in the code")
        List<String> issues,

        @JsonProperty("suggestions")
        @JsonPropertyDescription("List of improvement suggestions")
        List<String> suggestions,

        @JsonProperty("severity")
        @JsonPropertyDescription("Overall severity: low, medium, or high")
        String severity
) {}

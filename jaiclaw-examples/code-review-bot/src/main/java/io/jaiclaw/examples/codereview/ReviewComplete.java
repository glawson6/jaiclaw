package io.jaiclaw.examples.codereview;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Goal condition — placed on the blackboard when the code review is complete.
 * Contains the final formatted review ready for delivery.
 */
@JsonClassDescription("Completed code review with formatted feedback")
public record ReviewComplete(
        @JsonProperty("review")
        @JsonPropertyDescription("The formatted code review text")
        String review,

        @JsonProperty("approved")
        @JsonPropertyDescription("Whether the code changes are approved")
        boolean approved,

        @JsonProperty("issueCount")
        @JsonPropertyDescription("Total number of issues found")
        int issueCount
) {}

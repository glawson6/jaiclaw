package io.jclaw.examples.compliance;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Goal condition — the final compliance report.
 */
@JsonClassDescription("Compliance check report with findings and pass/fail status")
public record ComplianceReport(
        @JsonProperty("compliant")
        @JsonPropertyDescription("Whether the document passes compliance checks")
        boolean compliant,

        @JsonProperty("findings")
        @JsonPropertyDescription("List of compliance findings (violations or warnings)")
        List<String> findings,

        @JsonProperty("summary")
        @JsonPropertyDescription("Overall compliance summary")
        String summary,

        @JsonProperty("score")
        @JsonPropertyDescription("Compliance score from 0 to 100")
        int score
) {}

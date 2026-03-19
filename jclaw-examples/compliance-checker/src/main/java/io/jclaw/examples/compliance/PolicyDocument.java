package io.jclaw.examples.compliance;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Extracted policy rules from a compliance document.
 */
@JsonClassDescription("Extracted compliance policy with rules and requirements")
public record PolicyDocument(
        @JsonProperty("policyName")
        @JsonPropertyDescription("Name of the compliance policy")
        String policyName,

        @JsonProperty("rules")
        @JsonPropertyDescription("List of compliance rules extracted from the policy document")
        List<String> rules,

        @JsonProperty("requiredSections")
        @JsonPropertyDescription("Sections that must be present in compliant documents")
        List<String> requiredSections
) {}

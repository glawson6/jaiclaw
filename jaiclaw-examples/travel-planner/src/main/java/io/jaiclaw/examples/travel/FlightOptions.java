package io.jaiclaw.examples.travel;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Flight search results placed on the blackboard after flight research.
 */
@JsonClassDescription("Available flight options for the trip")
public record FlightOptions(
        @JsonProperty("options")
        @JsonPropertyDescription("List of flight options with price and schedule")
        List<String> options,

        @JsonProperty("bestOption")
        @JsonPropertyDescription("Recommended flight option")
        String bestOption,

        @JsonProperty("estimatedCost")
        @JsonPropertyDescription("Estimated flight cost per person in USD")
        double estimatedCost
) {}

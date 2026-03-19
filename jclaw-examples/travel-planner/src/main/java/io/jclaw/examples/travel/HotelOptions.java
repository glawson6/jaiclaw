package io.jclaw.examples.travel;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Hotel search results placed on the blackboard after hotel research.
 */
@JsonClassDescription("Available hotel options for the trip")
public record HotelOptions(
        @JsonProperty("options")
        @JsonPropertyDescription("List of hotel options with price and rating")
        List<String> options,

        @JsonProperty("bestOption")
        @JsonPropertyDescription("Recommended hotel option")
        String bestOption,

        @JsonProperty("estimatedCostPerNight")
        @JsonPropertyDescription("Estimated cost per night in USD")
        double estimatedCostPerNight
) {}

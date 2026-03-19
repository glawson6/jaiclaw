package io.jclaw.examples.travel;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Initial input representing a travel planning request.
 */
@JsonClassDescription("A travel planning request with destination and dates")
public record TravelRequest(
        @JsonProperty("destination")
        @JsonPropertyDescription("Travel destination city or region")
        String destination,

        @JsonProperty("departureDate")
        @JsonPropertyDescription("Departure date (YYYY-MM-DD)")
        String departureDate,

        @JsonProperty("returnDate")
        @JsonPropertyDescription("Return date (YYYY-MM-DD)")
        String returnDate,

        @JsonProperty("budget")
        @JsonPropertyDescription("Total budget in USD")
        double budget,

        @JsonProperty("travelers")
        @JsonPropertyDescription("Number of travelers")
        int travelers
) {}

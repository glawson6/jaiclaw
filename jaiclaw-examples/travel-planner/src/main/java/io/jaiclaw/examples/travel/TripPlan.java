package io.jaiclaw.examples.travel;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Goal condition — the final assembled trip plan.
 */
@JsonClassDescription("Complete trip plan with flights, hotels, and itinerary")
public record TripPlan(
        @JsonProperty("summary")
        @JsonPropertyDescription("Trip summary")
        String summary,

        @JsonProperty("totalCost")
        @JsonPropertyDescription("Total estimated cost in USD")
        double totalCost,

        @JsonProperty("withinBudget")
        @JsonPropertyDescription("Whether the plan is within the requested budget")
        boolean withinBudget,

        @JsonProperty("itinerary")
        @JsonPropertyDescription("Day-by-day itinerary")
        String itinerary
) {}

package io.jclaw.examples.travel;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;

/**
 * Embabel GOAP agent for multi-step trip planning.
 *
 * <p>The planner chains three actions automatically:
 * <ol>
 *   <li>searchFlights — TravelRequest → FlightOptions</li>
 *   <li>searchHotels — TravelRequest → HotelOptions</li>
 *   <li>assemblePlan — FlightOptions + HotelOptions + TravelRequest → TripPlan (goal)</li>
 * </ol>
 *
 * <p>In production, searchFlights and searchHotels would use BrowserService
 * (Playwright) to scrape actual travel sites.
 */
@Agent(description = "Plans trips by researching flights and hotels, then assembling a complete itinerary")
public class TravelPlannerAgent {

    @Action(description = "Search for available flights to the destination")
    public FlightOptions searchFlights(TravelRequest request, OperationContext context) {
        return context.ai()
                .withDefaultLlm()
                .createObject(
                        "Simulate searching for flights from a major US airport to "
                                + request.destination() + " departing " + request.departureDate()
                                + " returning " + request.returnDate()
                                + " for " + request.travelers() + " travelers."
                                + " Budget: $" + request.budget()
                                + ". Return realistic flight options with airlines, times, and prices.",
                        FlightOptions.class
                );
    }

    @Action(description = "Search for available hotels at the destination")
    public HotelOptions searchHotels(TravelRequest request, OperationContext context) {
        return context.ai()
                .withDefaultLlm()
                .createObject(
                        "Simulate searching for hotels in " + request.destination()
                                + " from " + request.departureDate() + " to " + request.returnDate()
                                + " for " + request.travelers() + " guests."
                                + " Budget remaining for accommodation: estimate based on $" + request.budget()
                                + " total trip budget. Return realistic options with hotel names, ratings, and prices.",
                        HotelOptions.class
                );
    }

    @Action(description = "Assemble the final trip plan from flight and hotel research")
    @AchievesGoal(description = "A complete trip plan with flights, hotels, and day-by-day itinerary")
    public TripPlan assemblePlan(TravelRequest request, FlightOptions flights, HotelOptions hotels,
                                 OperationContext context) {
        return context.ai()
                .withDefaultLlm()
                .createObject(
                        "Assemble a complete trip plan for " + request.destination() + ":\n"
                                + "- Flight: " + flights.bestOption() + " ($" + flights.estimatedCost() + "/person)\n"
                                + "- Hotel: " + hotels.bestOption() + " ($" + hotels.estimatedCostPerNight() + "/night)\n"
                                + "- Travelers: " + request.travelers() + "\n"
                                + "- Dates: " + request.departureDate() + " to " + request.returnDate() + "\n"
                                + "- Budget: $" + request.budget() + "\n\n"
                                + "Create a day-by-day itinerary with activities, calculate total cost, "
                                + "and indicate if it's within budget.",
                        TripPlan.class
                );
    }
}

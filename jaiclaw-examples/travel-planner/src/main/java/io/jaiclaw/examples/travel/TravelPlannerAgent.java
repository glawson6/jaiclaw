package io.jaiclaw.examples.travel;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import io.jaiclaw.examples.travel.api.TravelDataProvider;

/**
 * Embabel GOAP agent for multi-step trip planning.
 *
 * <p>The planner chains five actions automatically:
 * <ol>
 *   <li>searchFlights — TravelRequest → FlightOptions</li>
 *   <li>searchHotels — TravelRequest → HotelOptions</li>
 *   <li>searchActivities — TravelRequest → ActivityOptions</li>
 *   <li>checkWeather — TravelRequest → WeatherForecast</li>
 *   <li>assemblePlan — all results + TravelRequest → TripPlan (goal)</li>
 * </ol>
 *
 * <p>The first 4 actions can run in any order (GOAP planner decides).
 * {@code assemblePlan} depends on all 4 results being on the blackboard.
 *
 * <p>Data comes from {@link TravelDataProvider}. The example ships with
 * a stub implementation; swap in your own bean to hit a real travel API.
 */
@Agent(description = "Plans trips by researching flights, hotels, activities, and weather, then assembling a complete itinerary")
public class TravelPlannerAgent {

    private final TravelDataProvider provider;

    public TravelPlannerAgent(TravelDataProvider provider) {
        this.provider = provider;
    }

    @Action(description = "Search for available flights to the destination")
    public FlightOptions searchFlights(TravelRequest request) {
        return provider.searchFlights(
                request.origin(), request.destination(),
                request.departureDate(), request.returnDate(),
                request.travelers(), request.budget());
    }

    @Action(description = "Search for available hotels at the destination")
    public HotelOptions searchHotels(TravelRequest request) {
        return provider.searchHotels(
                request.destination(),
                request.departureDate(), request.returnDate(),
                request.travelers(), request.budget());
    }

    @Action(description = "Search for activities and experiences at the destination")
    public ActivityOptions searchActivities(TravelRequest request) {
        return provider.searchActivities(
                request.destination(),
                request.departureDate(), request.returnDate(),
                request.travelers(), request.budget());
    }

    @Action(description = "Get weather forecast for the destination during travel dates")
    public WeatherForecast checkWeather(TravelRequest request) {
        return provider.getWeather(
                request.destination(),
                request.departureDate(), request.returnDate());
    }

    @Action(description = "Assemble the final trip plan from all research results")
    @AchievesGoal(description = "A complete trip plan with flights, hotels, activities, weather, and day-by-day itinerary")
    public TripPlan assemblePlan(TravelRequest request, FlightOptions flights, HotelOptions hotels,
                                 ActivityOptions activities, WeatherForecast weather,
                                 OperationContext context) {
        return context.ai()
                .withDefaultLlm()
                .createObject(
                        "Assemble a complete trip plan for " + request.destination() + ":\n"
                                + "- Origin: " + request.origin() + "\n"
                                + "- Flight: " + flights.bestOption() + " ($" + flights.estimatedCost() + "/person)\n"
                                + "- Hotel: " + hotels.bestOption() + " ($" + hotels.estimatedCostPerNight() + "/night)\n"
                                + "- Activities available: " + activities.activities().size()
                                + " options (est. $" + activities.totalEstimatedCost() + " total)\n"
                                + "- Weather: " + weather.packingAdvice() + "\n"
                                + "- Travelers: " + request.travelers() + "\n"
                                + "- Dates: " + request.departureDate() + " to " + request.returnDate() + "\n"
                                + "- Budget: $" + request.budget() + "\n\n"
                                + "Create a day-by-day itinerary incorporating the best activities "
                                + "and weather conditions. Include a flight summary, hotel summary, "
                                + "packing list based on weather, calculate total cost, "
                                + "and indicate if it's within budget.",
                        TripPlan.class
                );
    }
}

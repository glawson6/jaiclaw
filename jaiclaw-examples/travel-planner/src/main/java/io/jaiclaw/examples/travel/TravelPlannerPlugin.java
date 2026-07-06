package io.jaiclaw.examples.travel;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.examples.travel.api.TravelDataProvider;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.Map;
import java.util.Set;

/**
 * JaiClaw plugin registering 4 travel planning tools callable by the LLM tool loop.
 *
 * <p>All tools delegate to the active {@link TravelDataProvider} implementation.
 * The example ships with {@code StubTravelDataProvider}; swap in your own bean
 * to hit a real travel API.
 *
 * <p>Registered as a bean by {@link TravelPlannerConfiguration}.
 */
public class TravelPlannerPlugin implements JaiClawPlugin {

    private final TravelDataProvider provider;
    private final ObjectMapper objectMapper;

    public TravelPlannerPlugin(TravelDataProvider provider, ObjectMapper objectMapper) {
        this.provider = provider;
        this.objectMapper = objectMapper;
    }

    @Override
    public PluginDefinition definition() {
        return new PluginDefinition(
                "travel-planner-plugin",
                "Travel Planner Plugin",
                "Tools for searching flights, hotels, activities, and weather",
                "1.0.0",
                PluginKind.GENERAL
        );
    }

    @Override
    public void register(PluginApi api) {
        api.registerTool(new SearchFlightsTool(provider, objectMapper));
        api.registerTool(new SearchHotelsTool(provider, objectMapper));
        api.registerTool(new SearchActivitiesTool(provider, objectMapper));
        api.registerTool(new GetWeatherTool(provider, objectMapper));
    }

    // ---- Tools ----

    static class SearchFlightsTool extends AbstractBuiltinTool {

        private final TravelDataProvider provider;
        private final ObjectMapper objectMapper;

        SearchFlightsTool(TravelDataProvider provider, ObjectMapper objectMapper) {
            super(new ToolDefinition(
                    "search_flights",
                    "Search for available flights between two cities",
                    ToolCatalog.SECTION_CUSTOM,
                    """
                    {
                      "type": "object",
                      "properties": {
                        "origin": { "type": "string", "description": "Departure city or airport code" },
                        "destination": { "type": "string", "description": "Destination city or airport code" },
                        "departure_date": { "type": "string", "description": "Departure date (YYYY-MM-DD)" },
                        "return_date": { "type": "string", "description": "Return date (YYYY-MM-DD)" },
                        "travelers": { "type": "integer", "description": "Number of travelers" },
                        "budget": { "type": "number", "description": "Total trip budget in USD" }
                      },
                      "required": ["origin", "destination", "departure_date", "return_date"]
                    }
                    """,
                    Set.of(ToolProfile.FULL)
            ));
            this.provider = provider;
            this.objectMapper = objectMapper;
        }

        @Override
        protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
            String origin = requireParam(parameters, "origin");
            String destination = requireParam(parameters, "destination");
            String departureDate = requireParam(parameters, "departure_date");
            String returnDate = requireParam(parameters, "return_date");
            int travelers = parameters.containsKey("travelers")
                    ? ((Number) parameters.get("travelers")).intValue() : 1;
            double budget = parameters.containsKey("budget")
                    ? ((Number) parameters.get("budget")).doubleValue() : 10000.0;

            FlightOptions result = provider.searchFlights(origin, destination,
                    departureDate, returnDate, travelers, budget);
            return new ToolResult.Success(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }

    static class SearchHotelsTool extends AbstractBuiltinTool {

        private final TravelDataProvider provider;
        private final ObjectMapper objectMapper;

        SearchHotelsTool(TravelDataProvider provider, ObjectMapper objectMapper) {
            super(new ToolDefinition(
                    "search_hotels",
                    "Search for available hotels at a destination",
                    ToolCatalog.SECTION_CUSTOM,
                    """
                    {
                      "type": "object",
                      "properties": {
                        "destination": { "type": "string", "description": "Destination city" },
                        "check_in": { "type": "string", "description": "Check-in date (YYYY-MM-DD)" },
                        "check_out": { "type": "string", "description": "Check-out date (YYYY-MM-DD)" },
                        "guests": { "type": "integer", "description": "Number of guests" },
                        "budget": { "type": "number", "description": "Total trip budget in USD" }
                      },
                      "required": ["destination", "check_in", "check_out"]
                    }
                    """,
                    Set.of(ToolProfile.FULL)
            ));
            this.provider = provider;
            this.objectMapper = objectMapper;
        }

        @Override
        protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
            String destination = requireParam(parameters, "destination");
            String checkIn = requireParam(parameters, "check_in");
            String checkOut = requireParam(parameters, "check_out");
            int guests = parameters.containsKey("guests")
                    ? ((Number) parameters.get("guests")).intValue() : 2;
            double budget = parameters.containsKey("budget")
                    ? ((Number) parameters.get("budget")).doubleValue() : 5000.0;

            HotelOptions result = provider.searchHotels(destination, checkIn, checkOut, guests, budget);
            return new ToolResult.Success(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }

    static class SearchActivitiesTool extends AbstractBuiltinTool {

        private final TravelDataProvider provider;
        private final ObjectMapper objectMapper;

        SearchActivitiesTool(TravelDataProvider provider, ObjectMapper objectMapper) {
            super(new ToolDefinition(
                    "search_activities",
                    "Search for activities and experiences at a destination",
                    ToolCatalog.SECTION_CUSTOM,
                    """
                    {
                      "type": "object",
                      "properties": {
                        "destination": { "type": "string", "description": "Destination city" },
                        "start_date": { "type": "string", "description": "Trip start date (YYYY-MM-DD)" },
                        "end_date": { "type": "string", "description": "Trip end date (YYYY-MM-DD)" },
                        "travelers": { "type": "integer", "description": "Number of travelers" },
                        "budget": { "type": "number", "description": "Total trip budget in USD" }
                      },
                      "required": ["destination", "start_date", "end_date"]
                    }
                    """,
                    Set.of(ToolProfile.FULL)
            ));
            this.provider = provider;
            this.objectMapper = objectMapper;
        }

        @Override
        protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
            String destination = requireParam(parameters, "destination");
            String startDate = requireParam(parameters, "start_date");
            String endDate = requireParam(parameters, "end_date");
            int travelers = parameters.containsKey("travelers")
                    ? ((Number) parameters.get("travelers")).intValue() : 2;
            double budget = parameters.containsKey("budget")
                    ? ((Number) parameters.get("budget")).doubleValue() : 5000.0;

            ActivityOptions result = provider.searchActivities(destination, startDate, endDate, travelers, budget);
            return new ToolResult.Success(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }

    static class GetWeatherTool extends AbstractBuiltinTool {

        private final TravelDataProvider provider;
        private final ObjectMapper objectMapper;

        GetWeatherTool(TravelDataProvider provider, ObjectMapper objectMapper) {
            super(new ToolDefinition(
                    "get_weather",
                    "Get weather forecast for a destination during travel dates",
                    ToolCatalog.SECTION_CUSTOM,
                    """
                    {
                      "type": "object",
                      "properties": {
                        "destination": { "type": "string", "description": "Destination city" },
                        "start_date": { "type": "string", "description": "Trip start date (YYYY-MM-DD)" },
                        "end_date": { "type": "string", "description": "Trip end date (YYYY-MM-DD)" }
                      },
                      "required": ["destination", "start_date", "end_date"]
                    }
                    """,
                    Set.of(ToolProfile.FULL)
            ));
            this.provider = provider;
            this.objectMapper = objectMapper;
        }

        @Override
        protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
            String destination = requireParam(parameters, "destination");
            String startDate = requireParam(parameters, "start_date");
            String endDate = requireParam(parameters, "end_date");

            WeatherForecast result = provider.getWeather(destination, startDate, endDate);
            return new ToolResult.Success(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }
}

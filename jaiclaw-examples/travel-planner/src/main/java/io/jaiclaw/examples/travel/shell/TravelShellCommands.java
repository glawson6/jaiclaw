package io.jaiclaw.examples.travel.shell;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.AgentRuntimeContext;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.core.model.AgentIdentity;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.examples.travel.ActivityOptions;
import io.jaiclaw.examples.travel.FlightOptions;
import io.jaiclaw.examples.travel.HotelOptions;
import io.jaiclaw.examples.travel.WeatherForecast;
import io.jaiclaw.examples.travel.api.TravelDataProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

/**
 * Spring Shell commands for the travel planner example.
 *
 * <p>Provides both direct data-lookup commands (flights, hotels, activities, weather)
 * and an LLM-powered chat command that uses the full agent tool loop.
 */
@ShellComponent
public class TravelShellCommands {

    private final ObjectProvider<AgentRuntime> agentRuntimeProvider;
    private final SessionManager sessionManager;
    private final JaiClawProperties properties;
    private final TravelDataProvider provider;
    private final ObjectMapper objectMapper;
    private String currentSessionKey = "default";

    public TravelShellCommands(ObjectProvider<AgentRuntime> agentRuntimeProvider,
                               SessionManager sessionManager,
                               JaiClawProperties properties,
                               TravelDataProvider provider,
                               ObjectMapper objectMapper) {
        this.agentRuntimeProvider = agentRuntimeProvider;
        this.sessionManager = sessionManager;
        this.properties = properties;
        this.provider = provider;
        this.objectMapper = objectMapper;
    }

    // ---- Chat ----

    @ShellMethod(key = "chat", value = "Send a message to the travel planner agent")
    public String chat(@ShellOption(help = "Your message") String message) {
        AgentRuntime agentRuntime = agentRuntimeProvider.getIfAvailable();
        if (agentRuntime == null) {
            return "No LLM configured. Set ANTHROPIC_API_KEY, OPENAI_API_KEY, or enable Ollama.";
        }

        String agentId = properties.agent().defaultAgent();
        AgentIdentity identity = new AgentIdentity(
                agentId,
                properties.identity().name(),
                properties.identity().description());
        var session = sessionManager.getOrCreate(currentSessionKey, agentId);
        AgentRuntimeContext context = new AgentRuntimeContext(
                agentId, currentSessionKey, session, identity, ToolProfile.FULL, ".");

        try {
            var response = agentRuntime.run(message, context).join();
            return response.content();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @ShellMethod(key = {"plan-trip", "plan"}, value = "Plan a trip (shortcut for a structured chat message)")
    public String planTrip(
            @ShellOption(help = "Destination city") String destination,
            @ShellOption(defaultValue = "JFK", help = "Origin city or airport code") String origin,
            @ShellOption(help = "Departure date (YYYY-MM-DD)") String departure,
            @ShellOption(help = "Return date (YYYY-MM-DD)") String returnDate,
            @ShellOption(defaultValue = "2", help = "Number of travelers") int travelers,
            @ShellOption(defaultValue = "5000", help = "Budget in USD") double budget) {

        String message = "Plan a trip to %s for %d travelers from %s, departing %s, returning %s, budget $%.0f"
                .formatted(destination, travelers, origin, departure, returnDate, budget);
        return chat(message);
    }

    @ShellMethod(key = "new-session", value = "Start a new chat session")
    public String newSession() {
        sessionManager.reset(currentSessionKey);
        currentSessionKey = "session-" + System.currentTimeMillis();
        return "New session started: " + currentSessionKey;
    }

    // ---- Direct data lookup ----

    @ShellMethod(key = {"search-flights", "flights"}, value = "Search for flights (direct data lookup)")
    public String searchFlights(
            @ShellOption(help = "Destination city") String destination,
            @ShellOption(defaultValue = "JFK", help = "Origin airport code") String origin,
            @ShellOption(help = "Departure date (YYYY-MM-DD)") String departure,
            @ShellOption(help = "Return date (YYYY-MM-DD)") String returnDate,
            @ShellOption(defaultValue = "2", help = "Number of travelers") int travelers,
            @ShellOption(defaultValue = "5000", help = "Budget in USD") double budget) {
        try {
            FlightOptions result = provider.searchFlights(origin, destination,
                    departure, returnDate, travelers, budget);
            return formatJson(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @ShellMethod(key = {"search-hotels", "hotels"}, value = "Search for hotels (direct data lookup)")
    public String searchHotels(
            @ShellOption(help = "Destination city") String destination,
            @ShellOption(help = "Check-in date (YYYY-MM-DD)") String checkIn,
            @ShellOption(help = "Check-out date (YYYY-MM-DD)") String checkOut,
            @ShellOption(defaultValue = "2", help = "Number of guests") int guests,
            @ShellOption(defaultValue = "5000", help = "Budget in USD") double budget) {
        try {
            HotelOptions result = provider.searchHotels(destination, checkIn, checkOut, guests, budget);
            return formatJson(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @ShellMethod(key = {"search-activities", "activities"}, value = "Search for activities (direct data lookup)")
    public String searchActivities(
            @ShellOption(help = "Destination city") String destination,
            @ShellOption(help = "Start date (YYYY-MM-DD)") String startDate,
            @ShellOption(help = "End date (YYYY-MM-DD)") String endDate,
            @ShellOption(defaultValue = "2", help = "Number of travelers") int travelers,
            @ShellOption(defaultValue = "5000", help = "Budget in USD") double budget) {
        try {
            ActivityOptions result = provider.searchActivities(destination, startDate, endDate, travelers, budget);
            return formatJson(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @ShellMethod(key = "weather", value = "Get weather forecast (direct data lookup)")
    public String weather(
            @ShellOption(help = "Destination city") String destination,
            @ShellOption(help = "Start date (YYYY-MM-DD)") String startDate,
            @ShellOption(help = "End date (YYYY-MM-DD)") String endDate) {
        try {
            WeatherForecast result = provider.getWeather(destination, startDate, endDate);
            return formatJson(result);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String formatJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }
}

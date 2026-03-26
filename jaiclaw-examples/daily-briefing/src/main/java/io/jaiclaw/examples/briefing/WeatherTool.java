package io.jaiclaw.examples.briefing;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * Simulated weather tool. In production, replace with a real weather API call
 * (e.g., OpenWeatherMap, WeatherAPI).
 */
@Component
public class WeatherTool implements ToolCallback {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "get_weather",
                "Get the current weather for a city",
                "briefing",
                """
                {
                  "type": "object",
                  "properties": {
                    "city": { "type": "string", "description": "City name" }
                  },
                  "required": ["city"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String city = (String) parameters.get("city");
        // Simulated response — replace with real API call
        String weather = String.format(
                "{\"city\":\"%s\",\"date\":\"%s\",\"temp_f\":72,\"condition\":\"Partly Cloudy\","
                        + "\"humidity\":45,\"wind_mph\":8}",
                city, LocalDate.now()
        );
        return new ToolResult.Success(weather);
    }
}

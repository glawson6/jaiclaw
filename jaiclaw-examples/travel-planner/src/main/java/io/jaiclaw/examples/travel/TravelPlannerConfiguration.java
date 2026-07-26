package io.jaiclaw.examples.travel;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.examples.travel.api.StubTravelDataProvider;
import io.jaiclaw.examples.travel.api.TravelDataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composes all travel planner beans via explicit {@code @Bean} factory methods.
 *
 * <ul>
 *   <li>{@link TravelDataProvider} — {@link StubTravelDataProvider} with realistic
 *       hardcoded data for Tokyo, Paris, Cancun, and NYC. Swap in your own
 *       implementation to hit a real travel API.</li>
 *   <li>{@link TravelPlannerPlugin} — registers 4 tools for the JaiClaw tool loop</li>
 * </ul>
 */
@Configuration
public class TravelPlannerConfiguration {

    @Bean
    TravelDataProvider travelDataProvider() {
        return new StubTravelDataProvider();
    }

    @Bean
    TravelPlannerPlugin travelPlannerPlugin(TravelDataProvider provider, ObjectMapper objectMapper) {
        return new TravelPlannerPlugin(provider, objectMapper);
    }
}

package io.jaiclaw.examples.travel.api;

import io.jaiclaw.examples.travel.ActivityOptions;
import io.jaiclaw.examples.travel.ActivityOptions.Activity;
import io.jaiclaw.examples.travel.FlightOptions;
import io.jaiclaw.examples.travel.FlightOptions.FlightOffer;
import io.jaiclaw.examples.travel.HotelOptions;
import io.jaiclaw.examples.travel.HotelOptions.HotelOffer;
import io.jaiclaw.examples.travel.WeatherForecast;
import io.jaiclaw.examples.travel.WeatherForecast.DayForecast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Travel data provider returning realistic hardcoded data. The only
 * implementation the example ships with — swap in your own
 * {@link TravelDataProvider} bean to hit a real travel API.
 *
 * <p>Supports 4 popular destinations with tailored data (Tokyo, Paris, Cancun, NYC).
 * Unknown destinations receive generic data with adjusted prices.
 */
public class StubTravelDataProvider implements TravelDataProvider {

    private static final Logger log = LoggerFactory.getLogger(StubTravelDataProvider.class);

    // --- Flight data by destination ---

    private static final Map<String, List<FlightOffer>> FLIGHT_DATA = Map.of(
            "tokyo", List.of(
                    new FlightOffer("ANA", "NH 109", "JFK", "NRT",
                            "08:30", "11:45+1", 1250.00, 0),
                    new FlightOffer("United", "UA 79", "JFK", "NRT",
                            "11:00", "15:20+1", 980.00, 1),
                    new FlightOffer("JAL", "JL 5", "JFK", "HND",
                            "13:15", "16:30+1", 2100.00, 0)
            ),
            "paris", List.of(
                    new FlightOffer("Air France", "AF 9", "JFK", "CDG",
                            "19:00", "08:45+1", 850.00, 0),
                    new FlightOffer("Delta", "DL 264", "JFK", "CDG",
                            "22:30", "12:15+1", 720.00, 1),
                    new FlightOffer("Air France", "AF 17", "JFK", "CDG",
                            "17:30", "07:15+1", 1650.00, 0)
            ),
            "cancun", List.of(
                    new FlightOffer("JetBlue", "B6 1416", "JFK", "CUN",
                            "06:00", "10:15", 380.00, 0),
                    new FlightOffer("American", "AA 1297", "JFK", "CUN",
                            "09:30", "15:00", 320.00, 1),
                    new FlightOffer("Delta", "DL 519", "JFK", "CUN",
                            "07:45", "11:55", 580.00, 0)
            ),
            "new york", List.of(
                    new FlightOffer("Southwest", "WN 302", "LAX", "JFK",
                            "06:00", "14:30", 280.00, 0),
                    new FlightOffer("United", "UA 1598", "ORD", "JFK",
                            "08:15", "11:45", 220.00, 0),
                    new FlightOffer("Delta", "DL 400", "LAX", "JFK",
                            "09:00", "17:15", 450.00, 0)
            )
    );

    // --- Hotel data by destination ---

    private static final Map<String, List<HotelOffer>> HOTEL_DATA = Map.of(
            "tokyo", List.of(
                    new HotelOffer("Sakura Budget Inn", 2.5, 65.00,
                            "Asakusa, Taito-ku", 7.8),
                    new HotelOffer("Hotel Gracery Shinjuku", 4.0, 150.00,
                            "Shinjuku, Tokyo", 8.5),
                    new HotelOffer("The Peninsula Tokyo", 5.0, 550.00,
                            "Marunouchi, Chiyoda-ku", 9.4)
            ),
            "paris", List.of(
                    new HotelOffer("Hotel du Marais", 2.0, 85.00,
                            "Le Marais, 3rd Arr.", 7.5),
                    new HotelOffer("Hotel Monge", 4.0, 220.00,
                            "Latin Quarter, 5th Arr.", 8.9),
                    new HotelOffer("Le Bristol Paris", 5.0, 750.00,
                            "Rue du Faubourg Saint-Honore, 8th Arr.", 9.6)
            ),
            "cancun", List.of(
                    new HotelOffer("Hostal MX Cancun", 2.0, 45.00,
                            "Downtown Cancun", 7.2),
                    new HotelOffer("Hyatt Ziva Cancun", 4.5, 280.00,
                            "Hotel Zone, Blvd Kukulcan", 8.7),
                    new HotelOffer("Ritz-Carlton Cancun", 5.0, 480.00,
                            "Hotel Zone, Retorno del Rey", 9.3)
            ),
            "new york", List.of(
                    new HotelOffer("Pod 51", 3.0, 120.00,
                            "Midtown East, Manhattan", 7.6),
                    new HotelOffer("The Knickerbocker", 4.5, 320.00,
                            "Times Square, Manhattan", 8.8),
                    new HotelOffer("The Plaza", 5.0, 650.00,
                            "Fifth Avenue, Central Park South", 9.5)
            )
    );

    // --- Activity data by destination ---

    private static final Map<String, List<Activity>> ACTIVITY_DATA = Map.of(
            "tokyo", List.of(
                    new Activity("Senso-ji Temple & Asakusa Walking Tour", "cultural",
                            "Explore Tokyo's oldest temple and the historic Nakamise shopping street",
                            25.00, "3 hours"),
                    new Activity("Tsukiji Outer Market Food Tour", "food",
                            "Sample fresh sushi, tamagoyaki, and street food with a local guide",
                            85.00, "3 hours"),
                    new Activity("Mount Takao Day Hike", "outdoor",
                            "Scenic hike to the summit with panoramic views of Mt. Fuji on clear days",
                            15.00, "half day"),
                    new Activity("Shibuya & Shinjuku Nightlife Tour", "nightlife",
                            "Bar-hopping through Golden Gai and izakaya alleys",
                            60.00, "4 hours"),
                    new Activity("TeamLab Borderless Digital Art Museum", "cultural",
                            "Immersive digital art experience in Odaiba",
                            30.00, "2 hours")
            ),
            "paris", List.of(
                    new Activity("Louvre Museum Guided Tour", "cultural",
                            "Skip-the-line tour covering Mona Lisa, Venus de Milo, and more",
                            65.00, "3 hours"),
                    new Activity("Seine River Dinner Cruise", "food",
                            "Three-course French dinner while cruising past illuminated landmarks",
                            110.00, "2.5 hours"),
                    new Activity("Versailles Palace & Gardens Day Trip", "cultural",
                            "Full day at the palace with audio guide and garden access",
                            45.00, "full day"),
                    new Activity("Montmartre Wine & Cheese Tasting", "food",
                            "Visit local fromageries and wine caves in the artistic quarter",
                            75.00, "2 hours")
            ),
            "cancun", List.of(
                    new Activity("Chichen Itza Day Trip", "cultural",
                            "Visit the ancient Mayan wonder with lunch and cenote swim",
                            90.00, "full day"),
                    new Activity("Snorkeling at Isla Mujeres", "outdoor",
                            "Catamaran trip with snorkeling at MUSA underwater sculpture park",
                            65.00, "half day"),
                    new Activity("Taco & Mezcal Street Food Tour", "food",
                            "Downtown Cancun food crawl with local guide",
                            50.00, "3 hours"),
                    new Activity("Xcaret Eco-Park", "outdoor",
                            "All-inclusive park with underground rivers, wildlife, and cultural shows",
                            120.00, "full day"),
                    new Activity("Coco Bongo Night Show", "nightlife",
                            "World-famous acrobatics, music, and dance show",
                            80.00, "4 hours")
            ),
            "new york", List.of(
                    new Activity("Statue of Liberty & Ellis Island", "cultural",
                            "Ferry ride with pedestal access and audio tour",
                            25.00, "half day"),
                    new Activity("Broadway Show (discount TKTS)", "cultural",
                            "Same-day discounted tickets to a top Broadway production",
                            90.00, "3 hours"),
                    new Activity("Central Park Bike Tour", "outdoor",
                            "Guided cycling tour through Central Park highlights",
                            45.00, "2 hours"),
                    new Activity("Greenwich Village Food Walk", "food",
                            "Pizza, bagels, and craft cocktails in the Village",
                            70.00, "3 hours")
            )
    );

    // --- Weather profiles by destination (average ranges) ---

    private record WeatherProfile(String[] conditions, double highMin, double highMax,
                                   double lowMin, double lowMax, int precipMin, int precipMax) {}

    private static final Map<String, WeatherProfile> WEATHER_PROFILES = Map.of(
            "tokyo", new WeatherProfile(
                    new String[]{"partly cloudy", "sunny", "overcast", "light rain"},
                    18.0, 28.0, 10.0, 18.0, 10, 40),
            "paris", new WeatherProfile(
                    new String[]{"partly cloudy", "sunny", "overcast", "light rain"},
                    14.0, 24.0, 6.0, 14.0, 15, 45),
            "cancun", new WeatherProfile(
                    new String[]{"sunny", "partly cloudy", "sunny", "scattered showers"},
                    28.0, 34.0, 22.0, 26.0, 5, 30),
            "new york", new WeatherProfile(
                    new String[]{"partly cloudy", "sunny", "overcast", "rain"},
                    12.0, 26.0, 4.0, 16.0, 15, 50)
    );

    private static final WeatherProfile DEFAULT_WEATHER = new WeatherProfile(
            new String[]{"partly cloudy", "sunny", "overcast"},
            16.0, 26.0, 8.0, 16.0, 10, 35);

    @Override
    public FlightOptions searchFlights(String origin, String destination,
                                        String departureDate, String returnDate,
                                        int travelers, double budget) {
        log.info("[STUB] Searching flights: {} → {} ({} to {}, {} travelers, budget ${})",
                origin, destination, departureDate, returnDate, travelers, budget);

        String key = destination.toLowerCase().trim();
        List<FlightOffer> offers = FLIGHT_DATA.getOrDefault(key, buildGenericFlights(origin, destination));

        // Adjust origin airport if provided and different from default
        if (origin != null && !origin.equalsIgnoreCase("JFK")) {
            offers = offers.stream()
                    .map(o -> new FlightOffer(o.airline(), o.flightNumber(),
                            origin.toUpperCase(), o.arrivalAirport(),
                            o.departureTime(), o.arrivalTime(),
                            o.pricePerPerson(), o.stops()))
                    .toList();
        }

        FlightOffer best = offers.stream()
                .min((a, b) -> {
                    // Prefer direct flights, then by price
                    if (a.stops() != b.stops()) return Integer.compare(a.stops(), b.stops());
                    return Double.compare(a.pricePerPerson(), b.pricePerPerson());
                })
                .orElse(offers.getFirst());

        return new FlightOptions(offers, formatFlightSummary(best), best.pricePerPerson());
    }

    @Override
    public HotelOptions searchHotels(String destination, String checkIn, String checkOut,
                                      int guests, double budget) {
        log.info("[STUB] Searching hotels: {} ({} to {}, {} guests, budget ${})",
                destination, checkIn, checkOut, guests, budget);

        String key = destination.toLowerCase().trim();
        List<HotelOffer> offers = HOTEL_DATA.getOrDefault(key, buildGenericHotels(destination));

        HotelOffer best = offers.stream()
                .filter(h -> h.pricePerNight() <= budget / 7.0) // rough per-night budget
                .max((a, b) -> Double.compare(a.reviewScore(), b.reviewScore()))
                .orElse(offers.get(1)); // default to mid-range

        return new HotelOptions(offers, best.name() + " (" + best.stars() + " stars, $"
                + best.pricePerNight() + "/night)", best.pricePerNight());
    }

    @Override
    public ActivityOptions searchActivities(String destination, String startDate, String endDate,
                                             int travelers, double budget) {
        log.info("[STUB] Searching activities: {} ({} to {}, {} travelers, budget ${})",
                destination, startDate, endDate, travelers, budget);

        String key = destination.toLowerCase().trim();
        List<Activity> activities = ACTIVITY_DATA.getOrDefault(key, buildGenericActivities(destination));

        double totalCost = activities.stream().mapToDouble(Activity::price).sum();
        return new ActivityOptions(activities, totalCost);
    }

    @Override
    public WeatherForecast getWeather(String destination, String startDate, String endDate) {
        log.info("[STUB] Getting weather: {} ({} to {})", destination, startDate, endDate);

        String key = destination.toLowerCase().trim();
        WeatherProfile profile = WEATHER_PROFILES.getOrDefault(key, DEFAULT_WEATHER);

        List<DayForecast> days = new ArrayList<>();
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        int dayIndex = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String condition = profile.conditions()[dayIndex % profile.conditions().length];
            double highRange = profile.highMax() - profile.highMin();
            double lowRange = profile.lowMax() - profile.lowMin();
            // Deterministic variation based on day index
            double highVariation = Math.sin(dayIndex * 1.3) * 0.5 + 0.5;
            double lowVariation = Math.cos(dayIndex * 0.9) * 0.5 + 0.5;

            double high = Math.round((profile.highMin() + highRange * highVariation) * 10.0) / 10.0;
            double low = Math.round((profile.lowMin() + lowRange * lowVariation) * 10.0) / 10.0;
            int precip = (int) (profile.precipMin()
                    + (profile.precipMax() - profile.precipMin()) * (condition.contains("rain") || condition.contains("shower") ? 0.8 : 0.2));

            days.add(new DayForecast(date.toString(), condition, high, low, precip));
            dayIndex++;
        }

        String packingAdvice = buildPackingAdvice(profile, destination);
        return new WeatherForecast(destination, days, packingAdvice);
    }

    // --- Helpers ---

    private static String formatFlightSummary(FlightOffer offer) {
        String stops = offer.stops() == 0 ? "direct" : offer.stops() + " stop(s)";
        return "%s %s (%s→%s, %s, $%.0f/person)".formatted(
                offer.airline(), offer.flightNumber(),
                offer.departureAirport(), offer.arrivalAirport(),
                stops, offer.pricePerPerson());
    }

    private static List<FlightOffer> buildGenericFlights(String origin, String destination) {
        String dep = origin != null ? origin.toUpperCase() : "JFK";
        String arr = destination.substring(0, Math.min(3, destination.length())).toUpperCase();
        return List.of(
                new FlightOffer("United", "UA 100", dep, arr,
                        "09:00", "15:30", 650.00, 0),
                new FlightOffer("American", "AA 200", dep, arr,
                        "12:00", "20:15", 520.00, 1),
                new FlightOffer("Delta", "DL 300", dep, arr,
                        "16:00", "22:00", 1100.00, 0)
        );
    }

    private static List<HotelOffer> buildGenericHotels(String destination) {
        return List.of(
                new HotelOffer("Budget Inn " + destination, 2.0, 70.00,
                        "City Center", 7.0),
                new HotelOffer("Comfort Hotel " + destination, 3.5, 160.00,
                        "Downtown", 8.2),
                new HotelOffer("Grand " + destination + " Hotel", 5.0, 400.00,
                        "Premium District", 9.1)
        );
    }

    private static List<Activity> buildGenericActivities(String destination) {
        return List.of(
                new Activity("City Walking Tour", "cultural",
                        "Guided tour of " + destination + "'s top landmarks", 30.00, "3 hours"),
                new Activity("Local Food Tasting", "food",
                        "Sample regional cuisine with a local guide", 55.00, "2.5 hours"),
                new Activity("Nature Excursion", "outdoor",
                        "Day trip to nearby natural attractions", 45.00, "half day"),
                new Activity("Evening Entertainment", "nightlife",
                        "Experience the local nightlife scene", 40.00, "3 hours")
        );
    }

    private static String buildPackingAdvice(WeatherProfile profile, String destination) {
        double avgHigh = (profile.highMin() + profile.highMax()) / 2;
        double avgLow = (profile.lowMin() + profile.lowMax()) / 2;
        int avgPrecip = (profile.precipMin() + profile.precipMax()) / 2;

        StringBuilder advice = new StringBuilder();
        if (avgHigh > 28) {
            advice.append("Light, breathable clothing. Sunscreen and sunglasses essential. ");
        } else if (avgHigh > 20) {
            advice.append("Light layers for daytime, a jacket for evenings. ");
        } else {
            advice.append("Warm layers including a coat. ");
        }

        if (avgLow < 10) {
            advice.append("Pack warm sleepwear and a scarf. ");
        }

        if (avgPrecip > 30) {
            advice.append("Rain jacket or umbrella strongly recommended. ");
        } else if (avgPrecip > 15) {
            advice.append("A compact umbrella is advisable. ");
        }

        advice.append("Comfortable walking shoes for exploring ").append(destination).append(".");
        return advice.toString();
    }
}

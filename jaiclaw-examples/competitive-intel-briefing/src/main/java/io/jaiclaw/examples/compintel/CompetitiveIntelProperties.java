package io.jaiclaw.examples.compintel;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configurable competitor list for the briefing pipeline.
 *
 * <pre>
 * jaiclaw:
 *   competitive:
 *     competitors:
 *       - Acme
 *       - Globex
 *       - Initech
 * </pre>
 */
@ConfigurationProperties(prefix = "jaiclaw.competitive")
public record CompetitiveIntelProperties(List<String> competitors) {

    public static final List<String> DEFAULTS = List.of("Acme", "Globex", "Initech");

    public CompetitiveIntelProperties {
        if (competitors == null || competitors.isEmpty()) {
            competitors = DEFAULTS;
        } else {
            competitors = List.copyOf(competitors);
        }
    }
}

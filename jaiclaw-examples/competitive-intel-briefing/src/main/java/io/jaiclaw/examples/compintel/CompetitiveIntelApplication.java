package io.jaiclaw.examples.compintel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Competitive-intel briefing pipeline. Runs on a quartz cron schedule
 * (`0 0 7 ? * MON-FRI` by default) and produces a markdown briefing in
 * {@code ~/.jaiclaw/competitive-intel/briefings/<yyyy-MM-dd>.md}.
 *
 * <p>Drive it on-demand from the Spring Shell with {@code run-now}.
 */
@SpringBootApplication
@EnableConfigurationProperties(CompetitiveIntelProperties.class)
public class CompetitiveIntelApplication {
    public static void main(String[] args) {
        SpringApplication.run(CompetitiveIntelApplication.class, args);
    }
}

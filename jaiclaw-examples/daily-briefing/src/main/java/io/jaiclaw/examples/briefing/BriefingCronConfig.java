package io.jaiclaw.examples.briefing;

import io.jaiclaw.core.model.CronJob;
import io.jaiclaw.cron.CronService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a cron job that generates a daily briefing at 7 AM on weekdays.
 * The agent uses WeatherTool and NewsTool to gather data, then delivers
 * the digest via configured channels (Telegram, Email).
 */
@Configuration
public class BriefingCronConfig {

    private static final Logger log = LoggerFactory.getLogger(BriefingCronConfig.class);

    @Bean
    ApplicationRunner registerBriefingJob(CronService cronService) {
        return args -> {
            CronJob job = new CronJob(
                    "daily-briefing",
                    "Daily Morning Briefing",
                    "default",
                    "0 7 * * MON-FRI",
                    "America/New_York",
                    "Generate a morning briefing: get the current weather for New York, "
                            + "then fetch the top 5 tech news headlines. "
                            + "Format as a concise digest with weather first, then news.",
                    "telegram",
                    null,
                    true,
                    null,
                    null
            );
            cronService.addJob(job);
            log.info("Registered daily-briefing cron job (7 AM ET, weekdays)");
        };
    }
}

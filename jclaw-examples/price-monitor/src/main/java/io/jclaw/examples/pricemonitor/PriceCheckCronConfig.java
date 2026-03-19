package io.jclaw.examples.pricemonitor;

import io.jclaw.core.model.CronJob;
import io.jclaw.cron.CronService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a cron job that checks product prices every hour.
 * When a price drops below threshold, the agent sends an SMS alert.
 */
@Configuration
public class PriceCheckCronConfig {

    private static final Logger log = LoggerFactory.getLogger(PriceCheckCronConfig.class);

    @Bean
    ApplicationRunner registerPriceCheckJob(CronService cronService) {
        return args -> {
            CronJob job = new CronJob(
                    "price-check",
                    "Hourly Price Check",
                    "default",
                    "0 * * * *",
                    "UTC",
                    "Check the current price for each monitored product using the check_price tool. "
                            + "Compare against the target prices. If any product is at or below its "
                            + "target price, send an SMS alert with the product name, current price, "
                            + "and the savings amount.",
                    "sms",
                    null,
                    true,
                    null,
                    null
            );
            cronService.addJob(job);
            log.info("Registered price-check cron job (every hour)");
        };
    }
}

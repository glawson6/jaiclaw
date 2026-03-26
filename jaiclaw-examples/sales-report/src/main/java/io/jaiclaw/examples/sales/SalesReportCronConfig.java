package io.jaiclaw.examples.sales;

import io.jaiclaw.core.model.CronJob;
import io.jaiclaw.cron.CronService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a cron job that generates a weekly sales report every Monday at 9 AM.
 * The agent uses SalesFetchTool to pull data and the canvas to render an HTML dashboard.
 */
@Configuration
public class SalesReportCronConfig {

    private static final Logger log = LoggerFactory.getLogger(SalesReportCronConfig.class);

    @Bean
    ApplicationRunner registerSalesReportJob(CronService cronService) {
        return args -> {
            CronJob job = new CronJob(
                    "weekly-sales-report",
                    "Weekly Sales Report",
                    "default",
                    "0 9 * * MON",
                    "America/New_York",
                    "Fetch the latest sales data using the get_sales_data tool, "
                            + "then generate a sales report with a summary table and key metrics. "
                            + "Use the canvas to render it as an HTML dashboard.",
                    null,
                    null,
                    true,
                    null,
                    null
            );
            cronService.addJob(job);
            log.info("Registered weekly-sales-report cron job (Monday 9 AM ET)");
        };
    }
}

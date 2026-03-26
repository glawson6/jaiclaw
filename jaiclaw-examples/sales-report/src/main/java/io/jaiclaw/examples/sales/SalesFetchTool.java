package io.jaiclaw.examples.sales;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated sales data tool. In production, replace with a real CRM/database query.
 */
@Component
public class SalesFetchTool implements ToolCallback {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "get_sales_data",
                "Fetch sales data for a given period",
                "sales",
                """
                {
                  "type": "object",
                  "properties": {
                    "period": { "type": "string", "description": "Period: 'week', 'month', or 'quarter'" },
                    "region": { "type": "string", "description": "Sales region (optional)" }
                  },
                  "required": ["period"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String period = (String) parameters.get("period");
        String region = (String) parameters.getOrDefault("region", "all");
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        String data = String.format("""
                {
                  "period": "%s",
                  "region": "%s",
                  "end_date": "%s",
                  "total_revenue": %d,
                  "total_deals": %d,
                  "avg_deal_size": %d,
                  "top_products": [
                    {"name": "Enterprise Plan", "revenue": %d, "units": %d},
                    {"name": "Pro Plan", "revenue": %d, "units": %d},
                    {"name": "Starter Plan", "revenue": %d, "units": %d}
                  ],
                  "conversion_rate": %.1f
                }""",
                period, region, LocalDate.now(),
                rng.nextInt(100000, 500000),
                rng.nextInt(20, 80),
                rng.nextInt(2000, 10000),
                rng.nextInt(50000, 200000), rng.nextInt(5, 20),
                rng.nextInt(20000, 80000), rng.nextInt(10, 40),
                rng.nextInt(5000, 30000), rng.nextInt(20, 100),
                rng.nextDouble(5.0, 25.0)
        );
        return new ToolResult.Success(data);
    }
}

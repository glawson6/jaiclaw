package io.jaiclaw.examples.pricemonitor;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulated price checking tool. In production, this would use
 * BrowserService (Playwright) to scrape actual product pages.
 */
@Component
public class PriceCheckTool implements ToolCallback {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "check_price",
                "Check the current price of a product on a given URL",
                "price-monitor",
                """
                {
                  "type": "object",
                  "properties": {
                    "product_name": { "type": "string", "description": "Product name" },
                    "url": { "type": "string", "description": "Product page URL" },
                    "target_price": { "type": "number", "description": "Target price to alert on" }
                  },
                  "required": ["product_name", "url"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String productName = (String) parameters.get("product_name");
        String url = (String) parameters.get("url");
        Number targetPrice = (Number) parameters.get("target_price");

        // Simulated — in production, use BrowserService to scrape the URL
        double currentPrice = ThreadLocalRandom.current().nextDouble(50.0, 500.0);
        currentPrice = Math.round(currentPrice * 100.0) / 100.0;

        boolean belowTarget = targetPrice != null && currentPrice <= targetPrice.doubleValue();

        String result = String.format("""
                {
                  "product": "%s",
                  "url": "%s",
                  "current_price": %.2f,
                  "target_price": %s,
                  "below_target": %s,
                  "currency": "USD"
                }""",
                productName, url, currentPrice,
                targetPrice != null ? String.format("%.2f", targetPrice.doubleValue()) : "null",
                belowTarget
        );
        return new ToolResult.Success(result);
    }
}

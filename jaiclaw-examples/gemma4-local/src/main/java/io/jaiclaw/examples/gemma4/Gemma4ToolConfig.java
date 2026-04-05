package io.jaiclaw.examples.gemma4;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Registers lightweight tools to demonstrate Gemma 4's native function calling.
 * Gemma 4 12B+ variants support structured tool use via Ollama.
 */
@Configuration(proxyBeanMethods = false)
class Gemma4ToolConfig {

    private static final Logger log = LoggerFactory.getLogger(Gemma4ToolConfig.class);

    @Bean
    @ConditionalOnBean(ToolRegistry.class)
    ApplicationRunner gemma4ToolRegistrar(ToolRegistry registry) {
        return args -> {
            registry.register(new CurrentTimeTool());
            registry.register(new CalculateTool());
            log.info("Registered Gemma 4 demo tools: current_time, calculate");
        };
    }

    static class CurrentTimeTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "current_time",
                    "Returns the current date and time in the specified timezone",
                    "gemma4",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "timezone": { "type": "string", "description": "IANA timezone (e.g., America/New_York, UTC)" }
                      },
                      "required": ["timezone"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String timezone = (String) parameters.getOrDefault("timezone", "UTC");
            try {
                ZoneId zone = ZoneId.of(timezone);
                String now = LocalDateTime.now(zone)
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                return new ToolResult.Success("Current time in %s: %s".formatted(timezone, now));
            } catch (Exception e) {
                return new ToolResult.Error("Invalid timezone: " + timezone + ". Use IANA format (e.g., America/New_York).");
            }
        }
    }

    static class CalculateTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "calculate",
                    "Evaluates a simple arithmetic expression (supports +, -, *, /, parentheses)",
                    "gemma4",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "expression": { "type": "string", "description": "Arithmetic expression to evaluate (e.g., 2 + 3 * 4)" }
                      },
                      "required": ["expression"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String expression = (String) parameters.getOrDefault("expression", "");
            try {
                double result = evaluateExpression(expression);
                return new ToolResult.Success("%s = %s".formatted(expression, formatNumber(result)));
            } catch (Exception e) {
                return new ToolResult.Error("Could not evaluate: " + expression);
            }
        }

        private static double evaluateExpression(String input) {
            final String expr = input.replaceAll("\\s+", "");
            return new Object() {
                int pos = -1;
                int ch;

                void nextChar() { ch = (++pos < expr.length()) ? expr.charAt(pos) : -1; }
                boolean eat(int c) { if (ch == c) { nextChar(); return true; } return false; }

                double parse() { nextChar(); double x = parseExpr(); return x; }

                double parseExpr() {
                    double x = parseTerm();
                    while (true) {
                        if (eat('+')) x += parseTerm();
                        else if (eat('-')) x -= parseTerm();
                        else return x;
                    }
                }

                double parseTerm() {
                    double x = parseFactor();
                    while (true) {
                        if (eat('*')) x *= parseFactor();
                        else if (eat('/')) x /= parseFactor();
                        else return x;
                    }
                }

                double parseFactor() {
                    if (eat('-')) return -parseFactor();
                    double x;
                    int start = pos;
                    if (eat('(')) { x = parseExpr(); eat(')'); }
                    else if ((ch >= '0' && ch <= '9') || ch == '.') {
                        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                        x = Double.parseDouble(expr.substring(start, pos));
                    } else {
                        throw new RuntimeException("Unexpected: " + (char) ch);
                    }
                    return x;
                }
            }.parse();
        }

        private static String formatNumber(double d) {
            return d == (long) d ? String.valueOf((long) d) : String.valueOf(d);
        }
    }
}

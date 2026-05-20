package io.jaiclaw.examples.tax;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.rules.tax.facts.FilingStatus;
import io.jaiclaw.tools.ToolCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Component
public class TaxComparisonTool implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(TaxComparisonTool.class);

    private final TaxCalculationTool taxCalculationTool;

    public TaxComparisonTool(TaxCalculationTool taxCalculationTool) {
        this.taxCalculationTool = taxCalculationTool;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "compare_tax",
                "Compare two tax scenarios side by side. Useful for 'what if' questions like comparing filing statuses or income levels.",
                ToolCatalog.SECTION_CUSTOM,
                """
                {
                  "type": "object",
                  "properties": {
                    "scenarioA": {
                      "type": "object",
                      "description": "First scenario",
                      "properties": {
                        "label": { "type": "string", "description": "Label for this scenario (e.g., 'Single filing')" },
                        "filingStatus": { "type": "string", "enum": ["SINGLE", "MARRIED_FILING_JOINTLY", "MARRIED_FILING_SEPARATELY", "HEAD_OF_HOUSEHOLD"] },
                        "grossIncome": { "type": "number" },
                        "earnedIncome": { "type": "number" },
                        "numberOfQualifyingChildren": { "type": "integer" },
                        "age": { "type": "integer" },
                        "itemizedDeductions": { "type": "number" }
                      },
                      "required": ["filingStatus", "grossIncome"]
                    },
                    "scenarioB": {
                      "type": "object",
                      "description": "Second scenario",
                      "properties": {
                        "label": { "type": "string", "description": "Label for this scenario (e.g., 'Married filing jointly')" },
                        "filingStatus": { "type": "string", "enum": ["SINGLE", "MARRIED_FILING_JOINTLY", "MARRIED_FILING_SEPARATELY", "HEAD_OF_HOUSEHOLD"] },
                        "grossIncome": { "type": "number" },
                        "earnedIncome": { "type": "number" },
                        "numberOfQualifyingChildren": { "type": "integer" },
                        "age": { "type": "integer" },
                        "itemizedDeductions": { "type": "number" }
                      },
                      "required": ["filingStatus", "grossIncome"]
                    }
                  },
                  "required": ["scenarioA", "scenarioB"]
                }
                """
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        try {
            Map<String, Object> scenarioA = (Map<String, Object>) parameters.get("scenarioA");
            Map<String, Object> scenarioB = (Map<String, Object>) parameters.get("scenarioB");

            String labelA = (String) scenarioA.getOrDefault("label", "Scenario A");
            String labelB = (String) scenarioB.getOrDefault("label", "Scenario B");

            String resultA = runScenario(scenarioA);
            String resultB = runScenario(scenarioB);

            return new ToolResult.Success(String.format(
                    "{\"comparison\":{\"scenarioA\":{\"label\":\"%s\",\"result\":%s},\"scenarioB\":{\"label\":\"%s\",\"result\":%s}}}",
                    labelA, resultA, labelB, resultB
            ));

        } catch (Exception e) {
            log.error("Tax comparison error", e);
            return new ToolResult.Error("Tax comparison failed: " + e.getMessage());
        }
    }

    private String runScenario(Map<String, Object> scenario) {
        FilingStatus filingStatus = FilingStatus.valueOf((String) scenario.get("filingStatus"));
        BigDecimal grossIncome = toBigDecimal(scenario.get("grossIncome"));
        BigDecimal earnedIncome = scenario.containsKey("earnedIncome")
                ? toBigDecimal(scenario.get("earnedIncome")) : grossIncome;
        int qualifyingChildren = scenario.containsKey("numberOfQualifyingChildren")
                ? ((Number) scenario.get("numberOfQualifyingChildren")).intValue() : 0;
        int age = scenario.containsKey("age")
                ? ((Number) scenario.get("age")).intValue() : 35;
        BigDecimal itemizedDeductions = scenario.containsKey("itemizedDeductions")
                ? toBigDecimal(scenario.get("itemizedDeductions")) : BigDecimal.ZERO;

        return taxCalculationTool.executeTaxRules(filingStatus, grossIncome, earnedIncome,
                qualifyingChildren, age, itemizedDeductions);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}

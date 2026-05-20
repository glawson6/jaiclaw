package io.jaiclaw.examples.tax;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.rules.tax.facts.*;
import io.jaiclaw.tools.ToolCatalog;
import org.kie.api.runtime.StatelessKieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class TaxCalculationTool implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(TaxCalculationTool.class);

    private final StatelessKieSession taxKieSession;

    public TaxCalculationTool(@Qualifier("taxKieSession") StatelessKieSession taxKieSession) {
        this.taxKieSession = taxKieSession;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "calculate_tax",
                "Calculate federal income tax using IRS 2026 rules. Returns standard deduction, bracket breakdown, credits, and total tax. NEVER calculate taxes yourself — always use this tool for accuracy.",
                ToolCatalog.SECTION_CUSTOM,
                """
                {
                  "type": "object",
                  "properties": {
                    "filingStatus": { "type": "string", "description": "Filing status: SINGLE, MARRIED_FILING_JOINTLY, MARRIED_FILING_SEPARATELY, HEAD_OF_HOUSEHOLD", "enum": ["SINGLE", "MARRIED_FILING_JOINTLY", "MARRIED_FILING_SEPARATELY", "HEAD_OF_HOUSEHOLD"] },
                    "grossIncome": { "type": "number", "description": "Total gross income in USD" },
                    "earnedIncome": { "type": "number", "description": "Earned income (wages, salary). Defaults to grossIncome if not specified." },
                    "numberOfQualifyingChildren": { "type": "integer", "description": "Number of qualifying children under 17 (default: 0)" },
                    "age": { "type": "integer", "description": "Taxpayer age (default: 35)" },
                    "itemizedDeductions": { "type": "number", "description": "Total itemized deductions. If less than standard deduction, standard is used. (default: 0)" }
                  },
                  "required": ["filingStatus", "grossIncome"]
                }
                """
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        try {
            FilingStatus filingStatus = FilingStatus.valueOf((String) parameters.get("filingStatus"));
            BigDecimal grossIncome = toBigDecimal(parameters.get("grossIncome"));
            BigDecimal earnedIncome = parameters.containsKey("earnedIncome")
                    ? toBigDecimal(parameters.get("earnedIncome")) : grossIncome;
            int qualifyingChildren = parameters.containsKey("numberOfQualifyingChildren")
                    ? ((Number) parameters.get("numberOfQualifyingChildren")).intValue() : 0;
            int age = parameters.containsKey("age")
                    ? ((Number) parameters.get("age")).intValue() : 35;
            BigDecimal itemizedDeductions = parameters.containsKey("itemizedDeductions")
                    ? toBigDecimal(parameters.get("itemizedDeductions")) : BigDecimal.ZERO;

            String result = executeTaxRules(filingStatus, grossIncome, earnedIncome,
                    qualifyingChildren, age, itemizedDeductions);
            return new ToolResult.Success(result);

        } catch (IllegalArgumentException e) {
            return new ToolResult.Error("Invalid input: " + e.getMessage());
        } catch (Exception e) {
            log.error("Tax calculation error", e);
            return new ToolResult.Error("Tax calculation failed: " + e.getMessage());
        }
    }

    String executeTaxRules(FilingStatus filingStatus, BigDecimal grossIncome,
                           BigDecimal earnedIncome, int qualifyingChildren,
                           int age, BigDecimal itemizedDeductions) {
        String taxpayerId = UUID.randomUUID().toString().substring(0, 8);

        // Build TaxPayer fact
        TaxPayer taxpayer = new TaxPayer(taxpayerId, filingStatus);
        taxpayer.setGrossIncome(grossIncome);
        taxpayer.setAdjustedGrossIncome(grossIncome);
        taxpayer.setModifiedAdjustedGrossIncome(grossIncome);
        taxpayer.setEarnedIncome(earnedIncome);
        taxpayer.setNumberOfQualifyingChildren(qualifyingChildren);
        taxpayer.setNumberOfDependents(qualifyingChildren);
        taxpayer.setAge(age);
        taxpayer.setItemizedDeductions(itemizedDeductions);
        taxpayer.setUseStandardDeduction(itemizedDeductions.compareTo(BigDecimal.ZERO) == 0);
        taxpayer.setAgedOrBlind(age >= 65);

        // Build TaxReturn fact
        TaxReturn taxReturn = new TaxReturn("RET-" + taxpayerId, taxpayer);
        taxReturn.setItemizedDeductions(itemizedDeductions);

        // Build credit facts
        ChildTaxCredit childTaxCredit = new ChildTaxCredit("CTC-" + taxpayerId, qualifyingChildren);

        EarnedIncomeCredit earnedIncomeCredit = new EarnedIncomeCredit("EIC-" + taxpayerId, qualifyingChildren, filingStatus);
        earnedIncomeCredit.setEarnedIncome(earnedIncome);
        earnedIncomeCredit.setAdjustedGrossIncome(grossIncome);

        AlternativeMinimumTax amt = new AlternativeMinimumTax("AMT-" + taxpayerId, filingStatus);

        // Fire rules
        List<Object> facts = new ArrayList<>();
        facts.add(taxpayer);
        facts.add(taxReturn);
        facts.add(childTaxCredit);
        facts.add(earnedIncomeCredit);
        facts.add(amt);

        taxKieSession.execute(facts);

        // Format results
        return formatResults(taxpayer, taxReturn, childTaxCredit, earnedIncomeCredit, amt);
    }

    private String formatResults(TaxPayer taxpayer, TaxReturn taxReturn,
                                 ChildTaxCredit childTaxCredit, EarnedIncomeCredit earnedIncomeCredit,
                                 AlternativeMinimumTax amt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"filingStatus\":\"").append(taxpayer.getFilingStatus().getDisplayName()).append("\",");
        sb.append("\"grossIncome\":").append(taxpayer.getGrossIncome()).append(",");
        sb.append("\"standardDeduction\":").append(taxReturn.getStandardDeduction()).append(",");
        sb.append("\"itemizedDeductions\":").append(taxReturn.getItemizedDeductions()).append(",");
        sb.append("\"deductionUsed\":\"").append(
                taxReturn.getStandardDeduction().compareTo(taxReturn.getItemizedDeductions()) >= 0
                        ? "standard" : "itemized").append("\",");
        sb.append("\"totalDeductions\":").append(taxReturn.getTotalDeductions()).append(",");
        sb.append("\"taxableIncome\":").append(taxpayer.getTaxableIncome()).append(",");

        // Bracket breakdown
        sb.append("\"brackets\":[");
        List<TaxBracketCalculation> brackets = taxReturn.getBracketCalculations();
        if (brackets != null && !brackets.isEmpty()) {
            sb.append(brackets.stream()
                    .filter(b -> b.getIncomeInBracket() != null && b.getIncomeInBracket().compareTo(BigDecimal.ZERO) > 0)
                    .map(b -> String.format("{\"rate\":\"%s%%\",\"income\":%s,\"tax\":%s}",
                            b.getTaxRate(), b.getIncomeInBracket(), b.getTaxFromBracket()))
                    .collect(Collectors.joining(",")));
        }
        sb.append("],");

        sb.append("\"regularTax\":").append(taxReturn.getRegularTax()).append(",");

        // Credits
        sb.append("\"credits\":{");
        sb.append("\"childTaxCredit\":").append(childTaxCredit.getTotalCredit()).append(",");
        sb.append("\"earnedIncomeCredit\":").append(earnedIncomeCredit.getCreditAmount()).append(",");
        sb.append("\"totalCredits\":").append(taxReturn.getTotalCredits());
        sb.append("},");

        // AMT
        sb.append("\"alternativeMinimumTax\":").append(amt.getAmtDue()).append(",");

        // Final amounts
        sb.append("\"totalTaxBeforeCredits\":").append(taxReturn.getTotalTaxBeforeCredits()).append(",");
        sb.append("\"totalTaxAfterCredits\":").append(taxReturn.getTotalTaxAfterCredits()).append(",");
        sb.append("\"effectiveTaxRate\":\"").append(
                taxpayer.getGrossIncome().compareTo(BigDecimal.ZERO) > 0
                        ? taxReturn.getTotalTaxAfterCredits()
                        .multiply(new BigDecimal("100"))
                        .divide(taxpayer.getGrossIncome(), 1, RoundingMode.HALF_UP) + "%"
                        : "0%").append("\"");
        sb.append("}");
        return sb.toString();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}

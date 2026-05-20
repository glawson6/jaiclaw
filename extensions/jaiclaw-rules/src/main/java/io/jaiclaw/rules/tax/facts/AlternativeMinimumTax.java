package io.jaiclaw.rules.tax.facts;

import java.math.BigDecimal;

/**
 * Represents Alternative Minimum Tax (AMT) calculation based on IRC Section 55.
 * Based on IRS Revenue Procedure 2025-32 for tax year 2026.
 */
public class AlternativeMinimumTax {

    private String amtId;
    private FilingStatus filingStatus;
    private BigDecimal exemptionAmount = BigDecimal.ZERO;
    private BigDecimal thresholdPhaseoutAmount = BigDecimal.ZERO;
    private BigDecimal completePhaseoutAmount = BigDecimal.ZERO;
    private BigDecimal alternativeMinimumTaxableIncome = BigDecimal.ZERO;
    private BigDecimal exemptionAfterPhaseout = BigDecimal.ZERO;
    private BigDecimal amtIncomeAfterExemption = BigDecimal.ZERO;
    private BigDecimal excessTaxableIncomeThreshold = BigDecimal.ZERO;
    private BigDecimal amtCalculated = BigDecimal.ZERO;
    private BigDecimal regularTax = BigDecimal.ZERO;
    private BigDecimal amtDue = BigDecimal.ZERO;

    public AlternativeMinimumTax() {}

    public AlternativeMinimumTax(String amtId, FilingStatus filingStatus) {
        this.amtId = amtId;
        this.filingStatus = filingStatus;
    }

    public String getAmtId() { return amtId; }
    public void setAmtId(String amtId) { this.amtId = amtId; }
    public FilingStatus getFilingStatus() { return filingStatus; }
    public void setFilingStatus(FilingStatus filingStatus) { this.filingStatus = filingStatus; }
    public BigDecimal getExemptionAmount() { return exemptionAmount; }
    public void setExemptionAmount(BigDecimal exemptionAmount) { this.exemptionAmount = exemptionAmount; }
    public BigDecimal getThresholdPhaseoutAmount() { return thresholdPhaseoutAmount; }
    public void setThresholdPhaseoutAmount(BigDecimal thresholdPhaseoutAmount) { this.thresholdPhaseoutAmount = thresholdPhaseoutAmount; }
    public BigDecimal getCompletePhaseoutAmount() { return completePhaseoutAmount; }
    public void setCompletePhaseoutAmount(BigDecimal completePhaseoutAmount) { this.completePhaseoutAmount = completePhaseoutAmount; }
    public BigDecimal getAlternativeMinimumTaxableIncome() { return alternativeMinimumTaxableIncome; }
    public void setAlternativeMinimumTaxableIncome(BigDecimal alternativeMinimumTaxableIncome) { this.alternativeMinimumTaxableIncome = alternativeMinimumTaxableIncome; }
    public BigDecimal getExemptionAfterPhaseout() { return exemptionAfterPhaseout; }
    public void setExemptionAfterPhaseout(BigDecimal exemptionAfterPhaseout) { this.exemptionAfterPhaseout = exemptionAfterPhaseout; }
    public BigDecimal getAmtIncomeAfterExemption() { return amtIncomeAfterExemption; }
    public void setAmtIncomeAfterExemption(BigDecimal amtIncomeAfterExemption) { this.amtIncomeAfterExemption = amtIncomeAfterExemption; }
    public BigDecimal getExcessTaxableIncomeThreshold() { return excessTaxableIncomeThreshold; }
    public void setExcessTaxableIncomeThreshold(BigDecimal excessTaxableIncomeThreshold) { this.excessTaxableIncomeThreshold = excessTaxableIncomeThreshold; }
    public BigDecimal getAmtCalculated() { return amtCalculated; }
    public void setAmtCalculated(BigDecimal amtCalculated) { this.amtCalculated = amtCalculated; }
    public BigDecimal getRegularTax() { return regularTax; }
    public void setRegularTax(BigDecimal regularTax) { this.regularTax = regularTax; }
    public BigDecimal getAmtDue() { return amtDue; }
    public void setAmtDue(BigDecimal amtDue) { this.amtDue = amtDue; }

    @Override
    public String toString() {
        return "AlternativeMinimumTax{filingStatus=" + filingStatus + ", exemptionAmount=" + exemptionAmount +
                ", amtCalculated=" + amtCalculated + ", amtDue=" + amtDue + '}';
    }
}

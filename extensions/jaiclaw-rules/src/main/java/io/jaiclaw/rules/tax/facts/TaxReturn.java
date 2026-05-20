package io.jaiclaw.rules.tax.facts;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a tax return with calculated tax amounts, credits, and deductions.
 * Based on IRS Revenue Procedure 2025-32 for tax year 2026.
 */
public class TaxReturn {

    private String returnId;
    private int taxYear = 2026;
    private TaxPayer taxPayer;
    private BigDecimal standardDeduction = BigDecimal.ZERO;
    private BigDecimal itemizedDeductions = BigDecimal.ZERO;
    private BigDecimal totalDeductions = BigDecimal.ZERO;
    private BigDecimal regularTax = BigDecimal.ZERO;
    private BigDecimal alternativeMinimumTax = BigDecimal.ZERO;
    private BigDecimal totalTaxBeforeCredits = BigDecimal.ZERO;
    private BigDecimal childTaxCredit = BigDecimal.ZERO;
    private BigDecimal earnedIncomeCredit = BigDecimal.ZERO;
    private BigDecimal adoptionCredit = BigDecimal.ZERO;
    private BigDecimal otherCredits = BigDecimal.ZERO;
    private BigDecimal totalCredits = BigDecimal.ZERO;
    private BigDecimal totalTaxAfterCredits = BigDecimal.ZERO;
    private BigDecimal refundableCredits = BigDecimal.ZERO;
    private BigDecimal taxDue = BigDecimal.ZERO;
    private BigDecimal refundAmount = BigDecimal.ZERO;
    private List<TaxBracketCalculation> bracketCalculations = new ArrayList<>();

    public TaxReturn() {}

    public TaxReturn(String returnId, TaxPayer taxPayer) {
        this.returnId = returnId;
        this.taxPayer = taxPayer;
    }

    public String getReturnId() { return returnId; }
    public void setReturnId(String returnId) { this.returnId = returnId; }
    public int getTaxYear() { return taxYear; }
    public void setTaxYear(int taxYear) { this.taxYear = taxYear; }
    public TaxPayer getTaxPayer() { return taxPayer; }
    public void setTaxPayer(TaxPayer taxPayer) { this.taxPayer = taxPayer; }
    public BigDecimal getStandardDeduction() { return standardDeduction; }
    public void setStandardDeduction(BigDecimal standardDeduction) { this.standardDeduction = standardDeduction; }
    public BigDecimal getItemizedDeductions() { return itemizedDeductions; }
    public void setItemizedDeductions(BigDecimal itemizedDeductions) { this.itemizedDeductions = itemizedDeductions; }
    public BigDecimal getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(BigDecimal totalDeductions) { this.totalDeductions = totalDeductions; }
    public BigDecimal getRegularTax() { return regularTax; }
    public void setRegularTax(BigDecimal regularTax) { this.regularTax = regularTax; }
    public BigDecimal getAlternativeMinimumTax() { return alternativeMinimumTax; }
    public void setAlternativeMinimumTax(BigDecimal alternativeMinimumTax) { this.alternativeMinimumTax = alternativeMinimumTax; }
    public BigDecimal getTotalTaxBeforeCredits() { return totalTaxBeforeCredits; }
    public void setTotalTaxBeforeCredits(BigDecimal totalTaxBeforeCredits) { this.totalTaxBeforeCredits = totalTaxBeforeCredits; }
    public BigDecimal getChildTaxCredit() { return childTaxCredit; }
    public void setChildTaxCredit(BigDecimal childTaxCredit) { this.childTaxCredit = childTaxCredit; }
    public BigDecimal getEarnedIncomeCredit() { return earnedIncomeCredit; }
    public void setEarnedIncomeCredit(BigDecimal earnedIncomeCredit) { this.earnedIncomeCredit = earnedIncomeCredit; }
    public BigDecimal getAdoptionCredit() { return adoptionCredit; }
    public void setAdoptionCredit(BigDecimal adoptionCredit) { this.adoptionCredit = adoptionCredit; }
    public BigDecimal getOtherCredits() { return otherCredits; }
    public void setOtherCredits(BigDecimal otherCredits) { this.otherCredits = otherCredits; }
    public BigDecimal getTotalCredits() { return totalCredits; }
    public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }
    public BigDecimal getTotalTaxAfterCredits() { return totalTaxAfterCredits; }
    public void setTotalTaxAfterCredits(BigDecimal totalTaxAfterCredits) { this.totalTaxAfterCredits = totalTaxAfterCredits; }
    public BigDecimal getRefundableCredits() { return refundableCredits; }
    public void setRefundableCredits(BigDecimal refundableCredits) { this.refundableCredits = refundableCredits; }
    public BigDecimal getTaxDue() { return taxDue; }
    public void setTaxDue(BigDecimal taxDue) { this.taxDue = taxDue; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public List<TaxBracketCalculation> getBracketCalculations() { return bracketCalculations; }
    public void setBracketCalculations(List<TaxBracketCalculation> bracketCalculations) { this.bracketCalculations = bracketCalculations; }

    @Override
    public String toString() {
        return "TaxReturn{returnId='" + returnId + "', taxYear=" + taxYear +
                ", standardDeduction=" + standardDeduction + ", regularTax=" + regularTax +
                ", totalCredits=" + totalCredits + ", totalTaxAfterCredits=" + totalTaxAfterCredits + '}';
    }
}

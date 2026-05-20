package io.jaiclaw.rules.tax.facts;

import java.math.BigDecimal;

/**
 * Represents Earned Income Credit (EIC) calculation based on IRC Section 32.
 * Based on IRS Revenue Procedure 2025-32 for tax year 2026.
 */
public class EarnedIncomeCredit {

    private String creditId;
    private int numberOfQualifyingChildren;
    private BigDecimal earnedIncome = BigDecimal.ZERO;
    private BigDecimal adjustedGrossIncome = BigDecimal.ZERO;
    private FilingStatus filingStatus;
    private BigDecimal maximumCreditAmount = BigDecimal.ZERO;
    private BigDecimal thresholdPhaseoutAmount = BigDecimal.ZERO;
    private BigDecimal completedPhaseoutAmount = BigDecimal.ZERO;
    private BigDecimal earnedIncomeAmount = BigDecimal.ZERO;
    private BigDecimal investmentIncome = BigDecimal.ZERO;
    private BigDecimal investmentIncomeLimit = new BigDecimal("12200.00");
    private BigDecimal creditAmount = BigDecimal.ZERO;
    private boolean isEligible = true;

    public EarnedIncomeCredit() {}

    public EarnedIncomeCredit(String creditId, int numberOfQualifyingChildren, FilingStatus filingStatus) {
        this.creditId = creditId;
        this.numberOfQualifyingChildren = numberOfQualifyingChildren;
        this.filingStatus = filingStatus;
    }

    public String getCreditId() { return creditId; }
    public void setCreditId(String creditId) { this.creditId = creditId; }
    public int getNumberOfQualifyingChildren() { return numberOfQualifyingChildren; }
    public void setNumberOfQualifyingChildren(int numberOfQualifyingChildren) { this.numberOfQualifyingChildren = numberOfQualifyingChildren; }
    public BigDecimal getEarnedIncome() { return earnedIncome; }
    public void setEarnedIncome(BigDecimal earnedIncome) { this.earnedIncome = earnedIncome; }
    public BigDecimal getAdjustedGrossIncome() { return adjustedGrossIncome; }
    public void setAdjustedGrossIncome(BigDecimal adjustedGrossIncome) { this.adjustedGrossIncome = adjustedGrossIncome; }
    public FilingStatus getFilingStatus() { return filingStatus; }
    public void setFilingStatus(FilingStatus filingStatus) { this.filingStatus = filingStatus; }
    public BigDecimal getMaximumCreditAmount() { return maximumCreditAmount; }
    public void setMaximumCreditAmount(BigDecimal maximumCreditAmount) { this.maximumCreditAmount = maximumCreditAmount; }
    public BigDecimal getThresholdPhaseoutAmount() { return thresholdPhaseoutAmount; }
    public void setThresholdPhaseoutAmount(BigDecimal thresholdPhaseoutAmount) { this.thresholdPhaseoutAmount = thresholdPhaseoutAmount; }
    public BigDecimal getCompletedPhaseoutAmount() { return completedPhaseoutAmount; }
    public void setCompletedPhaseoutAmount(BigDecimal completedPhaseoutAmount) { this.completedPhaseoutAmount = completedPhaseoutAmount; }
    public BigDecimal getEarnedIncomeAmount() { return earnedIncomeAmount; }
    public void setEarnedIncomeAmount(BigDecimal earnedIncomeAmount) { this.earnedIncomeAmount = earnedIncomeAmount; }
    public BigDecimal getInvestmentIncome() { return investmentIncome; }
    public void setInvestmentIncome(BigDecimal investmentIncome) { this.investmentIncome = investmentIncome; }
    public BigDecimal getInvestmentIncomeLimit() { return investmentIncomeLimit; }
    public void setInvestmentIncomeLimit(BigDecimal investmentIncomeLimit) { this.investmentIncomeLimit = investmentIncomeLimit; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public void setCreditAmount(BigDecimal creditAmount) { this.creditAmount = creditAmount; }
    public boolean isEligible() { return isEligible; }
    public void setEligible(boolean eligible) { isEligible = eligible; }

    @Override
    public String toString() {
        return "EarnedIncomeCredit{numberOfQualifyingChildren=" + numberOfQualifyingChildren +
                ", earnedIncome=" + earnedIncome + ", creditAmount=" + creditAmount +
                ", isEligible=" + isEligible + '}';
    }
}

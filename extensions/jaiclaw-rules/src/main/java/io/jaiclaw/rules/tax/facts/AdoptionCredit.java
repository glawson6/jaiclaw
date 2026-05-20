package io.jaiclaw.rules.tax.facts;

import java.math.BigDecimal;

/**
 * Represents Adoption Credit calculation based on IRC Section 23.
 * Based on IRS Revenue Procedure 2025-32 for tax year 2026.
 */
public class AdoptionCredit {

    private String creditId;
    private int numberOfAdoptions;
    private boolean hasSpecialNeedsChild;
    private BigDecimal maximumCreditPerAdoption = new BigDecimal("17670.00");
    private BigDecimal refundablePortionPerAdoption = new BigDecimal("5120.00");
    private BigDecimal phaseoutBeginMAGI = new BigDecimal("265080.00");
    private BigDecimal phaseoutCompleteMAGI = new BigDecimal("305080.00");
    private BigDecimal qualifiedAdoptionExpenses = BigDecimal.ZERO;
    private BigDecimal modifiedAdjustedGrossIncome = BigDecimal.ZERO;
    private BigDecimal totalCredit = BigDecimal.ZERO;
    private BigDecimal refundableAmount = BigDecimal.ZERO;
    private BigDecimal nonRefundableAmount = BigDecimal.ZERO;

    public AdoptionCredit() {}

    public AdoptionCredit(String creditId, int numberOfAdoptions) {
        this.creditId = creditId;
        this.numberOfAdoptions = numberOfAdoptions;
    }

    public String getCreditId() { return creditId; }
    public void setCreditId(String creditId) { this.creditId = creditId; }
    public int getNumberOfAdoptions() { return numberOfAdoptions; }
    public void setNumberOfAdoptions(int numberOfAdoptions) { this.numberOfAdoptions = numberOfAdoptions; }
    public boolean isHasSpecialNeedsChild() { return hasSpecialNeedsChild; }
    public void setHasSpecialNeedsChild(boolean hasSpecialNeedsChild) { this.hasSpecialNeedsChild = hasSpecialNeedsChild; }
    public BigDecimal getMaximumCreditPerAdoption() { return maximumCreditPerAdoption; }
    public void setMaximumCreditPerAdoption(BigDecimal maximumCreditPerAdoption) { this.maximumCreditPerAdoption = maximumCreditPerAdoption; }
    public BigDecimal getRefundablePortionPerAdoption() { return refundablePortionPerAdoption; }
    public void setRefundablePortionPerAdoption(BigDecimal refundablePortionPerAdoption) { this.refundablePortionPerAdoption = refundablePortionPerAdoption; }
    public BigDecimal getPhaseoutBeginMAGI() { return phaseoutBeginMAGI; }
    public void setPhaseoutBeginMAGI(BigDecimal phaseoutBeginMAGI) { this.phaseoutBeginMAGI = phaseoutBeginMAGI; }
    public BigDecimal getPhaseoutCompleteMAGI() { return phaseoutCompleteMAGI; }
    public void setPhaseoutCompleteMAGI(BigDecimal phaseoutCompleteMAGI) { this.phaseoutCompleteMAGI = phaseoutCompleteMAGI; }
    public BigDecimal getQualifiedAdoptionExpenses() { return qualifiedAdoptionExpenses; }
    public void setQualifiedAdoptionExpenses(BigDecimal qualifiedAdoptionExpenses) { this.qualifiedAdoptionExpenses = qualifiedAdoptionExpenses; }
    public BigDecimal getModifiedAdjustedGrossIncome() { return modifiedAdjustedGrossIncome; }
    public void setModifiedAdjustedGrossIncome(BigDecimal modifiedAdjustedGrossIncome) { this.modifiedAdjustedGrossIncome = modifiedAdjustedGrossIncome; }
    public BigDecimal getTotalCredit() { return totalCredit; }
    public void setTotalCredit(BigDecimal totalCredit) { this.totalCredit = totalCredit; }
    public BigDecimal getRefundableAmount() { return refundableAmount; }
    public void setRefundableAmount(BigDecimal refundableAmount) { this.refundableAmount = refundableAmount; }
    public BigDecimal getNonRefundableAmount() { return nonRefundableAmount; }
    public void setNonRefundableAmount(BigDecimal nonRefundableAmount) { this.nonRefundableAmount = nonRefundableAmount; }

    @Override
    public String toString() {
        return "AdoptionCredit{numberOfAdoptions=" + numberOfAdoptions +
                ", hasSpecialNeedsChild=" + hasSpecialNeedsChild + ", totalCredit=" + totalCredit +
                ", refundableAmount=" + refundableAmount + '}';
    }
}

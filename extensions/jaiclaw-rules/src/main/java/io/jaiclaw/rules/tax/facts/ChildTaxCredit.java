package io.jaiclaw.rules.tax.facts;

import java.math.BigDecimal;

/**
 * Represents Child Tax Credit calculation based on IRC Section 24.
 * Based on IRS Revenue Procedure 2025-32 for tax year 2026.
 */
public class ChildTaxCredit {

    private String creditId;
    private int numberOfQualifyingChildren;
    private BigDecimal maximumCreditPerChild = new BigDecimal("2200.00");
    private BigDecimal refundablePortionPerChild = new BigDecimal("1700.00");
    private BigDecimal totalCredit = BigDecimal.ZERO;
    private BigDecimal nonRefundableAmount = BigDecimal.ZERO;
    private BigDecimal refundableAmount = BigDecimal.ZERO;

    public ChildTaxCredit() {}

    public ChildTaxCredit(String creditId, int numberOfQualifyingChildren) {
        this.creditId = creditId;
        this.numberOfQualifyingChildren = numberOfQualifyingChildren;
    }

    public String getCreditId() { return creditId; }
    public void setCreditId(String creditId) { this.creditId = creditId; }
    public int getNumberOfQualifyingChildren() { return numberOfQualifyingChildren; }
    public void setNumberOfQualifyingChildren(int numberOfQualifyingChildren) { this.numberOfQualifyingChildren = numberOfQualifyingChildren; }
    public BigDecimal getMaximumCreditPerChild() { return maximumCreditPerChild; }
    public void setMaximumCreditPerChild(BigDecimal maximumCreditPerChild) { this.maximumCreditPerChild = maximumCreditPerChild; }
    public BigDecimal getRefundablePortionPerChild() { return refundablePortionPerChild; }
    public void setRefundablePortionPerChild(BigDecimal refundablePortionPerChild) { this.refundablePortionPerChild = refundablePortionPerChild; }
    public BigDecimal getTotalCredit() { return totalCredit; }
    public void setTotalCredit(BigDecimal totalCredit) { this.totalCredit = totalCredit; }
    public BigDecimal getNonRefundableAmount() { return nonRefundableAmount; }
    public void setNonRefundableAmount(BigDecimal nonRefundableAmount) { this.nonRefundableAmount = nonRefundableAmount; }
    public BigDecimal getRefundableAmount() { return refundableAmount; }
    public void setRefundableAmount(BigDecimal refundableAmount) { this.refundableAmount = refundableAmount; }

    @Override
    public String toString() {
        return "ChildTaxCredit{numberOfQualifyingChildren=" + numberOfQualifyingChildren +
                ", totalCredit=" + totalCredit + ", refundableAmount=" + refundableAmount + '}';
    }
}

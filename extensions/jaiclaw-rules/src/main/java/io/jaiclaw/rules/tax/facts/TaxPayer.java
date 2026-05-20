package io.jaiclaw.rules.tax.facts;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a taxpayer with all relevant information for tax calculations.
 * Based on IRS Revenue Procedure 2025-32 for tax year 2026.
 */
public class TaxPayer {

    private String taxpayerId;
    private FilingStatus filingStatus;
    private BigDecimal grossIncome = BigDecimal.ZERO;
    private BigDecimal adjustedGrossIncome = BigDecimal.ZERO;
    private BigDecimal modifiedAdjustedGrossIncome = BigDecimal.ZERO;
    private BigDecimal taxableIncome = BigDecimal.ZERO;
    private BigDecimal earnedIncome = BigDecimal.ZERO;
    private BigDecimal unearnedIncome = BigDecimal.ZERO;
    private BigDecimal investmentIncome = BigDecimal.ZERO;
    private int numberOfDependents = 0;
    private int numberOfQualifyingChildren = 0;
    private List<Dependent> dependents = new ArrayList<>();
    private boolean isAgedOrBlind = false;
    private boolean isDependent = false;
    private int age = 0;
    private BigDecimal itemizedDeductions = BigDecimal.ZERO;
    private boolean useStandardDeduction = true;

    public TaxPayer() {}

    public TaxPayer(String taxpayerId, FilingStatus filingStatus) {
        this.taxpayerId = taxpayerId;
        this.filingStatus = filingStatus;
    }

    public String getTaxpayerId() { return taxpayerId; }
    public void setTaxpayerId(String taxpayerId) { this.taxpayerId = taxpayerId; }
    public FilingStatus getFilingStatus() { return filingStatus; }
    public void setFilingStatus(FilingStatus filingStatus) { this.filingStatus = filingStatus; }
    public BigDecimal getGrossIncome() { return grossIncome; }
    public void setGrossIncome(BigDecimal grossIncome) { this.grossIncome = grossIncome; }
    public BigDecimal getAdjustedGrossIncome() { return adjustedGrossIncome; }
    public void setAdjustedGrossIncome(BigDecimal adjustedGrossIncome) { this.adjustedGrossIncome = adjustedGrossIncome; }
    public BigDecimal getModifiedAdjustedGrossIncome() { return modifiedAdjustedGrossIncome; }
    public void setModifiedAdjustedGrossIncome(BigDecimal modifiedAdjustedGrossIncome) { this.modifiedAdjustedGrossIncome = modifiedAdjustedGrossIncome; }
    public BigDecimal getTaxableIncome() { return taxableIncome; }
    public void setTaxableIncome(BigDecimal taxableIncome) { this.taxableIncome = taxableIncome; }
    public BigDecimal getEarnedIncome() { return earnedIncome; }
    public void setEarnedIncome(BigDecimal earnedIncome) { this.earnedIncome = earnedIncome; }
    public BigDecimal getUnearnedIncome() { return unearnedIncome; }
    public void setUnearnedIncome(BigDecimal unearnedIncome) { this.unearnedIncome = unearnedIncome; }
    public BigDecimal getInvestmentIncome() { return investmentIncome; }
    public void setInvestmentIncome(BigDecimal investmentIncome) { this.investmentIncome = investmentIncome; }
    public int getNumberOfDependents() { return numberOfDependents; }
    public void setNumberOfDependents(int numberOfDependents) { this.numberOfDependents = numberOfDependents; }
    public int getNumberOfQualifyingChildren() { return numberOfQualifyingChildren; }
    public void setNumberOfQualifyingChildren(int numberOfQualifyingChildren) { this.numberOfQualifyingChildren = numberOfQualifyingChildren; }
    public List<Dependent> getDependents() { return dependents; }
    public void setDependents(List<Dependent> dependents) { this.dependents = dependents; }
    public boolean isAgedOrBlind() { return isAgedOrBlind; }
    public void setAgedOrBlind(boolean agedOrBlind) { isAgedOrBlind = agedOrBlind; }
    public boolean isDependent() { return isDependent; }
    public void setDependent(boolean dependent) { isDependent = dependent; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public BigDecimal getItemizedDeductions() { return itemizedDeductions; }
    public void setItemizedDeductions(BigDecimal itemizedDeductions) { this.itemizedDeductions = itemizedDeductions; }
    public boolean isUseStandardDeduction() { return useStandardDeduction; }
    public void setUseStandardDeduction(boolean useStandardDeduction) { this.useStandardDeduction = useStandardDeduction; }

    @Override
    public String toString() {
        return "TaxPayer{taxpayerId='" + taxpayerId + "', filingStatus=" + filingStatus +
                ", grossIncome=" + grossIncome + ", adjustedGrossIncome=" + adjustedGrossIncome +
                ", taxableIncome=" + taxableIncome + ", numberOfDependents=" + numberOfDependents +
                ", numberOfQualifyingChildren=" + numberOfQualifyingChildren + '}';
    }
}

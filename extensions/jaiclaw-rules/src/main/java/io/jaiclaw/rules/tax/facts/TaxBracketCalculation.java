package io.jaiclaw.rules.tax.facts;

import java.math.BigDecimal;

/**
 * Represents a tax bracket calculation showing the breakdown of tax computation.
 * Based on IRS Revenue Procedure 2025-32 for tax year 2026.
 */
public class TaxBracketCalculation {

    private int bracketNumber;
    private BigDecimal taxRate;
    private BigDecimal incomeInBracket;
    private BigDecimal taxFromBracket;
    private BigDecimal bracketFloor;
    private BigDecimal bracketCeiling;
    private String description;

    public TaxBracketCalculation() {}

    public TaxBracketCalculation(int bracketNumber, BigDecimal taxRate, BigDecimal incomeInBracket,
                                BigDecimal taxFromBracket, BigDecimal bracketFloor, BigDecimal bracketCeiling) {
        this.bracketNumber = bracketNumber;
        this.taxRate = taxRate;
        this.incomeInBracket = incomeInBracket;
        this.taxFromBracket = taxFromBracket;
        this.bracketFloor = bracketFloor;
        this.bracketCeiling = bracketCeiling;
    }

    public int getBracketNumber() { return bracketNumber; }
    public void setBracketNumber(int bracketNumber) { this.bracketNumber = bracketNumber; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public BigDecimal getIncomeInBracket() { return incomeInBracket; }
    public void setIncomeInBracket(BigDecimal incomeInBracket) { this.incomeInBracket = incomeInBracket; }
    public BigDecimal getTaxFromBracket() { return taxFromBracket; }
    public void setTaxFromBracket(BigDecimal taxFromBracket) { this.taxFromBracket = taxFromBracket; }
    public BigDecimal getBracketFloor() { return bracketFloor; }
    public void setBracketFloor(BigDecimal bracketFloor) { this.bracketFloor = bracketFloor; }
    public BigDecimal getBracketCeiling() { return bracketCeiling; }
    public void setBracketCeiling(BigDecimal bracketCeiling) { this.bracketCeiling = bracketCeiling; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "TaxBracketCalculation{bracketNumber=" + bracketNumber + ", taxRate=" + taxRate +
                "%, incomeInBracket=" + incomeInBracket + ", taxFromBracket=" + taxFromBracket + '}';
    }
}

package io.jaiclaw.rules.tax.facts;

/**
 * Enum representing the tax filing status as defined in IRC Section 1(j)(2).
 * Based on IRS Revenue Procedure 2025-32 for tax year 2026.
 */
public enum FilingStatus {

    MARRIED_FILING_JOINTLY("Married Filing Jointly", "MFJ"),
    HEAD_OF_HOUSEHOLD("Head of Household", "HOH"),
    SINGLE("Single", "SINGLE"),
    MARRIED_FILING_SEPARATELY("Married Filing Separately", "MFS"),
    ESTATE_OR_TRUST("Estate or Trust", "ESTATE");

    private final String displayName;
    private final String code;

    FilingStatus(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

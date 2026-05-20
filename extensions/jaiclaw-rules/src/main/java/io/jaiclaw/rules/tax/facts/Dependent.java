package io.jaiclaw.rules.tax.facts;

import java.math.BigDecimal;

/**
 * Represents a dependent for tax purposes.
 * Based on IRS Revenue Procedure 2025-32 for tax year 2026.
 */
public class Dependent {

    private String dependentId;
    private String name;
    private int age;
    private boolean isQualifyingChild;
    private boolean hasSpecialNeeds;
    private BigDecimal grossIncome = BigDecimal.ZERO;

    public Dependent() {}

    public Dependent(String dependentId, String name, int age, boolean isQualifyingChild) {
        this.dependentId = dependentId;
        this.name = name;
        this.age = age;
        this.isQualifyingChild = isQualifyingChild;
    }

    public String getDependentId() { return dependentId; }
    public void setDependentId(String dependentId) { this.dependentId = dependentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public boolean isQualifyingChild() { return isQualifyingChild; }
    public void setQualifyingChild(boolean qualifyingChild) { isQualifyingChild = qualifyingChild; }
    public boolean isHasSpecialNeeds() { return hasSpecialNeeds; }
    public void setHasSpecialNeeds(boolean hasSpecialNeeds) { this.hasSpecialNeeds = hasSpecialNeeds; }
    public BigDecimal getGrossIncome() { return grossIncome; }
    public void setGrossIncome(BigDecimal grossIncome) { this.grossIncome = grossIncome; }

    @Override
    public String toString() {
        return "Dependent{dependentId='" + dependentId + "', name='" + name + "', age=" + age +
                ", isQualifyingChild=" + isQualifyingChild + ", hasSpecialNeeds=" + hasSpecialNeeds + '}';
    }
}

# Tax Advisor

AI tax preparation interview with Drools-computed IRS 2026 federal taxes.

## Problem

Taxpayers don't understand filing statuses, credits, or bracket math. Online tax software asks rigid form questions. A tax professional is expensive. There's no easy way to compare "what if" scenarios (e.g., single vs. head of household).

## Solution

An AI agent conducts a natural-language interview ("I made about 85 grand"), explains tax concepts in plain language, and uses Drools rules to compute exact IRS 2026 taxes with penny-precise accuracy. The LLM never calculates taxes (hallucination risk) but is ideal for guiding data collection and explaining results. What-if comparisons run two scenarios side-by-side.

## Architecture

```
User: "I'm single, made $85,000"
    │
    ▼
┌──────────────────┐
│  Tax Advisor     │
│  Agent (LLM)     │──── Conversational interview
└──────┬───────────┘
       │
       ▼
┌──────────────────┐     ┌─────────────────────────────────┐
│  calculate_tax   │────▶│  Drools Tax Rules (6 DRL files) │
│  (custom tool)   │◀────│  Standard deductions, brackets, │
│                  │     │  CTC, EIC, AMT, other credits   │
└──────┬───────────┘     └─────────────────────────────────┘
       │
       ▼
  Agent explains results conversationally

For "what if" questions:
┌──────────────────┐
│  compare_tax     │──── Runs two scenarios, returns delta
└──────────────────┘
```

**Key classes:**
- `TaxAdvisorApplication` — Spring Boot entry point
- `TaxAdvisorConfig` — `@Configuration` that builds a custom `StatelessKieSession` with tax rules loaded (overrides default kmodule packages)
- `TaxCalculationTool` — custom `ToolCallback` that constructs `TaxPayer`, `TaxReturn`, and credit facts, fires rules, and formats the result
- `TaxComparisonTool` — runs two scenarios side-by-side for what-if comparisons

## Design

- **Custom KieSession**: Tax rules declare `package io.jaiclaw.rules.tax` but the default kmodule only loads `packages="rules"`. This example provides its own `kmodule.xml` with `packages="rules,io.jaiclaw.rules.tax"`.
- **Multi-fact graph**: Tax calculation requires inserting 5 facts (TaxPayer, TaxReturn, ChildTaxCredit, EarnedIncomeCredit, AlternativeMinimumTax) into the session. Rules fire across facts to compute deductions, brackets, and credits.
- **LLM as interviewer, not calculator**: The agent never does tax math. It collects data, calls the tool, and explains results. This eliminates hallucination risk for financial calculations.
- **Scenario comparison**: The compare_tax tool reuses the calculation logic to run two scenarios and return both results for side-by-side comparison.

## Build & Run

### Prerequisites
- Java 21+
- `ANTHROPIC_API_KEY` environment variable

### Build
```bash
./mvnw package -pl :jaiclaw-example-tax-advisor -am -DskipTests
```

### Run
```bash
ANTHROPIC_API_KEY=your-key java -jar jaiclaw-examples/tax-advisor/target/jaiclaw-example-tax-advisor-0.4.0.jar
```

### Verify
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"I'\''d like help estimating my 2026 taxes. I'\''m single and made about $85,000."}'
```

Expected: The agent acknowledges the filing status and income, asks about dependents and deductions, then calculates using the tool and explains: "Your taxable income after the $15,000 standard deduction is $70,000. Here's your bracket breakdown..."

### What-if example
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"What if I filed as head of household instead of single with the same $85,000 income?"}'
```

Expected: The agent uses compare_tax to show both scenarios side-by-side with the tax difference.

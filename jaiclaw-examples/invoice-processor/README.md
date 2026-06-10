# Invoice Processor

Accounts-payable invoice ingestion built on the JaiClaw pipeline module.
Showcases the **FILE trigger** and the **DEAD_LETTER** error strategy.

## Problem

AP teams spend 5–15 minutes per invoice on manual data entry, GL coding, and
PO matching. At 500 invoices/month that's 40–125 hours per month — plus the
late-payment penalties when invoices sit in someone's inbox. AI document
pipelines deliver 200–300% ROI within 12 months and are the highest-volume
AI automation purchase across finance.

## Solution

A five-stage pipeline that watches an inbox directory; each file flows through
the same path:

1. **classify** (AGENT) — is this an invoice, a credit memo, a receipt?
2. **extract** (AGENT) — vendor, PO number, amount, due date
3. **validate** (PROCESSOR) — checks the PO number against an in-memory stub
4. **approve-or-flag** (AGENT) — auto-approve if matched + amount < $5000
5. **notify** (PROCESSOR) — append a record to `~/.jaiclaw/invoice-processor/approved.jsonl`

Parse failures (LLM exception, malformed file, etc.) are routed to a
dead-letter log via `errorStrategy: DEAD_LETTER` so a corrupt invoice can't
take down the route.

## Architecture

```
~/.jaiclaw/invoice-processor/inbox/*.txt          (FILE trigger)
                  │
                  ▼
   ┌────────────────────────────────────────────────────────────┐
   │ Pipeline: invoice-processor   errorStrategy: DEAD_LETTER   │
   │   classify  ─▶  extract  ─▶  validate  ─▶  approve-or-flag │
   │   (AGENT)       (AGENT)     (PROCESSOR)       (AGENT)      │
   │                                  └────────────▶  notify    │
   │                                                (PROCESSOR) │
   └────────────────────────────────────────────────────────────┘
                  │                          ╲
                  ▼                           ╲
            log + JSONL                       Dead-letter log
                                              (on parse failure)
```

## Design

- **FILE trigger**, not HTTP. AP usually receives PDFs via email-to-folder
  forwarding or via an EDI watch directory. The Camel `file:` URI plus
  `move=.done&moveFailed=.error` gives us the same semantics with zero glue.
- **DEAD_LETTER strategy.** Phase A's dead-letter wiring is showcased: a bad
  file (say, an unreadable PDF or an LLM 500) doesn't poison the route — it
  goes to a log queue so an operator can inspect.
- **Bean stubs at the boundaries.** `invoiceValidator` and `invoiceNotifier`
  are tiny `Function<String,String>` Spring beans. Swap them with real
  SAP / NetSuite / Workday connectors via Spring `@Profile` and the rest of
  the pipeline stays unchanged.
- **Synthetic invoices via the shell.** `inbox <text>` drops a file into the
  watched directory so reviewers don't need real PDFs to demo it.

## Prerequisites

- Java 21
- `ANTHROPIC_API_KEY` for the AGENT stages

## Build & Run

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle
./mvnw package -pl :jaiclaw-example-invoice-processor -am -DskipTests -o
ANTHROPIC_API_KEY=sk-ant-... \
  java -jar jaiclaw-examples/invoice-processor/target/jaiclaw-example-invoice-processor-*.jar
```

Inside the shell:

```
shell:> inbox            # default sample invoice with PO-1001
Wrote ~/.jaiclaw/invoice-processor/inbox/invoice-….txt … pipeline picks it up

shell:> executions
executionId                           status     ms
…                                     SUCCESS    1850

shell:> last-result
executionId=…
status=SUCCESS
totalMs=1850
--- per-stage durations (ms) ---
  classify         = 612
  extract          = 691
  validate         = 0
  approve-or-flag  = 547
  notify           = 0

shell:> list-approved
{"approvedAt":"2026-06-10T…","po":"PO-1001","vendor":"Acme Corp","amount":"$1,250.00"}
```

## Extension points

- **Real OCR / PDF parsing:** wrap the file body in a Tika / pdfbox step before
  the classify stage by adding a CAMEL stage or extending the FILE-trigger URI.
- **Real PO database:** replace `InvoiceProcessorBeans#invoiceValidator` with a
  JDBC / REST bean.
- **Real ERP write-back:** replace `invoiceNotifier` with a NetSuite / SAP
  client that posts the AP voucher.
- **Email notifications:** add another PROCESSOR or chain a CAMEL output that
  sends a Slack DM / email on `FLAG_FOR_REVIEW`.

package io.jaiclaw.examples.invoice;

import io.jaiclaw.pipeline.ErrorStrategy;
import io.jaiclaw.pipeline.dsl.JaiClawPipeline;
import org.springframework.context.annotation.Configuration;

/**
 * Invoice-processor pipeline definition.
 *
 * <p>FILE trigger watches {@link InvoiceProcessorBeans#INBOX}; each file is
 * routed through five stages and either approved or escalated. Parse failures
 * are routed to a dead-letter log via {@code errorStrategy: DEAD_LETTER}.
 */
@Configuration
public class InvoiceProcessorPipelines extends JaiClawPipeline {

    static final String PIPELINE_ID = "invoice-processor";

    @Override
    public void define() {
        // Camel `file:` URI: watch the inbox; move processed files to .done,
        // failed parses to .error. The trailing `noop=false` ensures Camel
        // actually moves the files (default behaviour).
        String inboxUri = "file:" + InvoiceProcessorBeans.INBOX.toString()
                + "?move=.done&moveFailed=.error&readLock=changed";

        pipeline(PIPELINE_ID)
                .name("Invoice Processor")
                .description("Classify, extract, validate against PO database, approve or flag.")
                .errorStrategy(ErrorStrategy.DEAD_LETTER)
                .deadLetterUri("log:io.jaiclaw.invoices.dlq?level=ERROR&showBody=true")
                .maxRetries(1)
                .trigger().file(inboxUri)
                .then("classify").agent("default")
                    .systemPrompt("""
                        Classify the following document. Reply with exactly:
                          doc_type: <invoice|po|credit_memo|receipt>

                        Document:
                        {{input}}
                        """)
                .then("extract").agent("default")
                    .systemPrompt("""
                        Extract these fields from the invoice text. Reply with exactly these lines:
                          vendor: <vendor name>
                          po_number: <PO-XXXX or empty>
                          amount: <amount in USD>
                          due_date: <YYYY-MM-DD or empty>

                        Source classification:
                        {{stages.classify.output}}

                        Invoice text:
                        {{input}}
                        """)
                .then("validate").processor("invoiceValidator")
                .then("approve-or-flag").agent("default")
                    .systemPrompt("""
                        You are an AP approval bot. Given the extraction and validation, reply with exactly:
                          decision: <AUTO_APPROVED|FLAG_FOR_REVIEW|REJECTED>
                          reason: <one short sentence>

                        Auto-approve if validation: MATCH and amount < 5000. Otherwise flag.

                        Upstream:
                        {{stages.extract.output}}
                        """)
                .then("notify").processor("invoiceNotifier")
                .output()
                    .log()
                    .template("=== INVOICE PROCESSED (executionId={{pipeline.executionId}}) ===\n"
                            + "{{stages.notify.output}}");
    }
}

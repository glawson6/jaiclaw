package io.jaiclaw.examples.contracts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Contract reviewer pipeline. FILE trigger watches a contract inbox; the
 * pipeline classifies clauses, scores risk, generates redlines, runs a
 * compliance scan, and routes the contract to the right approver. Showcases
 * {@code errorStrategy: RETRY_THEN_FAIL} for transient LLM hiccups.
 */
@SpringBootApplication
public class ContractReviewerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractReviewerApplication.class, args);
    }
}

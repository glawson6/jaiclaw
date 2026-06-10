package io.jaiclaw.examples.aiops;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/**
 * Stub PROCESSOR beans for the AIOps incident pipeline.
 *
 * <p>Both are pure-Java string transformations — no external systems —
 * so the example runs without any infrastructure beyond an LLM key.
 * Real deployments would swap these for Kubernetes API / PagerDuty / Jira
 * calls.
 */
@Configuration
public class AiopsIncidentBeans {

    /**
     * Looks for keywords in the upstream runbook output and "executes" a safe
     * action. Always returns the body plus a {@code remediation:} marker.
     */
    @Bean
    public Function<String, String> remediationBean() {
        return body -> {
            String b = body == null ? "" : body;
            String marker;
            if (containsCi(b, "restart")) {
                marker = "remediation: RESTARTED(service-api)";
            } else if (containsCi(b, "cache") || containsCi(b, "evict")) {
                marker = "remediation: CLEARED_CACHE(redis-main)";
            } else if (containsCi(b, "scale")) {
                marker = "remediation: SCALED_UP(replicas=4)";
            } else {
                marker = "remediation: NO_ACTION(no matching runbook keyword)";
            }
            return b + "\n" + marker;
        };
    }

    /**
     * Decides escalation based on the remediation marker. NO_ACTION pages the
     * on-call, anything else auto-resolves.
     */
    @Bean
    public Function<String, String> escalationBean() {
        return body -> {
            String b = body == null ? "" : body;
            if (b.contains("remediation: NO_ACTION")) {
                return "PAGE_ONCALL:\n" + b
                        + "\nescalation: oncall=primary, channel=#oncall, severity=auto-detected";
            }
            return "AUTO_RESOLVED:\n" + b + "\nescalation: closed-with-bot-remediation";
        };
    }

    private static boolean containsCi(String haystack, String needle) {
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }
}

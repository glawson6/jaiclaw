package io.jaiclaw.rules.engine.service.impl;

import io.jaiclaw.rules.engine.facts.DecisionFact;
import io.jaiclaw.rules.engine.facts.TextAnalysisFact;
import io.jaiclaw.rules.engine.facts.ValidationFact;
import io.jaiclaw.rules.engine.model.RuleExecutionRequest;
import io.jaiclaw.rules.engine.model.RuleExecutionResponse;
import io.jaiclaw.rules.engine.service.RuleExecutionService;
import org.kie.api.runtime.StatelessKieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Drools-based implementation of the RuleExecutionService.
 * Executes business rules using Drools engine and returns LLM-friendly results.
 */
public class DroolsRuleExecutionService implements RuleExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(DroolsRuleExecutionService.class);

    private final StatelessKieSession kieSession;

    public DroolsRuleExecutionService(StatelessKieSession kieSession) {
        this.kieSession = kieSession;
        logger.info("DroolsRuleExecutionService initialized");
    }

    @Override
    public RuleExecutionResponse executeRule(RuleExecutionRequest request) {
        try {
            logger.debug("Executing rule: {} with facts: {}", request.getRuleName(), request.getFacts());

            Object fact = createFactFromRequest(request);
            List<Object> facts = new ArrayList<>();
            facts.add(fact);

            kieSession.execute(facts);

            Map<String, Object> results = extractResults(fact, request.getRuleName());
            List<String> messages = new ArrayList<>();
            messages.add("Rule '" + request.getRuleName() + "' executed successfully");

            List<String> trace = request.isEnableTrace() ?
                Arrays.asList("Fact type: " + fact.getClass().getSimpleName(), "Facts processed: 1") : null;

            return RuleExecutionResponse.builder()
                .success(true)
                .ruleName(request.getRuleName())
                .results(results)
                .messages(messages)
                .trace(trace)
                .build();

        } catch (Exception e) {
            logger.error("Error executing rule: {}", request.getRuleName(), e);
            return RuleExecutionResponse.builder()
                .success(false)
                .ruleName(request.getRuleName())
                .results(Map.of())
                .messages(Arrays.asList("Error: " + e.getMessage()))
                .error(e.getMessage())
                .build();
        }
    }

    @Override
    public List<String> listAvailableRules() {
        return Arrays.asList(
            "text-analysis",
            "decision-making",
            "decision",
            "validation"
        );
    }

    @Override
    public boolean isRuleAvailable(String ruleName) {
        if ("decision".equals(ruleName) || "decision-making".equals(ruleName)) {
            return true;
        }
        return listAvailableRules().contains(ruleName);
    }

    private Object createFactFromRequest(RuleExecutionRequest request) {
        String ruleName = request.getRuleName();
        Map<String, Object> facts = request.getFacts();

        switch (ruleName) {
            case "text-analysis":
                String text = (String) facts.getOrDefault("text", "");
                return new TextAnalysisFact(text);

            case "decision":
            case "decision-making":
                @SuppressWarnings("unchecked")
                Map<String, Object> parameters = (Map<String, Object>) facts.getOrDefault("parameters", Map.of());
                return new DecisionFact(parameters);

            case "validation":
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) facts.getOrDefault("data", Map.of());
                if (data.isEmpty()) {
                    data = facts;
                }
                return new ValidationFact(data);

            default:
                throw new IllegalArgumentException("Unknown rule: " + ruleName);
        }
    }

    private Map<String, Object> extractResults(Object fact, String ruleName) {
        Map<String, Object> results = new java.util.HashMap<>();

        if (fact instanceof TextAnalysisFact textFact) {
            results.put("sentiment", textFact.getSentiment());
            if (!textFact.getKeywords().isEmpty()) {
                results.put("keywords", textFact.getKeywords());
            }
            if (!textFact.getCategories().isEmpty()) {
                results.put("categories", textFact.getCategories());
            }
            if (textFact.getSummary() != null) {
                results.put("summary", textFact.getSummary());
            }
        } else if (fact instanceof DecisionFact decisionFact) {
            if (decisionFact.getDecision() != null) {
                results.put("decision", decisionFact.getDecision());
            }
            if (decisionFact.getRecommendation() != null) {
                results.put("recommendation", decisionFact.getRecommendation());
            }
            if (decisionFact.getPriority() != null) {
                results.put("priority", decisionFact.getPriority());
            }
            if (decisionFact.getReasoning() != null) {
                results.put("reasoning", decisionFact.getReasoning());
            }
        } else if (fact instanceof ValidationFact validationFact) {
            results.put("valid", validationFact.isValid());
            if (!validationFact.getErrors().isEmpty()) {
                results.put("errors", validationFact.getErrors());
            }
            if (!validationFact.getWarnings().isEmpty()) {
                results.put("warnings", validationFact.getWarnings());
            }
        }

        return results;
    }
}

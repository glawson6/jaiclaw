package io.jaiclaw.rules.engine.service;

import io.jaiclaw.rules.engine.model.RuleExecutionRequest;
import io.jaiclaw.rules.engine.model.RuleExecutionResponse;

import java.util.List;

/**
 * Service interface for executing business rules.
 */
public interface RuleExecutionService {

    RuleExecutionResponse executeRule(RuleExecutionRequest request);

    boolean isRuleAvailable(String ruleName);

    List<String> listAvailableRules();
}

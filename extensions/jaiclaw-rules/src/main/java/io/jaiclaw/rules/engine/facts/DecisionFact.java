package io.jaiclaw.rules.engine.facts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Fact model for decision rules.
 * Holds input parameters and decision outcomes.
 */
public class DecisionFact {

    private final Map<String, Object> parameters;
    private String decision;
    private String recommendation;
    private Integer priority;
    private String reasoning;

    @JsonCreator
    public DecisionFact(@JsonProperty("parameters") Map<String, Object> parameters) {
        this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public Object getParameter(String key) {
        return parameters.get(key);
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    @Override
    public String toString() {
        return "DecisionFact{" +
                "parameters=" + parameters +
                ", decision='" + decision + '\'' +
                ", recommendation='" + recommendation + '\'' +
                ", priority=" + priority +
                ", reasoning='" + reasoning + '\'' +
                '}';
    }
}

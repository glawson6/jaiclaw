package io.jaiclaw.rules.engine.facts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fact model for validation rules.
 * Holds data to validate and collects validation results.
 */
public class ValidationFact {

    private final Map<String, Object> data;
    private boolean valid = true;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    @JsonCreator
    public ValidationFact(@JsonProperty("data") Map<String, Object> data) {
        this.data = data != null ? new HashMap<>(data) : new HashMap<>();
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Object get(String key) {
        return data.get(key);
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public void invalidate() {
        this.valid = false;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void addError(String error) {
        this.errors.add(error);
        this.valid = false;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    @Override
    public String toString() {
        return "ValidationFact{" +
                "data=" + data +
                ", valid=" + valid +
                ", errors=" + errors +
                ", warnings=" + warnings +
                '}';
    }
}

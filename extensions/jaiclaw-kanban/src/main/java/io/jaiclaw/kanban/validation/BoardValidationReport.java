package io.jaiclaw.kanban.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Consolidated output of {@link BoardValidator} across one or more boards. */
public record BoardValidationReport(List<BoardValidationError> errors) {

    public BoardValidationReport {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String formatted() {
        return errors.stream()
                .map(BoardValidationError::formatted)
                .collect(Collectors.joining("\n"));
    }

    public static final class Builder {
        private final List<BoardValidationError> errors = new ArrayList<>();

        public Builder add(BoardValidationError error) {
            errors.add(error);
            return this;
        }

        public BoardValidationReport build() {
            return new BoardValidationReport(errors);
        }
    }
}

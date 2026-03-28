package io.jaiclaw.perplexity.model;

import java.util.List;

public record SearchApiRequest(
        String query,
        Integer numResults,
        String recencyFilter,
        List<String> domainFilter
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String query;
        private Integer numResults;
        private String recencyFilter;
        private List<String> domainFilter;

        public Builder query(String query) { this.query = query; return this; }
        public Builder numResults(Integer numResults) { this.numResults = numResults; return this; }
        public Builder recencyFilter(String recencyFilter) { this.recencyFilter = recencyFilter; return this; }
        public Builder domainFilter(List<String> domainFilter) { this.domainFilter = domainFilter; return this; }

        public SearchApiRequest build() {
            return new SearchApiRequest(query, numResults, recencyFilter, domainFilter);
        }
    }
}

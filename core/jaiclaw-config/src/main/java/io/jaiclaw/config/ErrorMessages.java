package io.jaiclaw.config;

/**
 * Per-tenant error message customization.
 *
 * @param emptyResponse      message for empty LLM responses
 * @param providerError      message for LLM provider errors
 * @param maxIterations      message when max tool iterations exceeded
 * @param toolExecutionError message for tool execution failures
 * @param generalError       generic fallback error message
 * @param timeout            message for request timeouts
 * @param noTenantContext    message when tenant context is missing
 */
public record ErrorMessages(
        String emptyResponse,
        String providerError,
        String maxIterations,
        String toolExecutionError,
        String generalError,
        String timeout,
        String noTenantContext
) {
    public static final ErrorMessages DEFAULT = new ErrorMessages(
            "I didn't generate a response. Please try again.",
            "I encountered an error communicating with the AI provider.",
            "I reached the maximum number of tool call iterations.",
            "A tool execution error occurred.",
            "I encountered an error processing your message. Please try again.",
            "The request timed out. Please try again.",
            "No tenant context could be resolved for this request."
    );

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String emptyResponse;
        private String providerError;
        private String maxIterations;
        private String toolExecutionError;
        private String generalError;
        private String timeout;
        private String noTenantContext;

        public Builder emptyResponse(String emptyResponse) { this.emptyResponse = emptyResponse; return this; }
        public Builder providerError(String providerError) { this.providerError = providerError; return this; }
        public Builder maxIterations(String maxIterations) { this.maxIterations = maxIterations; return this; }
        public Builder toolExecutionError(String toolExecutionError) { this.toolExecutionError = toolExecutionError; return this; }
        public Builder generalError(String generalError) { this.generalError = generalError; return this; }
        public Builder timeout(String timeout) { this.timeout = timeout; return this; }
        public Builder noTenantContext(String noTenantContext) { this.noTenantContext = noTenantContext; return this; }

        public ErrorMessages build() {
            return new ErrorMessages(emptyResponse, providerError, maxIterations,
                    toolExecutionError, generalError, timeout, noTenantContext);
        }
    }
}

package io.jaiclaw.pipeline;

/**
 * Runtime exception thrown when a pipeline security check fails.
 * Raised by {@link PipelineSecurityGuard} and {@link PipelineTransportAuthenticator}.
 */
public class PipelineSecurityException extends RuntimeException {

    private final String pipelineId;
    private final String stageName;
    private final String reason;

    public PipelineSecurityException(String pipelineId, String stageName, String reason) {
        super(buildMessage(pipelineId, stageName, reason));
        this.pipelineId = pipelineId;
        this.stageName = stageName;
        this.reason = reason;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public String getStageName() {
        return stageName;
    }

    public String getReason() {
        return reason;
    }

    private static String buildMessage(String pipelineId, String stageName, String reason) {
        StringBuilder sb = new StringBuilder("Pipeline security violation");
        if (pipelineId != null) {
            sb.append(" [pipeline=").append(pipelineId).append("]");
        }
        if (stageName != null) {
            sb.append(" [stage=").append(stageName).append("]");
        }
        sb.append(": ").append(reason);
        return sb.toString();
    }
}

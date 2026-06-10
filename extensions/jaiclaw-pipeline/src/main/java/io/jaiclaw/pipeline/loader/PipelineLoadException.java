package io.jaiclaw.pipeline.loader;

/**
 * Thrown when a per-file pipeline definition cannot be parsed. Distinct from
 * {@link io.jaiclaw.pipeline.validation.ValidationReport}-based failures so
 * callers can tell "this file is malformed" from "this pipeline configuration
 * is logically wrong".
 */
public class PipelineLoadException extends RuntimeException {

    public PipelineLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    public PipelineLoadException(String message) {
        super(message);
    }
}

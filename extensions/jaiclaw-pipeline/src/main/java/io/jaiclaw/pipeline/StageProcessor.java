package io.jaiclaw.pipeline;

import org.apache.camel.Exchange;

/**
 * SPI for processing a single pipeline stage. Implementations handle
 * one {@link StageType} (AGENT, PROCESSOR, or CAMEL).
 */
public interface StageProcessor {

    /**
     * Process a stage within a pipeline execution.
     *
     * @param exchange the Camel exchange carrying the message
     * @param stage    the stage definition
     * @param context  the current pipeline context
     * @throws Exception if processing fails
     */
    void process(Exchange exchange, StageDefinition stage, PipelineContext context) throws Exception;
}

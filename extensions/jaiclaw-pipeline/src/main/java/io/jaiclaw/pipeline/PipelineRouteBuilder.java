package io.jaiclaw.pipeline;

import io.jaiclaw.camel.CamelMessageConverter;
import io.jaiclaw.core.tenant.DefaultTenantContext;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core orchestration class that builds Camel routes for each {@link PipelineDefinition}.
 *
 * <p>For each pipeline, creates:
 * <ol>
 *   <li>A trigger route: from trigger URI → initialize PipelineContext → route to stage-0 queue</li>
 *   <li>Per-stage routes: from queue → authenticate → tenant context → stage processor → next queue</li>
 *   <li>An output route: from last stage → deliver to configured output</li>
 * </ol>
 */
public class PipelineRouteBuilder extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(PipelineRouteBuilder.class);
    static final String HEADER_PIPELINE_CONTEXT = "JaiClawPipelineContext";
    static final String HEADER_PIPELINE_START = "JaiClawPipelineStart";

    private final PipelineDefinition definition;
    private final PipelineProperties.PipelineDefaults defaults;
    private final AgentStageProcessor agentProcessor;
    private final BeanStageProcessor beanProcessor;
    private final CamelStageProcessor camelProcessor;
    private final PipelineAuditor auditor;
    private final PipelineHookFirer hookFirer;
    private final PipelineMetrics metrics;
    private final PipelineSecurityGuard securityGuard;
    private final PipelineTransportAuthenticator transportAuthenticator;

    public PipelineRouteBuilder(
            PipelineDefinition definition,
            PipelineProperties.PipelineDefaults defaults,
            AgentStageProcessor agentProcessor,
            BeanStageProcessor beanProcessor,
            CamelStageProcessor camelProcessor,
            PipelineAuditor auditor,
            PipelineHookFirer hookFirer,
            PipelineMetrics metrics,
            PipelineSecurityGuard securityGuard,
            PipelineTransportAuthenticator transportAuthenticator) {
        this.definition = definition;
        this.defaults = defaults != null ? defaults : PipelineProperties.PipelineDefaults.DEFAULT;
        this.agentProcessor = agentProcessor;
        this.beanProcessor = beanProcessor;
        this.camelProcessor = camelProcessor;
        this.auditor = auditor;
        this.hookFirer = hookFirer;
        this.metrics = metrics;
        this.securityGuard = securityGuard;
        this.transportAuthenticator = transportAuthenticator;
    }

    @Override
    public void configure() throws Exception {
        List<StageDefinition> stages = definition.stages();
        if (stages.isEmpty()) {
            log.warn("Pipeline '{}' has no stages, skipping route creation", definition.id());
            return;
        }

        String pipelineId = definition.id();

        // Configure error handling based on error strategy
        configureErrorHandling(pipelineId);

        // Build trigger route
        buildTriggerRoute(pipelineId, stages);

        // Build per-stage routes
        for (int i = 0; i < stages.size(); i++) {
            buildStageRoute(pipelineId, stages, i);
        }

        // Build output route
        buildOutputRoute(pipelineId, stages);

        log.info("Pipeline '{}' routes configured: {} stages, trigger={}, output={}",
                pipelineId, stages.size(), definition.trigger().type(), definition.output().type());
    }

    private void configureErrorHandling(String pipelineId) {
        switch (definition.errorStrategy()) {
            case DEAD_LETTER -> {
                String dlq = definition.deadLetterUri() != null
                        ? definition.deadLetterUri()
                        : "log:io.jaiclaw.pipeline.deadletter." + pipelineId + "?level=ERROR";
                deadLetterChannel(dlq)
                        .maximumRedeliveries(definition.maxRetries())
                        .redeliveryDelay(1000)
                        .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN);
            }
            case RETRY_THEN_FAIL -> {
                errorHandler(defaultErrorHandler()
                        .maximumRedeliveries(definition.maxRetries())
                        .redeliveryDelay(1000)
                        .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));
            }
            case STOP -> {
                // Default: no retries, fail immediately
                errorHandler(defaultErrorHandler().maximumRedeliveries(0));
            }
        }
    }

    private void buildTriggerRoute(String pipelineId, List<StageDefinition> stages) {
        String triggerUri = resolveTriggerUri(pipelineId);
        String firstStageUri = stageQueueUri(pipelineId, 0, stages.get(0));

        from(triggerUri)
                .routeId("pipeline-" + pipelineId + "-trigger")
                .process(exchange -> {
                    // Initialize PipelineContext
                    PipelineContext ctx = new PipelineContext(
                            pipelineId,
                            UUID.randomUUID().toString(),
                            null, // tenantId will be set from TenantContextHolder if available
                            exchange.getExchangeId(),
                            0, stages.size(),
                            null, null,
                            Map.of(), Map.of()
                    );

                    // Resolve tenant context
                    TenantContext tenantCtx = TenantContextHolder.get();
                    if (tenantCtx != null) {
                        ctx = new PipelineContext(
                                ctx.pipelineId(), ctx.executionId(), tenantCtx.getTenantId(),
                                ctx.correlationId(), ctx.stageIndex(), ctx.totalStages(),
                                ctx.replyChannelId(), ctx.replyPeerId(),
                                ctx.stageOutputs(), ctx.metadata()
                        );
                    }

                    // Security validation
                    if (securityGuard != null) {
                        securityGuard.validateExecution(definition, ctx);
                    }

                    exchange.getIn().setHeader(HEADER_PIPELINE_CONTEXT, ctx);
                    exchange.getIn().setHeader(HEADER_PIPELINE_START, Instant.now());

                    // Audit & hooks
                    if (auditor != null) auditor.pipelineStarted(ctx);
                    if (hookFirer != null) hookFirer.firePipelineStart(ctx);
                    if (metrics != null) metrics.recordPipelineActive(pipelineId, 1);
                })
                .to(firstStageUri);
    }

    private void buildStageRoute(String pipelineId, List<StageDefinition> stages, int stageIndex) {
        StageDefinition stage = stages.get(stageIndex);
        String stageUri = stageQueueUri(pipelineId, stageIndex, stage);
        boolean isLast = (stageIndex == stages.size() - 1);
        String nextUri = isLast
                ? "direct:pipeline-" + pipelineId + "-output"
                : stageQueueUri(pipelineId, stageIndex + 1, stages.get(stageIndex + 1));

        from(stageUri)
                .routeId("pipeline-" + pipelineId + "-stage-" + stage.name())
                .process(exchange -> {
                    PipelineContext ctx = exchange.getIn().getHeader(HEADER_PIPELINE_CONTEXT, PipelineContext.class);
                    if (ctx == null) {
                        throw new IllegalStateException("Missing PipelineContext header at stage " + stage.name());
                    }

                    // Transport authentication
                    if (stage.transport() != null && stage.transport().auth() != null && transportAuthenticator != null) {
                        Map<String, String> headers = extractHeaders(exchange);
                        transportAuthenticator.verify(stage.transport().auth(),
                                exchange.getIn().getBody(String.class),
                                headers, pipelineId, stage.name());
                    }

                    // Set tenant context for this stage
                    if (ctx.tenantId() != null) {
                        TenantContextHolder.set(new DefaultTenantContext(ctx.tenantId(), ctx.tenantId()));
                    }

                    // Security: validate stage input
                    if (securityGuard != null) {
                        securityGuard.validateStageInput(stage.name(),
                                exchange.getIn().getBody(String.class), stage.type());
                    }

                    // Audit & hooks: stage start
                    if (auditor != null) auditor.stageStarted(ctx, stage);
                    if (hookFirer != null) hookFirer.fireStageStart(ctx, stage);

                    Instant stageStart = Instant.now();

                    try {
                        // Dispatch to appropriate processor
                        StageProcessor processor = resolveProcessor(stage.type());
                        processor.process(exchange, stage, ctx);

                        String output = exchange.getIn().getBody(String.class);

                        // Security: validate stage output
                        if (securityGuard != null) {
                            output = securityGuard.validateStageOutput(stage.name(), output);
                            exchange.getIn().setBody(output);
                        }

                        // Advance context
                        PipelineContext nextCtx = ctx.nextStage(stage.name(), output != null ? output : "");
                        exchange.getIn().setHeader(HEADER_PIPELINE_CONTEXT, nextCtx);

                        Duration stageDuration = Duration.between(stageStart, Instant.now());

                        // Audit & hooks: stage complete
                        if (auditor != null) auditor.stageCompleted(nextCtx, stage, stageDuration);
                        if (hookFirer != null) hookFirer.fireStageComplete(nextCtx, stage, output);
                        if (metrics != null) metrics.recordStageExecution(
                                pipelineId, stage.name(), stage.type().name(), true, stageDuration);

                    } catch (Exception e) {
                        Duration stageDuration = Duration.between(stageStart, Instant.now());
                        if (auditor != null) auditor.stageFailed(ctx, stage, e);
                        if (metrics != null) metrics.recordStageExecution(
                                pipelineId, stage.name(), stage.type().name(), false, stageDuration);
                        throw e;
                    } finally {
                        if (ctx.tenantId() != null) {
                            TenantContextHolder.clear();
                        }
                    }
                })
                .to(nextUri);
    }

    private void buildOutputRoute(String pipelineId, List<StageDefinition> stages) {
        String outputRouteUri = "direct:pipeline-" + pipelineId + "-output";
        OutputDefinition output = definition.output();

        from(outputRouteUri)
                .routeId("pipeline-" + pipelineId + "-output")
                .process(exchange -> {
                    PipelineContext ctx = exchange.getIn().getHeader(HEADER_PIPELINE_CONTEXT, PipelineContext.class);
                    Instant pipelineStart = exchange.getIn().getHeader(HEADER_PIPELINE_START, Instant.class);

                    // Resolve output template
                    if (output.template() != null && ctx != null) {
                        String resolved = TemplateResolver.resolve(output.template(), ctx.stageOutputs());
                        exchange.getIn().setBody(resolved);
                    }

                    // Record pipeline completion
                    if (ctx != null) {
                        Duration totalDuration = pipelineStart != null
                                ? Duration.between(pipelineStart, Instant.now())
                                : Duration.ZERO;
                        if (auditor != null) auditor.pipelineCompleted(ctx, totalDuration);
                        if (hookFirer != null) hookFirer.firePipelineEnd(ctx);
                        if (metrics != null) {
                            metrics.recordPipelineExecution(pipelineId, ctx.tenantId(), true, totalDuration);
                            metrics.recordPipelineActive(pipelineId, -1);
                        }
                    }
                })
                .choice()
                    .when(constant(output.type() == OutputType.CHANNEL && output.channelId() != null))
                        .to("log:io.jaiclaw.pipeline.output." + pipelineId + "?level=INFO&showBody=true")
                    .when(constant(output.type() == OutputType.CAMEL_URI && output.uri() != null))
                        .toD(output.uri() != null ? output.uri() : "log:pipeline-output")
                    .when(constant(output.type() == OutputType.LOG))
                        .to("log:io.jaiclaw.pipeline.output." + pipelineId + "?level=INFO&showBody=true")
                    .otherwise()
                        .to("log:io.jaiclaw.pipeline.output." + pipelineId + "?level=DEBUG&showBody=false")
                .end();
    }

    private StageProcessor resolveProcessor(StageType type) {
        return switch (type) {
            case AGENT -> {
                if (agentProcessor == null) {
                    throw new IllegalStateException("AgentStageProcessor not available — is GatewayServiceAccessor configured?");
                }
                yield agentProcessor;
            }
            case PROCESSOR -> {
                if (beanProcessor == null) {
                    throw new IllegalStateException("BeanStageProcessor not available");
                }
                yield beanProcessor;
            }
            case CAMEL -> {
                if (camelProcessor == null) {
                    throw new IllegalStateException("CamelStageProcessor not available");
                }
                yield camelProcessor;
            }
        };
    }

    private String resolveTriggerUri(String pipelineId) {
        TriggerDefinition trigger = definition.trigger();
        return switch (trigger.type()) {
            case FILE -> trigger.uri() != null ? trigger.uri() : "file://pipeline-" + pipelineId + "-input";
            case CRON -> "timer:pipeline-" + pipelineId + "?period=" + trigger.expression();
            case HTTP -> "direct:pipeline-" + pipelineId + "-http";
            case CAMEL_URI -> trigger.uri() != null ? trigger.uri() : "direct:pipeline-" + pipelineId;
            case MANUAL -> "direct:pipeline-" + pipelineId;
        };
    }

    String stageQueueUri(String pipelineId, int stageIndex, StageDefinition stage) {
        // Per-stage transport override
        if (stage.transport() != null) {
            return stage.transport().uri();
        }

        // Default SEDA with configurable params
        return String.format("seda:pipeline-%s-stage-%d?size=%d&concurrentConsumers=%d&blockWhenFull=%s",
                pipelineId, stageIndex,
                defaults.sedaSize(), defaults.concurrentConsumers(), defaults.blockWhenFull());
    }

    private Map<String, String> extractHeaders(Exchange exchange) {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, Object> entry : exchange.getIn().getHeaders().entrySet()) {
            if (entry.getValue() instanceof String value) {
                headers.put(entry.getKey(), value);
            }
        }
        return headers;
    }
}

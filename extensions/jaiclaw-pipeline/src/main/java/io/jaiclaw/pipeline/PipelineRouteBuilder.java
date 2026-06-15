package io.jaiclaw.pipeline;

import io.jaiclaw.camel.CamelMessageConverter;
import io.jaiclaw.core.tenant.DefaultTenantContext;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.pipeline.gateway.DefaultPipelineGateway;
import io.jaiclaw.pipeline.gateway.PipelineExecutionResult;
import io.jaiclaw.pipeline.gateway.PipelineSyncCoordinator;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private final PipelineExecutionTracker tracker;
    private final PipelineSyncCoordinator syncCoordinator;

    /** Legacy 10-arg constructor — kept for callers that don't supply a tracker. */
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
        this(definition, defaults, agentProcessor, beanProcessor, camelProcessor,
                auditor, hookFirer, metrics, securityGuard, transportAuthenticator, null, null);
    }

    /** 11-arg constructor — kept for callers that don't supply a sync coordinator. */
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
            PipelineTransportAuthenticator transportAuthenticator,
            PipelineExecutionTracker tracker) {
        this(definition, defaults, agentProcessor, beanProcessor, camelProcessor,
                auditor, hookFirer, metrics, securityGuard, transportAuthenticator, tracker, null);
    }

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
            PipelineTransportAuthenticator transportAuthenticator,
            PipelineExecutionTracker tracker,
            PipelineSyncCoordinator syncCoordinator) {
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
        this.tracker = tracker;
        this.syncCoordinator = syncCoordinator;
    }

    @Override
    public void configure() throws Exception {
        List<StageDefinition> stages = definition.stages();
        if (stages.isEmpty()) {
            log.warn("Pipeline '{}' has no stages, skipping route creation", definition.id());
            return;
        }

        String pipelineId = definition.id();

        // onException must be declared before route definitions / errorHandler.
        // Completes any pending sync future with status=FAILED when a stage
        // throws or a pre-stage check (transport-auth, security-guard) fails.
        configureSyncFailureHandler();

        // Configure error handling based on error strategy
        configureErrorHandling(pipelineId);

        // Build trigger route
        buildTriggerRoute(pipelineId, stages);

        // Ensure direct:pipeline-<id> is always callable so the PipelineGateway
        // works for HTTP/CRON/FILE pipelines too (not just MANUAL/CAMEL_URI).
        buildGatewayConvergenceRoute(pipelineId);

        // Build per-stage routes
        for (int i = 0; i < stages.size(); i++) {
            buildStageRoute(pipelineId, stages, i);
        }

        // Build output route
        buildOutputRoute(pipelineId, stages);

        log.info("Pipeline '{}' routes configured: {} stages, trigger={}, output={}",
                pipelineId, stages.size(), definition.trigger().type(), definition.output().type());
    }

    /**
     * If a sync execution fails anywhere in the route, complete its registered
     * future with a FAILED-status {@link PipelineExecutionResult}. Lets the
     * configured {@code errorHandler} / DLQ still apply by returning
     * {@code continued(false)}.
     */
    private void configureSyncFailureHandler() {
        if (syncCoordinator == null) return;
        onException(Exception.class)
                .process(exchange -> {
                    Boolean syncRequested = exchange.getIn().getHeader(
                            DefaultPipelineGateway.HEADER_SYNC_REQUESTED, Boolean.class);
                    if (!Boolean.TRUE.equals(syncRequested)) return;
                    PipelineContext ctx = exchange.getIn().getHeader(
                            HEADER_PIPELINE_CONTEXT, PipelineContext.class);
                    if (ctx == null) return;
                    Throwable cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                    String reason = cause != null && cause.getMessage() != null
                            ? cause.getMessage()
                            : (cause != null ? cause.getClass().getSimpleName() : "unknown failure");
                    Instant startedAt = exchange.getIn().getHeader(HEADER_PIPELINE_START, Instant.class);
                    Instant now = Instant.now();
                    Duration totalDuration = startedAt != null
                            ? Duration.between(startedAt, now)
                            : Duration.ZERO;
                    PipelineExecutionResult result = PipelineExecutionResult.failure(
                            ctx, reason,
                            startedAt != null ? startedAt : now,
                            now,
                            totalDuration);
                    syncCoordinator.complete(ctx.executionId(), result);
                })
                .continued(false);
    }

    private void configureErrorHandling(String pipelineId) {
        switch (definition.errorStrategy()) {
            case DEAD_LETTER -> {
                String dlq;
                if (definition.deadLetterUri() != null && !definition.deadLetterUri().isBlank()) {
                    dlq = definition.deadLetterUri();
                } else if (defaults.deadLetterUri() != null && !defaults.deadLetterUri().isBlank()) {
                    dlq = defaults.deadLetterUri();
                } else {
                    dlq = "log:io.jaiclaw.pipeline.deadletter." + pipelineId + "?level=ERROR";
                }
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

    /**
     * For pipelines whose primary trigger URI isn't {@code direct:pipeline-<id>}
     * (HTTP/CRON/FILE), also register a {@code direct:pipeline-<id>} endpoint
     * that forwards into the same initialization logic. This is what
     * {@link io.jaiclaw.pipeline.gateway.PipelineGateway} sends to.
     */
    private void buildGatewayConvergenceRoute(String pipelineId) {
        String primaryUri = resolveTriggerUri(pipelineId);
        String gatewayUri = "direct:pipeline-" + pipelineId;
        if (primaryUri.equals(gatewayUri)) {
            return;
        }
        // Route into the same per-stage flow as the primary trigger by initializing
        // a fresh PipelineContext, identical to what buildTriggerRoute does.
        from(gatewayUri)
                .routeId("pipeline-" + pipelineId + "-gateway")
                .process(exchange -> {
                    // Same context-init as buildTriggerRoute. Kept inline (rather than
                    // refactored) to avoid changing the primary trigger semantics.
                    String triggerInput = exchange.getIn().getBody(String.class);
                    Map<String, String> initialMetadata = Map.of();
                    if (triggerInput != null) {
                        String stored = triggerInput.length() > PipelineContext.MAX_INPUT_BYTES
                                ? triggerInput.substring(0, PipelineContext.MAX_INPUT_BYTES) + "…[truncated]"
                                : triggerInput;
                        initialMetadata = Map.of(PipelineContext.INPUT_METADATA_KEY, stored);
                    }
                    String gatewayExecutionId = exchange.getIn().getHeader(
                            DefaultPipelineGateway.HEADER_GATEWAY_EXECUTION_ID, String.class);
                    String gatewayTenantId = exchange.getIn().getHeader(
                            DefaultPipelineGateway.HEADER_TENANT_ID, String.class);
                    String gatewayCorrelationId = exchange.getIn().getHeader(
                            DefaultPipelineGateway.HEADER_CORRELATION_ID, String.class);

                    PipelineContext ctx = new PipelineContext(
                            pipelineId,
                            gatewayExecutionId != null ? gatewayExecutionId : UUID.randomUUID().toString(),
                            gatewayTenantId,
                            gatewayCorrelationId != null ? gatewayCorrelationId : exchange.getExchangeId(),
                            0, definition.stages().size(),
                            null, null, Map.of(), initialMetadata);
                    TenantContext tenantCtx = TenantContextHolder.get();
                    if (tenantCtx != null && ctx.tenantId() == null) {
                        ctx = new PipelineContext(
                                ctx.pipelineId(), ctx.executionId(), tenantCtx.getTenantId(),
                                ctx.correlationId(), ctx.stageIndex(), ctx.totalStages(),
                                ctx.replyChannelId(), ctx.replyPeerId(),
                                ctx.stageOutputs(), ctx.metadata());
                    }
                    if (securityGuard != null) {
                        securityGuard.validateExecution(definition, ctx);
                    }
                    exchange.getIn().setHeader(HEADER_PIPELINE_CONTEXT, ctx);
                    exchange.getIn().setHeader(HEADER_PIPELINE_START, Instant.now());
                    if (auditor != null) auditor.pipelineStarted(ctx);
                    if (hookFirer != null) hookFirer.firePipelineStart(ctx);
                    if (metrics != null) metrics.recordPipelineActive(pipelineId, 1);
                    if (tracker != null) tracker.started(ctx);
                })
                .to(stageQueueUri(pipelineId, 0, definition.stages().get(0)));
    }

    private void buildTriggerRoute(String pipelineId, List<StageDefinition> stages) {
        String triggerUri = resolveTriggerUri(pipelineId);
        String firstStageUri = stageQueueUri(pipelineId, 0, stages.get(0));

        from(triggerUri)
                .routeId("pipeline-" + pipelineId + "-trigger")
                .process(exchange -> {
                    // Capture the original trigger payload so {{input}} placeholders
                    // resolve through every stage hop. Truncate to bound memory.
                    String triggerInput = exchange.getIn().getBody(String.class);
                    Map<String, String> initialMetadata = Map.of();
                    if (triggerInput != null) {
                        String stored = triggerInput.length() > PipelineContext.MAX_INPUT_BYTES
                                ? triggerInput.substring(0, PipelineContext.MAX_INPUT_BYTES) + "…[truncated]"
                                : triggerInput;
                        initialMetadata = Map.of(PipelineContext.INPUT_METADATA_KEY, stored);
                    }

                    // Initialize PipelineContext — prefer gateway-supplied IDs when present
                    // so the handle returned by PipelineGateway.trigger() matches the
                    // executionId surfaced by the tracker and the actuator endpoint.
                    String gatewayExecutionId = exchange.getIn().getHeader(
                            DefaultPipelineGateway.HEADER_GATEWAY_EXECUTION_ID, String.class);
                    String gatewayTenantId = exchange.getIn().getHeader(
                            DefaultPipelineGateway.HEADER_TENANT_ID, String.class);
                    String gatewayCorrelationId = exchange.getIn().getHeader(
                            DefaultPipelineGateway.HEADER_CORRELATION_ID, String.class);

                    PipelineContext ctx = new PipelineContext(
                            pipelineId,
                            gatewayExecutionId != null ? gatewayExecutionId : UUID.randomUUID().toString(),
                            gatewayTenantId,
                            gatewayCorrelationId != null ? gatewayCorrelationId : exchange.getExchangeId(),
                            0, stages.size(),
                            null, null,
                            Map.of(), initialMetadata
                    );

                    // Resolve tenant context — only override if no gateway-supplied tenant was present.
                    TenantContext tenantCtx = TenantContextHolder.get();
                    if (tenantCtx != null && ctx.tenantId() == null) {
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
                    if (tracker != null) tracker.started(ctx);
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
                    if (tracker != null) tracker.stageStarted(ctx, stage.name());

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
                        if (tracker != null) tracker.stageCompleted(nextCtx, stage.name(), stageDuration);

                    } catch (Exception e) {
                        Duration stageDuration = Duration.between(stageStart, Instant.now());
                        if (auditor != null) auditor.stageFailed(ctx, stage, e);
                        if (metrics != null) metrics.recordStageExecution(
                                pipelineId, stage.name(), stage.type().name(), false, stageDuration);
                        if (tracker != null) tracker.failed(ctx, e.getMessage(), stageDuration);
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

                    // Resolve output template (stage outputs + {{input}} + {{pipeline.*}})
                    if (output.template() != null && ctx != null) {
                        String resolved = TemplateResolver.resolve(output.template(), ctx);
                        exchange.getIn().setBody(resolved);
                    }

                    // Record pipeline completion
                    if (ctx != null) {
                        Instant now = Instant.now();
                        Duration totalDuration = pipelineStart != null
                                ? Duration.between(pipelineStart, now)
                                : Duration.ZERO;
                        if (auditor != null) auditor.pipelineCompleted(ctx, totalDuration);
                        if (hookFirer != null) hookFirer.firePipelineEnd(ctx);
                        if (metrics != null) {
                            metrics.recordPipelineExecution(pipelineId, ctx.tenantId(), true, totalDuration);
                            metrics.recordPipelineActive(pipelineId, -1);
                        }
                        if (tracker != null) tracker.succeeded(ctx, totalDuration);

                        // Complete the registered sync future *before* dispatching to
                        // the external output sink (CHANNEL/CAMEL_URI/LOG). Sync callers
                        // get the result without waiting on external delivery; the side
                        // effect still fires below via .choice().
                        Boolean syncRequested = exchange.getIn().getHeader(
                                DefaultPipelineGateway.HEADER_SYNC_REQUESTED, Boolean.class);
                        if (Boolean.TRUE.equals(syncRequested) && syncCoordinator != null) {
                            PipelineExecutionResult result = PipelineExecutionResult.success(
                                    ctx,
                                    pipelineStart != null ? pipelineStart : now,
                                    now,
                                    totalDuration);
                            syncCoordinator.complete(ctx.executionId(), result);
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

    String resolveTriggerUri(String pipelineId) {
        TriggerDefinition trigger = definition.trigger();
        return switch (trigger.type()) {
            case FILE -> trigger.uri() != null ? trigger.uri() : "file://pipeline-" + pipelineId + "-input";
            case CRON -> {
                String expr = trigger.expression();
                if (expr == null || expr.isBlank()) {
                    throw new IllegalStateException(
                            "Pipeline '" + pipelineId + "' has trigger.type=CRON but no cron expression");
                }
                // Camel quartz cron expressions use a 6-7 field cron format; URL-encode
                // so spaces and '?' characters survive the URI.
                yield "quartz://jaiclaw-pipelines/" + pipelineId
                        + "?cron=" + URLEncoder.encode(expr, StandardCharsets.UTF_8);
            }
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

package io.jaiclaw.pipeline.validation;

import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.pipeline.ErrorStrategy;
import io.jaiclaw.pipeline.OutputDefinition;
import io.jaiclaw.pipeline.OutputType;
import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.PipelineProperties;
import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.pipeline.StageDefinition;
import io.jaiclaw.pipeline.StageRuntime;
import io.jaiclaw.pipeline.StageType;
import io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort;
import io.jaiclaw.tools.bridge.embabel.WorkflowDescriptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Validates every registered pipeline at startup and produces a consolidated
 * {@link ValidationReport}. Failing fast here is far friendlier than letting
 * misconfigured pipelines crash mid-execution with cryptic Camel errors.
 *
 * <p>Checks performed (skipped silently when their backing bean is absent):
 * <ul>
 *   <li>Every {@code {{stages.X.output}}} / {@code {{stages.X.metadata.k}}}
 *       placeholder in a {@code systemPrompt} or {@code output.template}
 *       references a stage defined in the same pipeline.</li>
 *   <li>For each {@link StageType#PROCESSOR} stage: the {@code bean} name
 *       exists in the Spring context and its type is assignable to
 *       {@code Function<String,String>}.</li>
 *   <li>For {@link OutputType#CHANNEL} outputs: {@code channelId} resolves in
 *       {@link ChannelRegistry} when that bean is present.</li>
 *   <li>If {@link ErrorStrategy#DEAD_LETTER} is used, {@code deadLetterUri} is
 *       provided on the pipeline or as a global default.</li>
 * </ul>
 *
 * <p>The AGENT-stage {@code agentId} check is intentionally not implemented:
 * the runtime currently looks agents up through
 * {@code GatewayServiceAccessor} (a function, not an enumerable registry), so
 * we have no way to know which agentIds are valid at startup.
 */
public class PipelineValidator {

    static final int SUGGESTION_MAX_DISTANCE = 2;

    private final PipelineRegistry registry;
    private final PipelineProperties properties;
    private final ApplicationContext applicationContext;
    private final ObjectProvider<ChannelRegistry> channelRegistryProvider;
    private final ObjectProvider<AgentOrchestrationPort> orchestrationPortProvider;

    public PipelineValidator(
            PipelineRegistry registry,
            PipelineProperties properties,
            ApplicationContext applicationContext,
            ObjectProvider<ChannelRegistry> channelRegistryProvider) {
        this(registry, properties, applicationContext, channelRegistryProvider, null);
    }

    public PipelineValidator(
            PipelineRegistry registry,
            PipelineProperties properties,
            ApplicationContext applicationContext,
            ObjectProvider<ChannelRegistry> channelRegistryProvider,
            ObjectProvider<AgentOrchestrationPort> orchestrationPortProvider) {
        this.registry = registry;
        this.properties = properties;
        this.applicationContext = applicationContext;
        this.channelRegistryProvider = channelRegistryProvider;
        this.orchestrationPortProvider = orchestrationPortProvider;
    }

    /** Run validation and return the report. Does not throw. */
    public ValidationReport validate() {
        ValidationReport.Builder report = new ValidationReport.Builder();
        ChannelRegistry channelRegistry = channelRegistryProvider != null
                ? channelRegistryProvider.getIfAvailable()
                : null;
        AgentOrchestrationPort orchestrationPort = orchestrationPortProvider != null
                ? orchestrationPortProvider.getIfAvailable()
                : null;
        String defaultDeadLetterUri = properties != null && properties.defaults() != null
                ? properties.defaults().deadLetterUri()
                : null;

        for (PipelineDefinition definition : registry.getAll()) {
            validatePipeline(definition, channelRegistry, orchestrationPort, defaultDeadLetterUri, report);
        }
        return report.build();
    }

    /** Run validation and throw {@link IllegalStateException} if any error is found. */
    public void validateOrThrow() {
        ValidationReport report = validate();
        if (report.hasErrors()) {
            throw new IllegalStateException(report.formatted());
        }
    }

    private void validatePipeline(
            PipelineDefinition definition,
            ChannelRegistry channelRegistry,
            AgentOrchestrationPort orchestrationPort,
            String defaultDeadLetterUri,
            ValidationReport.Builder report) {

        String pipelineId = definition.id();
        Set<String> stageNames = new TreeSet<>();
        for (StageDefinition stage : definition.stages()) {
            stageNames.add(stage.name());
        }

        // 1. Placeholder references in stage systemPrompts.
        for (StageDefinition stage : definition.stages()) {
            validatePlaceholders(
                    pipelineId,
                    "stage '" + stage.name() + "'",
                    "systemPrompt",
                    stage.systemPrompt(),
                    stageNames,
                    report);

            // 2. PROCESSOR bean existence + type.
            if (stage.type() == StageType.PROCESSOR) {
                validateProcessorBean(pipelineId, stage, report);
            }

            // 2b. EMBABEL AGENT-stage runtime availability + workflow lookup.
            if (stage.type() == StageType.AGENT && stage.runtime() == StageRuntime.EMBABEL) {
                validateEmbabelStage(pipelineId, stage, orchestrationPort, report);
            }
        }

        // 3. Output template placeholders + channelId.
        OutputDefinition output = definition.output();
        if (output != null) {
            validatePlaceholders(
                    pipelineId,
                    "output",
                    "template",
                    output.template(),
                    stageNames,
                    report);

            if (output.type() == OutputType.CHANNEL && channelRegistry != null) {
                String channelId = output.channelId();
                if (channelId != null && !channelId.isBlank()
                        && !"pipeline-internal".equals(channelId)
                        && !channelRegistry.contains(channelId)) {
                    Optional<String> suggestion = Levenshtein.suggest(
                            channelId, channelRegistry.channelIds(), SUGGESTION_MAX_DISTANCE);
                    String registered = channelRegistry.channelIds().isEmpty()
                            ? "(none registered)"
                            : new TreeSet<>(channelRegistry.channelIds()).toString();
                    report.addPipelineError(pipelineId, new ValidationError(
                            pipelineId,
                            "output",
                            "UNKNOWN_CHANNEL",
                            "channelId '" + channelId + "' not found in ChannelRegistry — registered channels: " + registered,
                            suggestion.orElse(null)));
                }
            }
        }

        // 4. Dead-letter URI requirement.
        if (definition.errorStrategy() == ErrorStrategy.DEAD_LETTER
                && isBlank(definition.deadLetterUri())
                && isBlank(defaultDeadLetterUri)) {
            report.addPipelineError(pipelineId, new ValidationError(
                    pipelineId,
                    "errorStrategy",
                    "MISSING_DEAD_LETTER_URI",
                    "errorStrategy=DEAD_LETTER requires a deadLetterUri on the pipeline "
                            + "or a default at jaiclaw.pipeline.defaults.deadLetterUri",
                    null));
        }
    }

    private void validatePlaceholders(
            String pipelineId,
            String location,
            String fieldName,
            String template,
            Set<String> definedStages,
            ValidationReport.Builder report) {

        if (template == null || template.isBlank()) return;

        for (PlaceholderScanner.Placeholder placeholder : PlaceholderScanner.scan(template)) {
            switch (placeholder.kind()) {
                case STAGE_OUTPUT, STAGE_METADATA -> {
                    String referenced = placeholder.stage();
                    if (!definedStages.contains(referenced)) {
                        Optional<String> suggestion = Levenshtein.suggest(
                                referenced, definedStages, SUGGESTION_MAX_DISTANCE);
                        report.addPipelineError(pipelineId, new ValidationError(
                                pipelineId,
                                location,
                                "UNKNOWN_STAGE_REF",
                                fieldName + " references unknown stage '" + referenced + "'",
                                suggestion.orElse(null)));
                    }
                }
                case UNKNOWN -> report.addPipelineError(pipelineId, new ValidationError(
                        pipelineId,
                        location,
                        "UNKNOWN_PLACEHOLDER",
                        fieldName + " contains unrecognised placeholder '" + placeholder.raw() + "'",
                        null));
                default -> { /* PIPELINE_VAR and INPUT are always valid */ }
            }
        }
    }

    private void validateProcessorBean(
            String pipelineId,
            StageDefinition stage,
            ValidationReport.Builder report) {
        if (applicationContext == null) return;
        String beanName = stage.bean();
        if (beanName == null || beanName.isBlank()) {
            report.addPipelineError(pipelineId, new ValidationError(
                    pipelineId,
                    "stage '" + stage.name() + "'",
                    "MISSING_BEAN_NAME",
                    "PROCESSOR stage requires a non-blank bean name",
                    null));
            return;
        }
        if (!applicationContext.containsBean(beanName)) {
            List<String> functionBeans = collectFunctionBeanNames();
            Optional<String> suggestion = Levenshtein.suggest(beanName, functionBeans, SUGGESTION_MAX_DISTANCE);
            report.addPipelineError(pipelineId, new ValidationError(
                    pipelineId,
                    "stage '" + stage.name() + "'",
                    "UNKNOWN_BEAN",
                    "PROCESSOR bean '" + beanName + "' not found in ApplicationContext",
                    suggestion.orElse(null)));
            return;
        }
        Class<?> type = applicationContext.getType(beanName);
        if (type != null && !Function.class.isAssignableFrom(type)) {
            report.addPipelineError(pipelineId, new ValidationError(
                    pipelineId,
                    "stage '" + stage.name() + "'",
                    "WRONG_BEAN_TYPE",
                    "PROCESSOR bean '" + beanName + "' must implement Function<String,String> "
                            + "but is " + type.getName(),
                    null));
        }
    }

    private void validateEmbabelStage(
            String pipelineId,
            StageDefinition stage,
            AgentOrchestrationPort orchestrationPort,
            ValidationReport.Builder report) {
        if (orchestrationPort == null || !orchestrationPort.isAvailable()) {
            report.addPipelineError(pipelineId, new ValidationError(
                    pipelineId,
                    "stage '" + stage.name() + "'",
                    "EMBABEL_RUNTIME_UNAVAILABLE",
                    "stage requests runtime=EMBABEL but no AgentOrchestrationPort bean is available — "
                            + "add jaiclaw-starter-embabel (or another AgentOrchestrationPort impl) to the classpath",
                    null));
            return;
        }
        String workflow = stage.embabelWorkflow();
        // Compact constructor on StageDefinition already enforces non-blank; this
        // is belt-and-braces in case a future record-binding path skips it.
        if (workflow == null || workflow.isBlank()) {
            report.addPipelineError(pipelineId, new ValidationError(
                    pipelineId,
                    "stage '" + stage.name() + "'",
                    "MISSING_EMBABEL_WORKFLOW",
                    "stage requests runtime=EMBABEL but embabelWorkflow is blank",
                    null));
            return;
        }
        List<String> available = orchestrationPort.listWorkflows().stream()
                .map(WorkflowDescriptor::name)
                .toList();
        if (!available.contains(workflow)) {
            Optional<String> suggestion = Levenshtein.suggest(
                    workflow, available, SUGGESTION_MAX_DISTANCE);
            String known = available.isEmpty()
                    ? "(none registered)"
                    : new TreeSet<>(available).toString();
            report.addPipelineError(pipelineId, new ValidationError(
                    pipelineId,
                    "stage '" + stage.name() + "'",
                    "UNKNOWN_EMBABEL_WORKFLOW",
                    "embabelWorkflow '" + workflow + "' not found in "
                            + orchestrationPort.platformName() + " — registered: " + known,
                    suggestion.orElse(null)));
        }
    }

    private List<String> collectFunctionBeanNames() {
        if (applicationContext == null) return List.of();
        try {
            return new ArrayList<>(List.of(applicationContext.getBeanNamesForType(Function.class)));
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

package io.jaiclaw.pipeline;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring bean as a first-class pipeline processor — one that
 * carries its own metadata (display name, category, description, config
 * schema) and can be surfaced in the Pipeline Studio catalog + palette
 * without operator-managed configuration.
 *
 * <p>The annotated class must implement {@link ConfigurableStageProcessor}.
 * At startup, {@code PipelineCatalogService} (in
 * {@code jaiclaw-pipeline-authoring}) discovers every bean carrying
 * this annotation, projects its metadata into the {@code /api/pipeline-studio/catalog}
 * response, and lets the Studio SPA render a palette node whose
 * inspector form is driven by {@link #configSchema()}.
 *
 * <p>Bare {@code Function<String, String>} bean processors continue to
 * work — {@link BeanStageProcessor} handles them — but they appear in
 * the palette as "custom bean" nodes with a bean name and nothing else.
 * Add {@code @PipelineProcessor} + implement
 * {@link ConfigurableStageProcessor} to unlock the full node UX.
 *
 * @see ConfigurableStageProcessor
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PipelineProcessor {

    /**
     * Palette display name. Should be short and human-readable
     * (e.g. "Regex Extract", not "regexExtractProcessor").
     */
    String name();

    /**
     * Palette grouping category. Shipped conventions:
     * {@code "Transform"}, {@code "Validate"}, {@code "Control"},
     * {@code "Data"}, {@code "Integration"}. Adopters may introduce
     * new categories freely; the catalog surfaces whatever it finds.
     */
    String category() default "Custom";

    /**
     * One-sentence description shown as palette hover text and as the
     * inspector's default help copy.
     */
    String description() default "";

    /**
     * Optional icon identifier for the palette. Interpretation is
     * frontend-defined — the Studio SPA maps well-known icon names
     * (e.g. {@code "regex"}, {@code "json"}, {@code "http"}) to
     * shipped SVGs. Blank = default node icon.
     */
    String icon() default "";
}

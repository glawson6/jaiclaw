package io.jaiclaw.blueprints.pipeline;

import io.jaiclaw.blueprints.BlueprintAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers the built-in {@link PipelineAuthorBlueprints} so it appears in
 * the {@link io.jaiclaw.blueprints.BlueprintRegistry} without adopters wiring
 * anything. Fires only when the parent module opts in via
 * {@code jaiclaw.blueprints.enabled=true} and only if no adopter has already
 * declared their own {@code pipeline-author} provider (matches the module's
 * first-writer-wins conflict rule).
 */
@AutoConfiguration(after = BlueprintAutoConfiguration.class)
@ConditionalOnProperty(name = "jaiclaw.blueprints.enabled", havingValue = "true")
public class PipelineAuthorBlueprintsConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PipelineAuthorBlueprints pipelineAuthorBlueprints() {
        return new PipelineAuthorBlueprints();
    }
}

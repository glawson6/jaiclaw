package io.jaiclaw.compaction;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for compaction beans including JTokkit-based
 * token estimation and tool result compression.
 */
@AutoConfiguration
public class CompactionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TokenEstimator.class)
    @ConditionalOnClass(name = "com.knuddels.jtokkit.Encodings")
    public JtokkitTokenEstimator jtokkitTokenEstimator() {
        return new JtokkitTokenEstimator();
    }

    @Bean
    @ConditionalOnMissingBean(ToolResultCompressor.class)
    public TruncatingToolResultCompressor truncatingToolResultCompressor() {
        return new TruncatingToolResultCompressor();
    }
}

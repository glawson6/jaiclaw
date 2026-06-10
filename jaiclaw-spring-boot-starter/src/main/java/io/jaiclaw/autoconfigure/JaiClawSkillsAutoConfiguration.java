package io.jaiclaw.autoconfigure;

import io.jaiclaw.skills.SkillLoader;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Skills loader.
 *
 * <p>Beans defined here:
 * <ul>
 *   <li>{@link SkillLoader} — loads bundled + workspace SKILL.md files based
 *       on {@code jaiclaw.skills.allow-bundled} and
 *       {@code jaiclaw.skills.workspace-dir} configuration.</li>
 * </ul>
 *
 * <p>Runs after {@link JaiClawMemoryAutoConfiguration}; placed here in the
 * DAG so the agent auto-config that consumes {@link SkillLoader} has all of
 * memory + plugins + tools fully wired by then.
 *
 * <p>Carved out of the former {@code JaiClawAutoConfiguration} monolith
 * (audit {@code CODEBASE-ANALYSIS-2026-06-10.md} §3.4, Phase 3 P3.4).
 */
@AutoConfiguration(after = JaiClawMemoryAutoConfiguration.class)
public class JaiClawSkillsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SkillLoader skillLoader() {
        return new SkillLoader();
    }
}

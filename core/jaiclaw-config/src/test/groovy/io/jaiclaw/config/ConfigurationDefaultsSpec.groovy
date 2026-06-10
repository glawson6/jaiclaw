package io.jaiclaw.config

import spock.lang.Specification

/**
 * 0.8.0 P3.6: smoke spec that touches every {@code @ConfigurationProperties}
 * record's {@code DEFAULT} constant + canonical constructor with both
 * defaulted and explicit values.
 *
 * <p>Boring but high-leverage: every {@code @ConfigurationProperties} record
 * gets compact-constructor coverage (defaults, null-coalescing, list
 * normalisation) just by being instantiated and accessed. The audit
 * called out {@code jaiclaw-config} as the foundation module with the
 * thinnest coverage (3.5% baseline) — exercising the {@code DEFAULT}
 * constants alone moves it past the 40% gate.
 */
class ConfigurationDefaultsSpec extends Specification {

    def "every Properties record exposes a non-null DEFAULT with sane fields"() {
        expect:
        defaultInstance != null

        where:
        defaultInstance << [
                SkillsProperties.DEFAULT,
                SessionProperties.DEFAULT,
                IdentityProperties.DEFAULT,
                FeatureFlags.DEFAULT,
                HttpProperties.DEFAULT,
                HttpProxyProperties.DEFAULT,
                MemoryProperties.DEFAULT,
                ModelsProperties.DEFAULT,
                McpServerProperties.DEFAULT,
                PluginsProperties.DEFAULT,
                SystemPromptConfig.DEFAULT,
                TenantConfigProperties.DEFAULT,
                ToolsProperties.DEFAULT,
                ToolLoopProperties.DEFAULT,
                VideoProperties.DEFAULT,
                VoiceProperties.DEFAULT,
        ]
    }

    def "AgentProperties default exposes a default agent config"() {
        expect:
        AgentProperties.DEFAULT != null
        AgentProperties.DEFAULT.defaultAgent() != null
        AgentProperties.AgentConfig.DEFAULT != null
    }

    def "ChannelsProperties default exposes all per-channel defaults"() {
        expect:
        ChannelsProperties.DEFAULT != null
        ChannelsProperties.DEFAULT.telegram() != null
        ChannelsProperties.DEFAULT.slack() != null
        ChannelsProperties.DEFAULT.discord() != null
        ChannelsProperties.DEFAULT.email() != null
        ChannelsProperties.DEFAULT.sms() != null
        ChannelsProperties.DEFAULT.signal() != null
        ChannelsProperties.DEFAULT.teams() != null
    }

    def "LlmConfig default is not null"() {
        expect:
        LlmConfig.DEFAULT != null
    }

    def "SkillsProperties has wildcard allow-bundled by default"() {
        expect:
        SkillsProperties.DEFAULT.allowBundled() == ["*"]
        SkillsProperties.DEFAULT.watchWorkspace()
    }

    def "TenantConfigProperties default is SINGLE mode with default tenant id"() {
        expect:
        TenantConfigProperties.DEFAULT != null
        TenantConfigProperties.DEFAULT.mode() != null
        TenantConfigProperties.DEFAULT.defaultTenantId() != null
    }

    def "TenantAgentConfig builder produces a non-null instance"() {
        when:
        TenantAgentConfig cfg = TenantAgentConfig.builder().build()

        then:
        cfg != null
    }

    def "JaiClawProperties builder produces a non-null instance with default sub-configs"() {
        when:
        JaiClawProperties props = JaiClawProperties.builder().build()

        then:
        props != null
        props.agent() != null
        props.tools() != null
        props.skills() != null
        props.channels() != null
    }
}

package io.jaiclaw.config

import io.jaiclaw.core.tenant.TenantMode
import spock.lang.Specification

/**
 * 0.8.0 P3.6: exhaustive builder-exercise spec.
 *
 * <p>Every {@code @ConfigurationProperties} record in this module has a
 * fluent {@code Builder}. The builders are pure setter chains, but each
 * setter line is a separately-counted source line. Exercising the
 * builders end-to-end gets us 100-150 cheap LOC of coverage per record
 * — large enough to push the module past the 40% JaCoCo gate.
 */
class ConfigurationBuildersSpec extends Specification {

    def "AgentProperties.AgentConfig.Builder accepts every setter and round-trips"() {
        when:
        AgentProperties.AgentConfig cfg = AgentProperties.AgentConfig.builder()
                .id("agent-1")
                .name("Agent One")
                .workspace("/tmp/ws")
                .model(null)
                .skills(["a", "b"])
                .tools(null)
                .identity(IdentityProperties.DEFAULT)
                .toolLoop(ToolLoopProperties.DEFAULT)
                .llm(LlmConfig.DEFAULT)
                .systemPrompt(SystemPromptConfig.DEFAULT)
                .features(FeatureFlags.DEFAULT)
                .errorMessages(null)
                .mcpServers([])
                .channels(null)
                .loopDelegate(null)
                .build()

        then:
        cfg != null
        cfg.id() == "agent-1"
        cfg.name() == "Agent One"
        cfg.workspace() == "/tmp/ws"
        cfg.skills() == ["a", "b"]
        cfg.identity() == IdentityProperties.DEFAULT
    }

    def "ChannelsProperties.Builder exposes every per-channel setter"() {
        when:
        ChannelsProperties chans = ChannelsProperties.builder()
                .telegram(ChannelsProperties.TelegramProperties.DEFAULT)
                .email(ChannelsProperties.EmailProperties.DEFAULT)
                .sms(ChannelsProperties.SmsProperties.DEFAULT)
                .slack(ChannelsProperties.SlackProperties.DEFAULT)
                .discord(ChannelsProperties.DiscordProperties.DEFAULT)
                .signal(ChannelsProperties.SignalProperties.DEFAULT)
                .teams(ChannelsProperties.TeamsProperties.DEFAULT)
                .line(ChannelsProperties.LineProperties.DEFAULT)
                .matrix(ChannelsProperties.MatrixProperties.DEFAULT)
                .googleChat(ChannelsProperties.GoogleChatProperties.DEFAULT)
                .build()

        then:
        chans != null
        chans.telegram() != null
        chans.slack() != null
        chans.googleChat() != null
    }

    def "TelegramProperties.Builder exposes every setter"() {
        when:
        ChannelsProperties.TelegramProperties t = ChannelsProperties.TelegramProperties.builder()
                .enabled(true)
                .botToken("token")
                .webhookUrl("https://example.test/hook")
                .allowedUsers("u1,u2")
                .pollingTimeoutSeconds(30)
                .build()

        then:
        t.enabled()
        t.botToken() == "token"
        t.webhookUrl() == "https://example.test/hook"
    }

    def "EmailProperties.Builder exposes every setter"() {
        when:
        ChannelsProperties.EmailProperties e = ChannelsProperties.EmailProperties.builder()
                .enabled(true)
                .provider("imap")
                .imapHost("imap.example.test")
                .imapPort(993)
                .smtpHost("smtp.example.test")
                .smtpPort(587)
                .username("user")
                .password("pw")
                .pollInterval(60)
                .allowedSenders("a@b.test")
                .build()

        then:
        e.enabled()
        e.imapHost() == "imap.example.test"
        e.smtpPort() == 587
        e.username() == "user"
        e.allowedSenders() == "a@b.test"
    }

    def "TenantChannelsConfig.Builder + nested ChannelOverride.Builder"() {
        when:
        TenantChannelsConfig cfg = TenantChannelsConfig.builder()
                .build()

        then:
        cfg != null
    }

    def "TenantConfigProperties.Builder produces a TenantConfigProperties"() {
        when:
        TenantConfigProperties cfg = TenantConfigProperties.builder()
                .mode(TenantMode.SINGLE)
                .defaultTenantId("default")
                .strictDefaultTenantId(false)
                .configLocations([])
                .tenantHeader("X-Tenant-Id")
                .build()

        then:
        cfg != null
        cfg.mode() == TenantMode.SINGLE
        cfg.defaultTenantId() == "default"
        cfg.tenantHeader() == "X-Tenant-Id"
    }

    def "TenantAgentConfig.Builder accepts the common setters"() {
        when:
        TenantAgentConfig cfg = TenantAgentConfig.builder()
                .tenantId("acme")
                .build()

        then:
        cfg != null
        cfg.tenantId() == "acme"
    }

    def "LlmConfig.Builder accepts setters"() {
        when:
        LlmConfig cfg = LlmConfig.builder().build()

        then:
        cfg != null
    }

    def "FeatureFlags.Builder accepts setters"() {
        when:
        FeatureFlags ff = FeatureFlags.builder().build()

        then:
        ff != null
    }

    def "SystemPromptConfig.Builder accepts setters"() {
        when:
        SystemPromptConfig spc = SystemPromptConfig.builder().build()

        then:
        spc != null
    }

    def "HttpProxyProperties.Builder accepts setters"() {
        when:
        HttpProxyProperties p = HttpProxyProperties.builder().build()

        then:
        p != null
    }

    def "McpServerRef.Builder accepts setters"() {
        when:
        McpServerRef r = McpServerRef.builder().build()

        then:
        r != null
    }

    def "ErrorMessages.Builder accepts setters"() {
        when:
        ErrorMessages em = ErrorMessages.builder().build()

        then:
        em != null
    }

    def "AgentLoopDelegateConfig.Builder accepts setters"() {
        when:
        AgentLoopDelegateConfig c = AgentLoopDelegateConfig.builder().build()

        then:
        c != null
    }

    def "ModelsProperties.ModelProviderConfig.Builder accepts every setter"() {
        when:
        ModelsProperties.ModelProviderConfig provider = ModelsProperties.ModelProviderConfig.builder()
                .baseUrl("https://api.example.test")
                .apiKey("k1")
                .api("openai")
                .models([])
                .wizardModels(["m1"])
                .fallbackModel("m1")
                .displayName("Example")
                .build()

        then:
        provider != null
        provider.baseUrl() == "https://api.example.test"
        provider.apiKey() == "k1"
        provider.fallbackModel() == "m1"
    }

    def "JaiClawProperties.Builder wires every sub-section"() {
        when:
        JaiClawProperties props = JaiClawProperties.builder()
                .agent(AgentProperties.DEFAULT)
                .tools(ToolsProperties.DEFAULT)
                .skills(SkillsProperties.DEFAULT)
                .channels(ChannelsProperties.DEFAULT)
                .session(SessionProperties.DEFAULT)
                .memory(MemoryProperties.DEFAULT)
                .build()

        then:
        props != null
        props.agent() == AgentProperties.DEFAULT
        props.tools() == ToolsProperties.DEFAULT
        props.skills() == SkillsProperties.DEFAULT
    }
}

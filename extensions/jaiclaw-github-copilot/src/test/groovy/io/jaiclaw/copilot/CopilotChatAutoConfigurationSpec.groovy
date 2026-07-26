package io.jaiclaw.copilot

import io.jaiclaw.copilot.autoconfigure.CopilotChatAutoConfiguration
import io.jaiclaw.copilot.autoconfigure.CopilotChatProperties
import spock.lang.Specification

/**
 * Specs for the pieces of {@link CopilotChatAutoConfiguration} that don't
 * need a Spring context — property → provider-override translation, and the
 * "leave everything alone" default behavior.
 */
class CopilotChatAutoConfigurationSpec extends Specification {

    def autoConfig = new CopilotChatAutoConfiguration()

    def "buildProviderOverride returns null when no fields set"() {
        expect:
        autoConfig.buildProviderOverride(new CopilotChatProperties.Provider()) == null
        autoConfig.buildProviderOverride(null) == null
    }

    def "buildProviderOverride builds a ProviderConfig from base-url only"() {
        given:
        def p = new CopilotChatProperties.Provider(baseUrl: "https://proxy.internal/copilot")

        when:
        def cfg = autoConfig.buildProviderOverride(p)

        then:
        cfg != null
        cfg.getBaseUrl() == "https://proxy.internal/copilot"
        cfg.getType() == null
        cfg.getWireApi() == null
        cfg.getTransport() == null
    }

    def "buildProviderOverride wires all four fields"() {
        given:
        def p = new CopilotChatProperties.Provider(
                type: "openai",
                baseUrl: "https://openai.example.com/v1",
                wireApi: "chat",
                transport: "http")

        when:
        def cfg = autoConfig.buildProviderOverride(p)

        then:
        cfg.getType() == "openai"
        cfg.getBaseUrl() == "https://openai.example.com/v1"
        cfg.getWireApi() == "chat"
        cfg.getTransport() == "http"
    }

    def "buildProviderOverride ignores blank strings"() {
        given:
        def p = new CopilotChatProperties.Provider(baseUrl: "", type: "  ")

        expect:
        autoConfig.buildProviderOverride(p) == null
    }

    def "hasAnyValue reports correctly"() {
        expect:
        !new CopilotChatProperties.Provider().hasAnyValue()
        new CopilotChatProperties.Provider(baseUrl: "x").hasAnyValue()
        new CopilotChatProperties.Provider(type: "openai").hasAnyValue()
    }

    def "Cli defaults match the properties javadoc"() {
        given:
        def cli = new CopilotChatProperties.Cli()

        expect:
        cli.getBinary() == null
        cli.getUrl() == null
        cli.isCheckOnStartup()
    }

    def "Auth defaults leave PAT null and host at github.com"() {
        given:
        def auth = new CopilotChatProperties.Auth()

        expect:
        auth.getGithubToken() == null
        auth.getGithubHost() == "github.com"
        !auth.hasToken()
        !auth.isEnterpriseHost()   // github.com is NOT enterprise
    }

    def "Auth.hasToken detects a real PAT"() {
        given:
        def auth = new CopilotChatProperties.Auth(githubToken: "ghp_" + ("x" * 36))

        expect:
        auth.hasToken()
    }

    def "Auth.hasToken rejects null and blank tokens"() {
        expect:
        !new CopilotChatProperties.Auth(githubToken: null).hasToken()
        !new CopilotChatProperties.Auth(githubToken: "").hasToken()
        !new CopilotChatProperties.Auth(githubToken: "   ").hasToken()
    }

    def "Auth.isEnterpriseHost detects a GHE host"() {
        given:
        def auth = new CopilotChatProperties.Auth(githubHost: "github.mycorp.example")

        expect:
        auth.isEnterpriseHost()
    }

    def "Auth.isEnterpriseHost ignores case on github.com"() {
        expect:
        !new CopilotChatProperties.Auth(githubHost: "GitHub.com").isEnterpriseHost()
        !new CopilotChatProperties.Auth(githubHost: "GITHUB.COM").isEnterpriseHost()
    }
}

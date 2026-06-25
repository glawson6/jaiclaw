package io.jaiclaw.shell.commands.prompt

import io.jaiclaw.config.AgentProperties
import io.jaiclaw.config.IdentityProperties
import io.jaiclaw.config.JaiClawProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mock.env.MockEnvironment
import spock.lang.Specification

class JaiClawPromptProviderSpec extends Specification {

    PromptProperties props = PromptProperties.defaults()
    MockEnvironment env = new MockEnvironment()

    ObjectProvider<JaiClawProperties> jaiClawPropsProvider = Mock()

    def "renders identity-based default when no live override"() {
        given:
        jaiClawPropsProvider.getIfAvailable() >> JaiClawProperties.builder()
                .identity(new IdentityProperties("Acme Bot", "test"))
                .build()
        JaiClawPromptProvider provider = new JaiClawPromptProvider(props, jaiClawPropsProvider, env)

        when:
        def rendered = provider.prompt.toString()

        then:
        rendered == "Acme Bot > "
    }

    def "live property overrides the bound default"() {
        given:
        jaiClawPropsProvider.getIfAvailable() >> JaiClawProperties.builder()
                .identity(new IdentityProperties("Acme", "x"))
                .agent(new AgentProperties("opsbot", [:]))
                .build()
        env.setProperty("jaiclaw.shell.prompt.format", '${identity}/${agent}> ')
        JaiClawPromptProvider provider = new JaiClawPromptProvider(props, jaiClawPropsProvider, env)

        expect:
        provider.prompt.toString() == "Acme/opsbot> "
    }

    def "unresolved placeholder stays literal so operators see typos"() {
        given:
        jaiClawPropsProvider.getIfAvailable() >> JaiClawProperties.builder()
                .identity(new IdentityProperties("J", "d"))
                .build()
        env.setProperty("jaiclaw.shell.prompt.format", '${identity}|${idnetity}> ')
        JaiClawPromptProvider provider = new JaiClawPromptProvider(props, jaiClawPropsProvider, env)

        expect:
        provider.prompt.toString() == 'J|${idnetity}> '
    }

    def "tenant placeholder yields empty string in SINGLE mode"() {
        given:
        jaiClawPropsProvider.getIfAvailable() >> JaiClawProperties.builder()
                .identity(new IdentityProperties("J", "d"))
                .build()
        env.setProperty("jaiclaw.shell.prompt.format", '${identity}[${tenant}]> ')
        JaiClawPromptProvider provider = new JaiClawPromptProvider(props, jaiClawPropsProvider, env)

        expect:
        provider.prompt.toString() == "J[]> "
    }
}

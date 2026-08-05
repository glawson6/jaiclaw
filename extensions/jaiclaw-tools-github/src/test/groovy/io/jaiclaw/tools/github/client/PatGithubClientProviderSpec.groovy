package io.jaiclaw.tools.github.client

import io.jaiclaw.tools.github.GithubToolsProperties
import spock.lang.Specification

class PatGithubClientProviderSpec extends Specification {

    def "builds an anonymous client when no token is set and GITHUB_TOKEN env is absent"() {
        given:
        def props = new GithubToolsProperties(true, null, "https://api.github.com", 10, 30)
        def provider = new PatGithubClientProvider(props)

        when:
        def client = provider.getClient()

        then:
        client != null
        // Kohsuke's isAnonymous is true when no OAuth token is configured
        client.isAnonymous()
    }

    def "same client instance is returned on subsequent calls (cached)"() {
        given:
        def props = new GithubToolsProperties(true, null, "https://api.github.com", 10, 30)
        def provider = new PatGithubClientProvider(props)

        when:
        def a = provider.getClient()
        def b = provider.getClient()

        then:
        a.is(b)
    }

    def "properties defaults() records provide safe values for tests"() {
        expect:
        def defaults = GithubToolsProperties.defaults()
        defaults.apiUrl() == "https://api.github.com"
        defaults.connectTimeoutSeconds() == 10
        defaults.readTimeoutSeconds() == 30
        !defaults.enabled()
        defaults.token() == null
    }

    def "properties record fills in defaults when passed blank apiUrl"() {
        when:
        def props = new GithubToolsProperties(true, "tok", "", 0, 0)

        then:
        props.apiUrl() == "https://api.github.com"
        props.connectTimeoutSeconds() == 10
        props.readTimeoutSeconds() == 30
    }
}

package io.jclaw.identity

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class IdentityLinkServiceSpec extends Specification {

    @TempDir
    Path tempDir

    IdentityLinkStore store
    IdentityLinkService service

    def setup() {
        store = new IdentityLinkStore(tempDir.resolve("identity-links.json"))
        service = new IdentityLinkService(store)
    }

    def "link creates identity link with provided canonical ID"() {
        when:
        def link = service.link("user-001", "telegram", "123456")

        then:
        link.canonicalUserId() == "user-001"
        link.channel() == "telegram"
        link.channelUserId() == "123456"
    }

    def "link generates UUID when canonical ID is null"() {
        when:
        def link = service.link(null, "slack", "U12345")

        then:
        link.canonicalUserId() != null
        link.canonicalUserId().length() == 36 // UUID format
    }

    def "unlink removes identity link"() {
        given:
        service.link("user-001", "telegram", "123456")

        when:
        service.unlink("telegram", "123456")

        then:
        store.resolveCanonicalId("telegram", "123456").isEmpty()
    }

    def "getLinksForUser returns all linked channels"() {
        given:
        service.link("user-001", "telegram", "123456")
        service.link("user-001", "slack", "U789")

        when:
        def links = service.getLinksForUser("user-001")

        then:
        links.size() == 2
        links.collect { it.channel() }.containsAll(["telegram", "slack"])
    }
}

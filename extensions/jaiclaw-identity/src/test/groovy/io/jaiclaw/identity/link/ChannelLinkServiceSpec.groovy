package io.jaiclaw.identity.link

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.identity.IdentityLinkStore
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class ChannelLinkServiceSpec extends Specification {

    @TempDir Path tempDir

    static final String ISSUER = "https://idp.example.com/oidc"

    def props = new ChannelLinkProperties(true,
            "https://idp.example.com/oidc/auth",
            "https://idp.example.com/oidc/token",
            "jaiclaw-gateway", null,
            "https://jaiclaw.example.com/api/identity/link/callback",
            ["openid", "profile"], null, Duration.ofMinutes(10))

    Clock clock = Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneOffset.UTC)
    ChannelLinkNonceStore nonceStore
    IdentityLinkStore linkStore
    ChannelLinkService.TokenExchanger exchanger
    ChannelLinkService service

    def setup() {
        nonceStore = new InMemoryChannelLinkNonceStore(
                new TenantGuard(TenantProperties.DEFAULT), clock)
        linkStore = new IdentityLinkStore(tempDir.resolve("links.json"))
        exchanger = Mock(ChannelLinkService.TokenExchanger)
        service = new ChannelLinkService(props, nonceStore, linkStore, exchanger,
                new TenantGuard(TenantProperties.DEFAULT), clock)
    }

    def "issues an authorization URL with PKCE and a state nonce"() {
        when:
        def invitation = service.issue("telegram", "12345")

        then:
        invitation.authorizeUrl().startsWith("https://idp.example.com/oidc/auth?")
        invitation.authorizeUrl().contains("response_type=code")
        invitation.authorizeUrl().contains("client_id=jaiclaw-gateway")
        invitation.authorizeUrl().contains("state=" + invitation.nonce())
        invitation.authorizeUrl().contains("code_challenge_method=S256")
        invitation.authorizeUrl().contains("code_challenge=")

        and: "the verifier itself never appears in the URL — only its S256 hash"
        !invitation.authorizeUrl().contains("code_verifier")

        and:
        invitation.expiresAt() == clock.instant().plus(Duration.ofMinutes(10))
    }

    def "completing the flow records a VERIFIED link"() {
        given:
        def invitation = service.issue("telegram", "12345")
        exchanger.exchange(_, _) >> new ChannelLinkService.TokenExchanger.ExchangeResult(
                "user-sub-abc", ISSUER, null)

        when:
        def link = service.complete(invitation.nonce(), "auth-code").get()

        then:
        link.isVerified()
        link.canonicalUserId() == "user-sub-abc"
        link.channel() == "telegram"
        link.channelUserId() == "12345"
        link.issuer() == ISSUER
        link.verifiedAt() == clock.instant()

        and: "and it is persisted, so later messages resolve to the same subject"
        linkStore.resolveCanonicalId("telegram", "12345").get() == "user-sub-abc"
    }

    def "the channel binding comes from the stored request, never the callback"() {
        given: "a nonce issued for one channel user"
        def invitation = service.issue("telegram", "victim-chat-id")
        exchanger.exchange(_, _) >> new ChannelLinkService.TokenExchanger.ExchangeResult(
                "attacker-sub", ISSUER, null)

        when: "the attacker completes the flow with their OWN provider account"
        def link = service.complete(invitation.nonce(), "attacker-code").get()

        then: "they can only bind their subject to the channel id the nonce was issued for"
        link.channelUserId() == "victim-chat-id"
        link.canonicalUserId() == "attacker-sub"
    }

    def "a nonce cannot be redeemed twice"() {
        given:
        def invitation = service.issue("telegram", "12345")
        exchanger.exchange(_, _) >> new ChannelLinkService.TokenExchanger.ExchangeResult(
                "user-sub", ISSUER, null)

        when:
        def first = service.complete(invitation.nonce(), "code-1")
        def second = service.complete(invitation.nonce(), "code-2")

        then: "replay of an observed callback URL must not re-link"
        first.isPresent()
        second.isEmpty()
    }

    def "an expired nonce is refused"() {
        given:
        def invitation = service.issue("telegram", "12345")

        and: "eleven minutes pass"
        def later = Clock.fixed(clock.instant().plus(Duration.ofMinutes(11)), ZoneOffset.UTC)
        def lateStore = new InMemoryChannelLinkNonceStore(
                new TenantGuard(TenantProperties.DEFAULT), later)
        def lateService = new ChannelLinkService(props, lateStore, linkStore, exchanger,
                new TenantGuard(TenantProperties.DEFAULT), later)
        lateStore.store(new ChannelLinkRequest(invitation.nonce(), "telegram", "12345",
                null, "verifier", clock.instant(), clock.instant().plus(Duration.ofMinutes(10))))

        expect:
        lateService.complete(invitation.nonce(), "code").isEmpty()
    }

    def "an unknown state is refused"() {
        expect:
        service.complete("never-issued", "code").isEmpty()
    }

    def "a failed token exchange links nothing"() {
        given:
        def invitation = service.issue("telegram", "12345")
        exchanger.exchange(_, _) >> { throw new IllegalStateException("token endpoint 500") }

        expect:
        service.complete(invitation.nonce(), "code").isEmpty()
        linkStore.resolveCanonicalId("telegram", "12345").isEmpty()
    }

    def "a provider returning no subject links nothing"() {
        given:
        def invitation = service.issue("telegram", "12345")
        exchanger.exchange(_, _) >> new ChannelLinkService.TokenExchanger.ExchangeResult(
                null, ISSUER, null)

        expect:
        service.complete(invitation.nonce(), "code").isEmpty()
    }

    def "issuing fails fast when the flow is not configured"() {
        given:
        def unusable = new ChannelLinkService(ChannelLinkProperties.defaults(),
                nonceStore, linkStore, exchanger, null, clock)

        when:
        unusable.issue("telegram", "12345")

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("not configured")
    }

    def "the PKCE verifier is redacted from the request's toString"() {
        given:
        def request = new ChannelLinkRequest("nonce", "telegram", "123", null,
                "super-secret-verifier", clock.instant(), clock.instant().plusSeconds(600))

        expect: "records like this end up in logs by accident"
        !request.toString().contains("super-secret-verifier")
        request.toString().contains("<redacted>")
    }
}

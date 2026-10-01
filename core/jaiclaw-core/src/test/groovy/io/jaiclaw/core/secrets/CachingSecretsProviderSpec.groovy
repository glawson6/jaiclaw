package io.jaiclaw.core.secrets

import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The hot path this exists for: {@code SecretsPropertySource} is
 * {@code addFirst}-ed into the Spring {@code Environment}, so it is asked about
 * every property the application resolves. Against the 1Password provider that
 * meant an {@code op read} subprocess per miss.
 */
class CachingSecretsProviderSpec extends Specification {

    static class TickingClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z")
        @Override Instant instant() { now }
        @Override ZoneOffset getZone() { ZoneOffset.UTC }
        @Override Clock withZone(java.time.ZoneId z) { this }
        void advance(Duration d) { now = now.plus(d) }
    }

    /** Counts lookups so "how many subprocesses" is observable. */
    static class CountingProvider implements SecretsProvider {
        Map<String, String> values = [:]
        int getCalls = 0
        int refreshCalls = 0
        RuntimeException failWith = null

        @Override Optional<String> get(String key) {
            getCalls++
            if (failWith != null) throw failWith
            Optional.ofNullable(values[key])
        }
        @Override Map<String, String> getAll(String prefix) { [:] }
        @Override String name() { "counting" }
        @Override void refresh() { refreshCalls++ }
    }

    TickingClock clock = new TickingClock()
    CountingProvider delegate = new CountingProvider()

    private CachingSecretsProvider caching(Duration hit = Duration.ofMinutes(5),
                                           Duration miss = Duration.ofMinutes(1),
                                           int max = 4096) {
        new CachingSecretsProvider(delegate, hit, miss, max, clock)
    }

    def "a repeated hit consults the delegate once"() {
        given:
        delegate.values["api-key"] = "s3cret"
        def provider = caching()

        when:
        10.times { provider.get("api-key") }

        then: "nine subprocesses saved"
        provider.get("api-key").get() == "s3cret"
        delegate.getCalls == 1
        provider.delegateCalls() == 1
    }

    def "a repeated MISS also consults the delegate once"() {
        given: "misses are the common case — most properties are not secrets"
        def provider = caching()

        when:
        50.times { provider.get("server.port") }

        then: "without negative caching this would be 50 op reads"
        provider.get("server.port").isEmpty()
        delegate.getCalls == 1
    }

    def "a hit is re-fetched after its TTL"() {
        given:
        delegate.values["k"] = "v1"
        def provider = caching(Duration.ofMinutes(5), Duration.ofMinutes(1))
        provider.get("k")

        when: "the secret is rotated in the backing store"
        delegate.values["k"] = "v2"
        clock.advance(Duration.ofMinutes(5))

        then: "the new value is visible once the window closes"
        provider.get("k").get() == "v2"
        delegate.getCalls == 2
    }

    def "a miss is re-fetched on the shorter miss TTL"() {
        given: "an absent secret is likelier to appear than a present one is to change"
        def provider = caching(Duration.ofMinutes(5), Duration.ofMinutes(1))
        provider.get("later")

        when: "it appears, and only the miss TTL elapses"
        delegate.values["later"] = "now-here"
        clock.advance(Duration.ofMinutes(1))

        then:
        provider.get("later").get() == "now-here"
    }

    def "a hit is still cached while only the miss TTL has elapsed"() {
        given:
        delegate.values["k"] = "v"
        def provider = caching(Duration.ofMinutes(5), Duration.ofMinutes(1))
        provider.get("k")

        when:
        clock.advance(Duration.ofMinutes(1))
        provider.get("k")

        then: "the two TTLs are independent"
        delegate.getCalls == 1
    }

    def "refresh clears the cache so a rotation lands immediately"() {
        given: "the SPI always declared refresh(); nothing implemented it because nothing cached"
        delegate.values["k"] = "v1"
        def provider = caching()
        provider.get("k")

        when:
        delegate.values["k"] = "v2"
        provider.refresh()

        then:
        provider.get("k").get() == "v2"

        and: "and the delegate is refreshed too"
        delegate.refreshCalls == 1
    }

    def "a provider failure is not cached"() {
        given: "a transient outage must not become a TTL-long one"
        def provider = caching()
        delegate.failWith = new IllegalStateException("op CLI missing")

        when:
        provider.get("k")

        then:
        thrown(IllegalStateException)

        when: "the provider recovers"
        delegate.failWith = null
        delegate.values["k"] = "v"

        then: "the next lookup tries again rather than serving a cached error"
        provider.get("k").get() == "v"
    }

    def "a null key is handled without consulting the delegate"() {
        when:
        def result = caching().get(null)

        then:
        result.isEmpty()
        delegate.getCalls == 0
    }

    def "getAll is delegated rather than cached"() {
        given: "prefix result sets are unbounded and would risk serving a stale set"
        def provider = caching()

        when:
        provider.getAll("jaiclaw.")

        then:
        noExceptionThrown()
    }

    def "the cache is bounded"() {
        given:
        def provider = caching(Duration.ofMinutes(5), Duration.ofMinutes(5), 10)

        when: "far more distinct keys than the bound"
        200.times { provider.get("key-$it") }

        then: "a pathological lookup pattern cannot grow the heap without limit"
        provider.size() <= 10
    }

    def "name reports the wrapping so diagnostics are honest"() {
        expect:
        caching().name() == "caching(counting)"
    }

    def "a null delegate is rejected at construction"() {
        when:
        new CachingSecretsProvider(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "non-positive TTLs fall back to the defaults"() {
        given: "a zero TTL would make the cache useless rather than fast"
        delegate.values["k"] = "v"
        def provider = new CachingSecretsProvider(delegate, Duration.ZERO,
                Duration.ofSeconds(-1), 10, clock)

        when:
        provider.get("k")
        provider.get("k")

        then:
        delegate.getCalls == 1
    }

    def "hit and miss counters are tracked"() {
        given:
        delegate.values["k"] = "v"
        def provider = caching()

        when:
        provider.get("k")
        provider.get("k")
        provider.get("k")

        then:
        provider.cacheMisses() == 1
        provider.cacheHits() == 2
    }
}

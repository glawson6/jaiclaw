package io.jclaw.security.ratelimit

import spock.lang.Specification
import spock.lang.Subject

class UserRateLimiterSpec extends Specification {

    @Subject
    def limiter = new UserRateLimiter(3)

    def "should allow requests within rate limit"() {
        expect:
        limiter.isAllowed("user1")
        limiter.isAllowed("user1")
        limiter.isAllowed("user1")
    }

    def "should block requests exceeding rate limit"() {
        given:
        limiter.isAllowed("user1")
        limiter.isAllowed("user1")
        limiter.isAllowed("user1")

        expect:
        !limiter.isAllowed("user1")
    }

    def "should track limits independently per user"() {
        given: "user1 exhausts their limit"
        3.times { limiter.isAllowed("user1") }

        expect: "user2 still has full quota"
        limiter.isAllowed("user2")
        limiter.isAllowed("user2")
        limiter.isAllowed("user2")

        and: "user1 is blocked"
        !limiter.isAllowed("user1")

        and: "user2 is now also blocked"
        !limiter.isAllowed("user2")
    }

    def "should report remaining requests correctly"() {
        given:
        limiter.isAllowed("user1")

        expect:
        limiter.remaining("user1") == 2
    }

    def "should report full quota for unknown user"() {
        expect:
        limiter.remaining("unknown-user") == 3
    }
}

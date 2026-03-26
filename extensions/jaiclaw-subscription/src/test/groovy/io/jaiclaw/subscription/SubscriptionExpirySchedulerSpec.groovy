package io.jaiclaw.subscription

import spock.lang.Specification

import java.time.Duration

class SubscriptionExpirySchedulerSpec extends Specification {

    SubscriptionService subscriptionService = Mock()

    def "scheduler starts and stops without error"() {
        given:
        def scheduler = new SubscriptionExpiryScheduler(subscriptionService, "group1", Duration.ofMinutes(5))

        when:
        scheduler.start()

        then:
        noExceptionThrown()

        when:
        scheduler.stop()

        then:
        noExceptionThrown()
    }

    def "scheduler uses configured group id"() {
        given:
        def scheduler = new SubscriptionExpiryScheduler(subscriptionService, "group1", Duration.ofSeconds(1))

        when:
        scheduler.start()
        // Give it time for one tick
        Thread.sleep(1500)
        scheduler.stop()

        then:
        (1.._) * subscriptionService.processExpired("group1") >> []
    }
}

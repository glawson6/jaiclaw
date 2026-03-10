package io.jclaw.gateway

import spock.lang.Specification

class GatewayLifecycleSpec extends Specification {

    GatewayService gatewayService = Mock()
    GatewayLifecycle lifecycle = new GatewayLifecycle(gatewayService)

    def "start delegates to GatewayService and sets running"() {
        when:
        lifecycle.start()

        then:
        1 * gatewayService.start()
        lifecycle.isRunning()
    }

    def "stop delegates to GatewayService and clears running"() {
        given:
        lifecycle.start()

        when:
        lifecycle.stop()

        then:
        1 * gatewayService.stop()
        !lifecycle.isRunning()
    }

    def "phase is before default"() {
        expect:
        lifecycle.getPhase() < Integer.MAX_VALUE
    }
}

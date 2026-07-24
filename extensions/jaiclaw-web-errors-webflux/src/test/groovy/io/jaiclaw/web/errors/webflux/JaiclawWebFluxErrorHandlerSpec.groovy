package io.jaiclaw.web.errors.webflux

import io.jaiclaw.web.errors.core.WebErrorLogger
import io.jaiclaw.web.errors.core.WebErrorMapper
import io.jaiclaw.web.errors.core.WebErrorProperties
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * Verifies the reactive error handler in isolation — feed the handler
 * a mock exchange + throwable, then read the response body and status
 * directly. Boots no Spring context.
 *
 * The reproducer for the priority issue is the "NoResourceFoundException
 * (webflux flavour) → 404 opaque" test: this is the exact behavior the
 * taptech-platform-app reactive stack lacked.
 */
class JaiclawWebFluxErrorHandlerSpec extends Specification {

    WebErrorLogger errorLogger = Mock()

    WebErrorProperties props = WebErrorProperties.defaults()

    WebErrorMapper mapper = new WebErrorMapper(JaiclawWebFluxErrorAutoConfiguration.FLUX_MATCHERS)

    JaiclawWebFluxErrorHandler handler = new JaiclawWebFluxErrorHandler(mapper, props, errorLogger)

    def "reactive NoResourceFoundException returns 404 opaque, not 500 leaking message"() {
        given:
        MockServerHttpRequest request = MockServerHttpRequest.get("/.env").build()
        MockServerWebExchange exchange = MockServerWebExchange.from(request)
        Throwable ex = new org.springframework.web.reactive.resource.NoResourceFoundException(
                java.net.URI.create("/.env"), "/.env")

        when:
        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete()

        then:
        exchange.response.statusCode == HttpStatus.NOT_FOUND
        exchange.response.headers.contentType.toString().startsWith("text/plain")
        String body = readBody(exchange)
        body == "Not Found"
        0 * errorLogger.log5xx(_, _)
        1 * errorLogger.log4xx(_ as Throwable, _ as String)
    }

    def "unmapped throwable returns 500 opaque + logs at ERROR"() {
        given:
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/widgets").build()
        MockServerWebExchange exchange = MockServerWebExchange.from(request)
        Throwable ex = new IllegalStateException("something exploded")

        when:
        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete()

        then:
        exchange.response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        String body = readBody(exchange)
        body == "Internal Server Error"
        1 * errorLogger.log5xx({ Throwable t -> t.is(ex) }, { String s -> s == "POST /api/widgets" })
        0 * errorLogger.log4xx(_, _)
    }

    def "handler has higher precedence than Spring Boot's default (which is LOWEST_PRECEDENCE-1)"() {
        expect:
        // Any negative-ish order beats LOWEST_PRECEDENCE - 1 (a very large positive int).
        handler.getOrder() < org.springframework.core.Ordered.LOWEST_PRECEDENCE - 1
    }

    private static String readBody(MockServerWebExchange exchange) {
        Flux<DataBuffer> body = exchange.response.body
        StringBuilder sb = new StringBuilder()
        StepVerifier.create(body)
                .consumeNextWith({ DataBuffer buf ->
                    byte[] bytes = new byte[buf.readableByteCount()]
                    buf.read(bytes)
                    sb.append(new String(bytes, StandardCharsets.UTF_8))
                })
                .verifyComplete()
        return sb.toString()
    }
}

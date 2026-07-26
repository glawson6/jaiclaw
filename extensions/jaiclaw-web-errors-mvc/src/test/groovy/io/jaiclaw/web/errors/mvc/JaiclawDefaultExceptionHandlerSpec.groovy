package io.jaiclaw.web.errors.mvc

import io.jaiclaw.web.errors.core.WebErrorLogger
import io.jaiclaw.web.errors.core.WebErrorMapper
import io.jaiclaw.web.errors.core.WebErrorProperties
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Reproduces the exact bug motivating this module (see
 * {@code docs/issues/web-exception-handling-defaults.md}): a request
 * to a nonexistent static resource path like {@code /.env} should
 * return 404 with an opaque body, not 500 leaking the exception
 * message. Uses standalone {@code MockMvc} rather than a full
 * {@code @SpringBootTest} boot so we don't drag Boot test-autoconfigure
 * into the Groovy compile classpath.
 */
class JaiclawDefaultExceptionHandlerSpec extends Specification {

    WebErrorLogger errorLogger = Mock()

    WebErrorProperties props = WebErrorProperties.defaults()

    WebErrorMapper mapper = new WebErrorMapper(JaiclawWebMvcErrorAutoConfiguration.MVC_MATCHERS)

    JaiclawDefaultExceptionHandler handler = new JaiclawDefaultExceptionHandler(mapper, props, errorLogger)

    MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new DemoController())
            .setControllerAdvice(handler)
            .build()

    def "GET /.env would be a NoResourceFoundException — mapper returns 404 opaque"() {
        given:
        Throwable ex = new org.springframework.web.servlet.resource.NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/.env", "/.env")

        when:
        def response = mapper.map(ex, props)

        then:
        response.status() == 404
        response.body() == "Not Found"
        !response.isServerError()
    }

    def "GET /boom returns 500 opaque + logs at ERROR"() {
        when:
        def result = mvc.perform(get("/boom"))

        then:
        result.andExpect(status().isInternalServerError())
                .andExpect(content().string("Internal Server Error"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
        1 * errorLogger.log5xx(_ as Throwable, _ as String)
    }

    def "GET /biz-not-found returns 404 opaque (mapped by static override)"() {
        given:
        props = new WebErrorProperties(
                io.jaiclaw.web.errors.core.WebErrorBodyFormat.OPAQUE,
                WebErrorProperties.NotFound.DEFAULT,
                WebErrorProperties.InternalError.DEFAULT,
                false,
                "text/plain",
                Map.of("io.jaiclaw.web.errors.mvc.JaiclawDefaultExceptionHandlerSpec\$BizNotFoundException", 404))
        handler = new JaiclawDefaultExceptionHandler(mapper, props, errorLogger)
        mvc = MockMvcBuilders.standaloneSetup(new DemoController())
                .setControllerAdvice(handler)
                .build()

        when:
        def result = mvc.perform(get("/biz-not-found"))

        then:
        result.andExpect(status().isNotFound())
                .andExpect(content().string("Not Found"))
        0 * errorLogger.log5xx(_, _)
    }

    // ── fixtures ──────────────────────────────────────

    static class BizNotFoundException extends RuntimeException {
        BizNotFoundException(String msg) { super(msg) }
    }

    @RestController
    static class DemoController {
        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("boom-message")
        }

        @GetMapping("/biz-not-found")
        String bizNotFound() {
            throw new BizNotFoundException("no such widget")
        }
    }
}

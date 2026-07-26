package io.jaiclaw.web.errors.mvc

import io.jaiclaw.web.errors.core.WebErrorLogger
import io.jaiclaw.web.errors.core.WebErrorMapper
import io.jaiclaw.web.errors.core.WebErrorProperties
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import spock.lang.Specification

class JaiclawWebMvcErrorAutoConfigurationSpec extends Specification {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JaiclawWebMvcErrorAutoConfiguration))

    def "beans register with defaults on a bare webmvc context"() {
        expect:
        runner.run { ctx ->
            assert ctx.getBean(WebErrorMapper)
            assert ctx.getBean(WebErrorLogger) instanceof Slf4jMvcWebErrorLogger
            assert ctx.getBean(JaiclawDefaultExceptionHandler)
            // Verify properties bound with sensible defaults.
            WebErrorProperties props = ctx.getBean(WebErrorProperties)
            assert props.bodyFormat().name() == "OPAQUE"
            assert props.notFound().status() == 404
            assert props.notFound().body() == "Not Found"
            assert props.internalError().body() == "Internal Server Error"
            assert !props.includeExceptionMessage()
        }
    }

    def "enabled=false suppresses all beans"() {
        expect:
        runner.withPropertyValues("jaiclaw.web.errors.enabled=false").run { ctx ->
            assert !ctx.containsBean("jaiclawDefaultExceptionHandler")
            assert !ctx.containsBean("webErrorMapper")
            assert !ctx.containsBean("webErrorLogger")
        }
    }

    def "adopter-supplied WebErrorLogger bean wins"() {
        given:
        WebErrorLogger adopter = Mock()

        expect:
        runner.withBean(WebErrorLogger, { adopter }).run { ctx ->
            assert ctx.getBean(WebErrorLogger).is(adopter)
        }
    }

    def "adopter-supplied WebErrorMapper wins over the auto-configured default"() {
        given:
        WebErrorMapper adopter = new WebErrorMapper(
                new WebErrorMapper.ExceptionCategoryMatchers(
                        Set.of(), Set.of(), Set.of(), Set.of(),
                        Set.of(), Set.of(), Set.of(), Set.of()))

        expect:
        runner.withBean(WebErrorMapper, { adopter }).run { ctx ->
            assert ctx.getBean(WebErrorMapper).is(adopter)
        }
    }

    def "properties bind from YAML-style overrides"() {
        expect:
        runner.withPropertyValues(
                "jaiclaw.web.errors.body-format=problem-detail",
                "jaiclaw.web.errors.not-found.status=444",
                "jaiclaw.web.errors.not-found.body=",
                "jaiclaw.web.errors.include-exception-message=true"
        ).run { ctx ->
            WebErrorProperties props = ctx.getBean(WebErrorProperties)
            assert props.bodyFormat().name() == "PROBLEM_DETAIL"
            assert props.notFound().status() == 444
            assert props.notFound().body() == ""
            assert props.includeExceptionMessage()
        }
    }

    def "MVC matchers include Spring's WebMVC framework exception class names"() {
        expect:
        JaiclawWebMvcErrorAutoConfiguration.MVC_MATCHERS.notFoundClasses().contains(
                "org.springframework.web.servlet.resource.NoResourceFoundException")
        JaiclawWebMvcErrorAutoConfiguration.MVC_MATCHERS.notFoundClasses().contains(
                "org.springframework.web.servlet.NoHandlerFoundException")
        JaiclawWebMvcErrorAutoConfiguration.MVC_MATCHERS.validationClasses().contains(
                "org.springframework.web.bind.MethodArgumentNotValidException")
        JaiclawWebMvcErrorAutoConfiguration.MVC_MATCHERS.responseStatusClasses().contains(
                "org.springframework.web.server.ResponseStatusException")
    }
}

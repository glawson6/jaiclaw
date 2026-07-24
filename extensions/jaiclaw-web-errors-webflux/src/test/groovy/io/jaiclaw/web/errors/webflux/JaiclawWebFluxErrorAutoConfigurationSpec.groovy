package io.jaiclaw.web.errors.webflux

import io.jaiclaw.web.errors.core.WebErrorLogger
import io.jaiclaw.web.errors.core.WebErrorMapper
import io.jaiclaw.web.errors.core.WebErrorProperties
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import spock.lang.Specification

class JaiclawWebFluxErrorAutoConfigurationSpec extends Specification {

    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JaiclawWebFluxErrorAutoConfiguration))

    def "beans register with defaults on a bare reactive context"() {
        expect:
        runner.run { ctx ->
            assert ctx.getBean(WebErrorMapper)
            assert ctx.getBean(WebErrorLogger) instanceof Slf4jFluxWebErrorLogger
            assert ctx.getBean(JaiclawWebFluxErrorHandler)
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
            assert !ctx.containsBean("jaiclawWebFluxErrorHandler")
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
                "jaiclaw.web.errors.not-found.status=204",
                "jaiclaw.web.errors.not-found.body=",
                "jaiclaw.web.errors.include-exception-message=true"
        ).run { ctx ->
            WebErrorProperties props = ctx.getBean(WebErrorProperties)
            assert props.bodyFormat().name() == "PROBLEM_DETAIL"
            assert props.notFound().status() == 204
            assert props.notFound().body() == ""
            assert props.includeExceptionMessage()
        }
    }

    def "FLUX matchers include Spring's WebFlux framework exception class names"() {
        expect:
        JaiclawWebFluxErrorAutoConfiguration.FLUX_MATCHERS.notFoundClasses().contains(
                "org.springframework.web.reactive.resource.NoResourceFoundException")
        JaiclawWebFluxErrorAutoConfiguration.FLUX_MATCHERS.validationClasses().contains(
                "org.springframework.web.bind.support.WebExchangeBindException")
        JaiclawWebFluxErrorAutoConfiguration.FLUX_MATCHERS.validationClasses().contains(
                "org.springframework.web.server.ServerWebInputException")
        JaiclawWebFluxErrorAutoConfiguration.FLUX_MATCHERS.responseStatusClasses().contains(
                "org.springframework.web.server.ResponseStatusException")
    }

    def "servlet-stack context without spring-boot-webflux does not load the autoconfig"() {
        // Regression lock for the servlet-stack boot crash. The autoconfig used
        // to guard on WebExceptionHandler (spring-web / spring-webflux), which
        // is present on the servlet stack too. Spring would then introspect the
        // handler bean's return type, force-loading ErrorWebExceptionHandler
        // from spring-boot-webflux which is not on the servlet-stack classpath
        // → NoClassDefFoundError → boot crash. Guarding on
        // ErrorWebExceptionHandler makes the autoconfig silently absent in
        // that case, which is what this spec locks.
        given:
        def runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JaiclawWebFluxErrorAutoConfiguration))
                .withClassLoader(new FilteredClassLoader(
                        "org.springframework.boot.webflux.error.ErrorWebExceptionHandler"))

        expect:
        runner.run { ctx ->
            assert !ctx.containsBean("jaiclawWebFluxErrorHandler")
            assert !ctx.containsBean("webErrorMapper")
            assert !ctx.containsBean("webErrorLogger")
        }
    }
}

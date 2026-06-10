package io.jaiclaw.pipeline

import io.jaiclaw.pipeline.dsl.JaiClawPipeline
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

import java.util.function.Function

class PipelineAutoConfigurationSpec extends Specification {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PipelineAutoConfiguration))

    def "registry is empty when jaiclaw.pipeline.enabled is unset"() {
        when:
        runner.run { ctx ->
            PipelineRegistry registry = ctx.getBean(PipelineRegistry)
            assert registry.size() == 0
        }

        then:
        noExceptionThrown()
    }

    def "registry is empty when jaiclaw.pipeline.enabled=false explicitly"() {
        when:
        runner.withPropertyValues("jaiclaw.pipeline.enabled=false").run { ctx ->
            PipelineRegistry registry = ctx.getBean(PipelineRegistry)
            assert registry.size() == 0
        }

        then:
        noExceptionThrown()
    }

    def "context fails to start when enabled=true but no source is configured"() {
        when:
        runner.withPropertyValues("jaiclaw.pipeline.enabled=true").run { ctx ->
            assert ctx.startupFailure != null
            assert ctx.startupFailure.message.contains("no pipeline source is configured")
        }

        then:
        noExceptionThrown()
    }

    def "code-bean source satisfies the enabled-but-need-a-source rule"() {
        when:
        runner.withPropertyValues("jaiclaw.pipeline.enabled=true")
                .withUserConfiguration(CodeSourceConfig)
                .run { ctx ->
                    PipelineRegistry registry = ctx.getBean(PipelineRegistry)
                    assert registry.size() == 1
                    assert registry.contains("from-code")
                }

        then:
        noExceptionThrown()
    }

    def "locations source loads a per-file pipeline"() {
        when:
        runner.withPropertyValues(
                "jaiclaw.pipeline.enabled=true",
                "jaiclaw.pipeline.locations.patterns[0]=classpath*:jaiclaw/test-pipelines/*.yml"
        ).withUserConfiguration(FunctionBeansConfig).run { ctx ->
            PipelineRegistry registry = ctx.getBean(PipelineRegistry)
            assert registry.contains("file-only-pipe")
        }

        then:
        noExceptionThrown()
    }

    // ---- inner configs ----

    @Configuration
    static class FunctionBeansConfig {
        @Bean
        Function<String, String> noop() {
            return { String s -> s } as Function
        }
    }

    @Configuration
    static class CodeSourceConfig {
        @Bean
        Function<String, String> noop() {
            return { String s -> s } as Function
        }

        @Bean
        JaiClawPipeline codePipeline() {
            return new JaiClawPipeline() {
                @Override
                void define() {
                    pipeline("from-code")
                            .trigger().manual()
                            .then("s").processor("noop")
                            .output().log()
                }
            }
        }
    }
}

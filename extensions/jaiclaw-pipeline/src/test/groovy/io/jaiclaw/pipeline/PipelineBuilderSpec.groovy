package io.jaiclaw.pipeline

import io.jaiclaw.pipeline.dsl.JaiClawPipeline
import io.jaiclaw.pipeline.dsl.PipelineBuilder
import spock.lang.Specification

import java.time.Duration

class PipelineBuilderSpec extends Specification {

    def "builds pipeline with agent stage"() {
        given:
        JaiClawPipeline pipeline = new JaiClawPipeline() {
            @Override
            void define() {
                pipeline("test-pipeline")
                        .name("Test Pipeline")
                        .trigger().manual()
                        .stage("research").agent("researcher")
                            .systemPrompt("Research the topic")
                            .timeout(Duration.ofSeconds(180))
                        .output().log()
            }
        }

        when:
        List<PipelineDefinition> defs = pipeline.getDefinitions()

        then:
        defs.size() == 1
        PipelineDefinition def1 = defs[0]
        def1.id() == "test-pipeline"
        def1.name() == "Test Pipeline"
        def1.stages().size() == 1
        def1.stages()[0].name() == "research"
        def1.stages()[0].type() == StageType.AGENT
        def1.stages()[0].agentId() == "researcher"
        def1.stages()[0].systemPrompt() == "Research the topic"
        def1.stages()[0].timeout() == Duration.ofSeconds(180)
        def1.output().type() == OutputType.LOG
        def1.trigger().type() == TriggerType.MANUAL
    }

    def "builds pipeline with processor stage"() {
        given:
        JaiClawPipeline pipeline = new JaiClawPipeline() {
            @Override
            void define() {
                pipeline("proc-pipeline")
                        .stage("transform").processor("myTransformer")
                        .output().none()
            }
        }

        when:
        List<PipelineDefinition> defs = pipeline.getDefinitions()

        then:
        defs.size() == 1
        defs[0].stages()[0].type() == StageType.PROCESSOR
        defs[0].stages()[0].bean() == "myTransformer"
    }

    def "builds pipeline with camel stage"() {
        given:
        JaiClawPipeline pipeline = new JaiClawPipeline() {
            @Override
            void define() {
                pipeline("camel-pipeline")
                        .stage("store").camel("jpa:io.example.Article")
                        .output().camelUri("log:output")
            }
        }

        when:
        List<PipelineDefinition> defs = pipeline.getDefinitions()

        then:
        defs.size() == 1
        defs[0].stages()[0].type() == StageType.CAMEL
        defs[0].stages()[0].uri() == "jpa:io.example.Article"
        defs[0].output().type() == OutputType.CAMEL_URI
        defs[0].output().uri() == "log:output"
    }

    def "builds pipeline with all trigger types"() {
        given:
        JaiClawPipeline pipeline = new JaiClawPipeline() {
            @Override
            void define() {
                pipeline("file-trigger")
                        .trigger().file("file://inbox")
                        .stage("s1").processor("p1")
                        .output().none()

                pipeline("cron-trigger")
                        .trigger().cron("0 0 * * *")
                        .stage("s1").processor("p1")
                        .output().none()

                pipeline("http-trigger")
                        .trigger().http("/api/start")
                        .stage("s1").processor("p1")
                        .output().none()

                pipeline("camel-trigger")
                        .trigger().camelUri("amqp:queue:input")
                        .stage("s1").processor("p1")
                        .output().none()
            }
        }

        when:
        List<PipelineDefinition> defs = pipeline.getDefinitions()

        then:
        defs.size() == 4
        defs[0].trigger().type() == TriggerType.FILE
        defs[0].trigger().uri() == "file://inbox"
        defs[1].trigger().type() == TriggerType.CRON
        defs[1].trigger().expression() == "0 0 * * *"
        defs[2].trigger().type() == TriggerType.HTTP
        defs[2].trigger().path() == "/api/start"
        defs[3].trigger().type() == TriggerType.CAMEL_URI
        defs[3].trigger().uri() == "amqp:queue:input"
    }

    def "builds pipeline with channel output and template"() {
        given:
        JaiClawPipeline pipeline = new JaiClawPipeline() {
            @Override
            void define() {
                pipeline("channel-output")
                        .stage("s1").processor("p1")
                        .output().channel("slack")
                            .template("Result: {{stages.s1.output}}")
            }
        }

        when:
        List<PipelineDefinition> defs = pipeline.getDefinitions()

        then:
        defs[0].output().type() == OutputType.CHANNEL
        defs[0].output().channelId() == "slack"
        defs[0].output().template() == "Result: {{stages.s1.output}}"
    }

    def "builds pipeline with transport auth"() {
        given:
        JaiClawPipeline pipeline = new JaiClawPipeline() {
            @Override
            void define() {
                pipeline("kafka-pipeline")
                        .stage("ingest").processor("ingester")
                            .transport("kafka:raw-events?brokers=kafka:9092",
                                    TransportAuthType.HMAC_SHA256, "my-secret")
                        .stage("process").agent("analyzer")
                            .transport("amqp:queue:analyzed",
                                    TransportAuthType.BEARER_TOKEN, "my-token", "X-Custom-Token")
                        .output().log()
            }
        }

        when:
        List<PipelineDefinition> defs = pipeline.getDefinitions()

        then:
        defs[0].stages()[0].transport() != null
        defs[0].stages()[0].transport().uri() == "kafka:raw-events?brokers=kafka:9092"
        defs[0].stages()[0].transport().auth().authType() == TransportAuthType.HMAC_SHA256
        defs[0].stages()[0].transport().auth().secret() == "my-secret"
        defs[0].stages()[0].transport().auth().headerName() == "X-Hub-Signature-256" // default

        defs[0].stages()[1].transport().auth().authType() == TransportAuthType.BEARER_TOKEN
        defs[0].stages()[1].transport().auth().headerName() == "X-Custom-Token"
    }

    def "builds pipeline with transport URI only (no auth)"() {
        given:
        JaiClawPipeline pipeline = new JaiClawPipeline() {
            @Override
            void define() {
                pipeline("simple-kafka")
                        .stage("s1").processor("p1")
                            .transport("kafka:my-topic")
                        .output().none()
            }
        }

        when:
        List<PipelineDefinition> defs = pipeline.getDefinitions()

        then:
        defs[0].stages()[0].transport() != null
        defs[0].stages()[0].transport().uri() == "kafka:my-topic"
        defs[0].stages()[0].transport().auth() == null
    }

    def "builds pipeline with security settings"() {
        given:
        JaiClawPipeline pipeline = new JaiClawPipeline() {
            @Override
            void define() {
                pipeline("secure-pipeline")
                        .security()
                            .requireAuthentication()
                        .stage("s1").processor("p1")
                        .output().none()
            }
        }

        when:
        List<PipelineDefinition> defs = pipeline.getDefinitions()

        then:
        defs[0].security().enabled()
        defs[0].security().requireAuthentication()
    }

    def "multiple pipelines defined in single class"() {
        given:
        JaiClawPipeline pipeline = new JaiClawPipeline() {
            @Override
            void define() {
                pipeline("pipeline-a")
                        .stage("s1").processor("p1")
                        .output().none()

                pipeline("pipeline-b")
                        .stage("s1").processor("p2")
                        .output().log()
            }
        }

        when:
        List<PipelineDefinition> defs = pipeline.getDefinitions()

        then:
        defs.size() == 2
        defs[0].id() == "pipeline-a"
        defs[1].id() == "pipeline-b"
    }

    def "validation: missing stages throws"() {
        given:
        JaiClawPipeline pipeline = new JaiClawPipeline() {
            @Override
            void define() {
                pipeline("no-stages")
                        .output().none()
            }
        }

        when:
        pipeline.getDefinitions()

        then:
        thrown(IllegalStateException)
    }

    def "validation: duplicate stage names throws"() {
        given:
        JaiClawPipeline pipeline = new JaiClawPipeline() {
            @Override
            void define() {
                pipeline("dup-stages")
                        .stage("same").processor("p1")
                        .stage("same").processor("p2")
                        .output().none()
            }
        }

        when:
        pipeline.getDefinitions()

        then:
        thrown(IllegalStateException)
    }

    def "builds pipeline with tenantIds and error strategy"() {
        given:
        JaiClawPipeline pipeline = new JaiClawPipeline() {
            @Override
            void define() {
                pipeline("tenant-pipeline")
                        .name("Tenant Pipeline")
                        .tenantIds("acme", "globex")
                        .errorStrategy(ErrorStrategy.RETRY_THEN_FAIL)
                        .maxRetries(5)
                        .stage("s1").processor("p1")
                        .output().none()
            }
        }

        when:
        List<PipelineDefinition> defs = pipeline.getDefinitions()

        then:
        defs[0].tenantIds() == ["acme", "globex"]
        defs[0].errorStrategy() == ErrorStrategy.RETRY_THEN_FAIL
        defs[0].maxRetries() == 5
    }
}

package io.jaiclaw.pipeline.processors.data

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.pipeline.PipelineContext
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import org.apache.camel.Exchange
import org.apache.camel.Message
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class FileProcessorsSpec extends Specification {

    @TempDir
    Path tempDir

    Exchange exchange = Mock()
    Message message = Mock()
    PipelineContext ctx = new PipelineContext(
            "p", "e", null, null, 0, 1, null, null,
            [:] as Map, [:] as Map)
    StageDefinition stage = new StageDefinition(
            "s", StageType.PROCESSOR, "b", null, null, null, null, null, null)
    TenantGuard singleTenant = new TenantGuard(TenantProperties.DEFAULT)

    def setup() { exchange.getIn() >> message }

    def "File Write persists body then Read round-trips"() {
        given:
        FileProcessors.Write write = new FileProcessors.Write(tempDir, singleTenant)
        FileProcessors.Read read = new FileProcessors.Read(tempDir, singleTenant)
        message.getBody(String.class) >> "hello world"

        when:
        write.process(exchange, stage, ctx, [path: "sub/file.txt"])

        then:
        Files.exists(tempDir.resolve("sub/file.txt"))
        Files.readString(tempDir.resolve("sub/file.txt")) == "hello world"

        when:
        read.process(exchange, stage, ctx, [path: "sub/file.txt"])

        then:
        1 * message.setBody("hello world")
    }

    def "File Read fails when the file is missing"() {
        given:
        FileProcessors.Read read = new FileProcessors.Read(tempDir, singleTenant)

        when:
        read.process(exchange, stage, ctx, [path: "nope.txt"])

        then:
        thrown(java.nio.file.NoSuchFileException)
    }

    def "path escaping ../ is rejected"() {
        given:
        FileProcessors.Read read = new FileProcessors.Read(tempDir, singleTenant)

        when:
        read.process(exchange, stage, ctx, [path: "../etc/passwd"])

        then:
        thrown(IllegalArgumentException)
    }

    def "blank path is rejected"() {
        given:
        FileProcessors.Read read = new FileProcessors.Read(tempDir, singleTenant)

        when:
        read.process(exchange, stage, ctx, [path: ""])

        then:
        thrown(IllegalArgumentException)
    }
}

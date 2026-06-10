package io.jaiclaw.config

import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * 0.8.0 P3.6: covers {@link TenantEnvLoader}'s dotenv parser.
 *
 * <p>Tests exercise every branch of {@code parseLine}: comments, empty
 * lines, malformed lines, quoted values (single + double), unquoted
 * values — plus the resource-not-found short-circuit and the IO
 * failure path.
 */
class TenantEnvLoaderSpec extends Specification {

    @TempDir
    Path tmp

    def "load returns empty map when resource does not exist"() {
        given:
        ResourceLoader loader = Stub(ResourceLoader) {
            getResource(_ as String) >> {
                Stub(Resource) { exists() >> false }
            }
        }
        TenantEnvLoader env = new TenantEnvLoader(loader)

        expect:
        env.load("classpath:nope.env") == [:]
    }

    def "load parses a simple KEY=VALUE file"() {
        given:
        Path envFile = tmp.resolve("a.env")
        Files.writeString(envFile, """
            FOO=bar
            BAZ=qux
        """.stripIndent())
        TenantEnvLoader env = new TenantEnvLoader(fileResourceLoader())

        when:
        Map<String, String> result = env.load("file:" + envFile.toAbsolutePath())

        then:
        result["FOO"] == "bar"
        result["BAZ"] == "qux"
    }

    def "load skips comments and empty lines"() {
        given:
        Path envFile = tmp.resolve("b.env")
        Files.writeString(envFile, """
            # this is a comment

            FOO=bar
            # another comment
            BAZ=qux
        """.stripIndent())
        TenantEnvLoader env = new TenantEnvLoader(fileResourceLoader())

        when:
        Map<String, String> result = env.load("file:" + envFile.toAbsolutePath())

        then:
        result.size() == 2
        result["FOO"] == "bar"
    }

    def "load strips double quotes from values"() {
        given:
        Path envFile = tmp.resolve("c.env")
        Files.writeString(envFile, 'FOO="hello world"\n')
        TenantEnvLoader env = new TenantEnvLoader(fileResourceLoader())

        when:
        Map<String, String> result = env.load("file:" + envFile.toAbsolutePath())

        then:
        result["FOO"] == "hello world"
    }

    def "load strips single quotes from values"() {
        given:
        Path envFile = tmp.resolve("d.env")
        Files.writeString(envFile, "FOO='single quoted'\n")
        TenantEnvLoader env = new TenantEnvLoader(fileResourceLoader())

        when:
        Map<String, String> result = env.load("file:" + envFile.toAbsolutePath())

        then:
        result["FOO"] == "single quoted"
    }

    def "load skips lines with no equals sign"() {
        given:
        Path envFile = tmp.resolve("e.env")
        Files.writeString(envFile, """
            FOO=bar
            invalidlinenoequals
            BAZ=qux
        """.stripIndent())
        TenantEnvLoader env = new TenantEnvLoader(fileResourceLoader())

        when:
        Map<String, String> result = env.load("file:" + envFile.toAbsolutePath())

        then:
        result["FOO"] == "bar"
        result["BAZ"] == "qux"
        !result.containsKey("invalidlinenoequals")
    }

    def "load leaves unquoted values alone"() {
        given:
        Path envFile = tmp.resolve("f.env")
        Files.writeString(envFile, "FOO=unquoted value here\n")
        TenantEnvLoader env = new TenantEnvLoader(fileResourceLoader())

        when:
        Map<String, String> result = env.load("file:" + envFile.toAbsolutePath())

        then:
        result["FOO"] == "unquoted value here"
    }

    def "load returns empty when IO read fails"() {
        given:
        Resource broken = Mock(Resource) {
            exists() >> true
            getInputStream() >> { throw new IOException("boom") }
        }
        ResourceLoader loader = Stub(ResourceLoader) {
            getResource(_ as String) >> broken
        }
        TenantEnvLoader env = new TenantEnvLoader(loader)

        expect:
        env.load("file:broken") == [:]
    }

    def "load handles in-memory ByteArrayResource"() {
        given:
        String content = "ALPHA=one\nBETA=two\n"
        ResourceLoader loader = Stub(ResourceLoader) {
            getResource(_ as String) >> new ByteArrayResource(content.bytes)
        }
        TenantEnvLoader env = new TenantEnvLoader(loader)

        when:
        Map<String, String> result = env.load("inline:")

        then:
        result["ALPHA"] == "one"
        result["BETA"] == "two"
    }

    private ResourceLoader fileResourceLoader() {
        return new org.springframework.core.io.DefaultResourceLoader()
    }
}

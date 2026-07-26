package io.jaiclaw.maven

import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugin.logging.Log
import spock.lang.Specification
import spock.lang.TempDir

import java.lang.reflect.Field
import java.nio.file.Files
import java.nio.file.Path

/**
 * Locks the version-threading contract that makes ScaffoldMojo emit poms
 * pinned to the correct Boot + jaiclaw versions.
 *
 * <p>The mojo takes two version parameters that Maven fills from
 * {@code @Parameter(defaultValue = "${project.version}")} + a literal
 * default. This spec asserts:
 * <ul>
 *   <li>When both are missing (as they will be under a mis-configured
 *       Maven invocation), {@link ProjectManifest#validate()} fails fast
 *       with a targeted error message.</li>
 *   <li>When both are supplied, the generated pom.xml carries them into
 *       the {@code spring-boot-starter-parent} block and the
 *       {@code jaiclaw-bom} import block respectively.</li>
 * </ul>
 */
class ScaffoldMojoSpec extends Specification {

    @TempDir
    Path tempDir

    Log mockLog = Mock()

    private ScaffoldMojo createMojo(Map<String, Object> params = [:]) {
        ScaffoldMojo mojo = new ScaffoldMojo()
        mojo.setLog(mockLog)

        setField(mojo, 'manifest', params.manifest as File)
        setField(mojo, 'outputDir', params.getOrDefault('outputDir', tempDir.toFile()) as File)
        setField(mojo, 'jaiclawVersion', params.jaiclawVersion as String)
        setField(mojo, 'springBootVersion', params.springBootVersion as String)
        return mojo
    }

    private static void setField(Object target, String name, Object value) {
        Field field = ScaffoldMojo.getDeclaredField(name)
        field.setAccessible(true)
        field.set(target, value)
    }

    private File writeManifest(String contents) {
        Path path = tempDir.resolve("manifest.yml")
        Files.writeString(path, contents)
        return path.toFile()
    }

    def "fails fast when neither manifest nor mojo supplies jaiclawVersion + springBootVersion"() {
        given: "a minimal manifest with no version fields — both mojo params also null"
        File manifest = writeManifest("""\
name: verify-fail-fast
description: bare manifest — no versions anywhere
""")
        ScaffoldMojo mojo = createMojo(manifest: manifest)  // jaiclawVersion + springBootVersion both null

        when:
        mojo.execute()

        then: "targeted MojoFailureException, not the downstream mvn-package NPE mystery"
        MojoFailureException ex = thrown()
        ex.message.contains("jaiclaw-version") || ex.message.contains("spring-boot-version")
    }

    def "threads jaiclawVersion + springBootVersion from mojo params into the generated pom"() {
        given: "a minimal manifest — versions come from the mojo params"
        File manifest = writeManifest("""\
name: verify-threading
description: proves the mojo default-value wiring reaches the emitted pom
""")
        ScaffoldMojo mojo = createMojo(
                manifest: manifest,
                jaiclawVersion: "1.0.0-SNAPSHOT",
                springBootVersion: "4.1.0")

        when:
        mojo.execute()

        then:
        Path pom = tempDir.resolve("verify-threading/pom.xml")
        Files.exists(pom)

        and: "Boot parent block carries the mojo's springBootVersion"
        String contents = Files.readString(pom)
        contents.contains("<artifactId>spring-boot-starter-parent</artifactId>")
        contents.contains("<version>4.1.0</version>")

        and: "BOM import block carries the mojo's jaiclawVersion"
        contents.contains("<artifactId>jaiclaw-bom</artifactId>")
        contents.contains("<version>1.0.0-SNAPSHOT</version>")

        and: "the stale defaults never leak"
        !contents.contains("3.5.14")
        !contents.contains("0.6.0-SNAPSHOT")
    }
}

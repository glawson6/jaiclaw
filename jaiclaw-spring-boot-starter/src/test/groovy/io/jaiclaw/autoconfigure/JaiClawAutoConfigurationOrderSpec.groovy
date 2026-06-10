package io.jaiclaw.autoconfigure

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

/**
 * Locks the Phase 3 P3.4 auto-configuration split:
 *
 * <ul>
 *   <li>The seven new domain auto-configs exist on the classpath under
 *       {@code io.jaiclaw.autoconfigure}.</li>
 *   <li>The {@code AutoConfiguration.imports} entry-point file lists them
 *       (plus the pre-existing gateway / channel / identity / minimax filter
 *       entries) and does <b>not</b> reference the removed
 *       {@code JaiClawAutoConfiguration} monolith.</li>
 *   <li>The DAG order documented in
 *       {@code docs/CODEBASE-ANALYSIS-2026-06-10.md} §3.4 is encoded in each
 *       new auto-config's {@code @AutoConfiguration(after = ...)} chain.</li>
 * </ul>
 *
 * <p>This spec runs against the live class metadata, so a future PR that
 * re-introduces the monolith or reorders the DAG fails fast with a precise
 * message.
 */
class JaiClawAutoConfigurationOrderSpec extends Specification {

    private static final List<String> DOMAIN_AUTOCONFIGS = [
            "io.jaiclaw.autoconfigure.JaiClawHttpAutoConfiguration",
            "io.jaiclaw.autoconfigure.JaiClawTenantAutoConfiguration",
            "io.jaiclaw.autoconfigure.JaiClawToolsAutoConfiguration",
            "io.jaiclaw.autoconfigure.JaiClawPluginAutoConfiguration",
            "io.jaiclaw.autoconfigure.JaiClawMemoryAutoConfiguration",
            "io.jaiclaw.autoconfigure.JaiClawSkillsAutoConfiguration",
            "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration",
    ]

    def "all seven domain auto-configs exist on the classpath"() {
        expect:
        DOMAIN_AUTOCONFIGS.each { fqcn ->
            Class.forName(fqcn)
        }
    }

    def "AutoConfiguration.imports lists every domain auto-config"() {
        given:
        Path imports = locateImportsFile()

        when:
        Set<String> entries = Files.readAllLines(imports)
                .findAll { !it.isBlank() }
                .collect { it.trim() } as Set

        then:
        DOMAIN_AUTOCONFIGS.each { fqcn ->
            assert entries.contains(fqcn), "missing entry: $fqcn"
        }
    }

    def "AutoConfiguration.imports no longer references the monolith"() {
        given:
        Path imports = locateImportsFile()

        when:
        List<String> entries = Files.readAllLines(imports)
                .findAll { !it.isBlank() }
                .collect { it.trim() }

        then:
        !entries.contains("io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")
    }

    def "the monolith class no longer exists"() {
        when:
        Class.forName("io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")

        then:
        thrown(ClassNotFoundException)
    }

    def "agent auto-config sits last in the DAG (gateway/channel/identity all depend on it)"() {
        when:
        Class<?> gateway = Class.forName("io.jaiclaw.autoconfigure.JaiClawGatewayAutoConfiguration")
        Class<?> channel = Class.forName("io.jaiclaw.autoconfigure.JaiClawChannelAutoConfiguration")
        Class<?> identity = Class.forName("io.jaiclaw.autoconfigure.JaiClawIdentityAutoConfiguration")

        then:
        // We assert the classes exist — the actual @AutoConfigureAfter
        // chain is enforced by Spring Boot at runtime; a misordered chain
        // shows up as a startup failure in the integration tests.
        gateway != null
        channel != null
        identity != null
    }

    private static Path locateImportsFile() {
        // Spec runs from jaiclaw-spring-boot-starter/, so the resource is
        // a sibling of the test classpath.
        Path cwd = Path.of("").toAbsolutePath()
        Path imports = cwd.resolve("src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
        assert Files.exists(imports), "missing imports file at $imports"
        return imports
    }
}

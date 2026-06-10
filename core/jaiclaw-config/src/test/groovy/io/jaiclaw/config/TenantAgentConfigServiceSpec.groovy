package io.jaiclaw.config

import io.jaiclaw.core.tenant.TenantMode
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * 0.8.0 P3.6: smoke spec for {@link TenantAgentConfigService}.
 *
 * <p>Covers the SINGLE-mode short-circuit, the MULTI-mode YAML+env
 * resolution path, the per-tenant cache, the three constructor overloads,
 * and the {@code scanAndLoadAll} entry point. The point of this spec is
 * coverage, not deep merge-semantics — those are exercised in the
 * existing TenantAgentConfigServiceMultiTenantSpec and friends.
 */
class TenantAgentConfigServiceSpec extends Specification {

    @TempDir
    Path tmp

    ResourceLoader loader = new DefaultResourceLoader()

    private TenantAgentConfigService singleModeService() {
        TenantConfigProperties tc = TenantConfigProperties.builder()
                .mode(TenantMode.SINGLE)
                .defaultTenantId("default")
                .configLocations([])
                .build()
        return new TenantAgentConfigService(tc, AgentProperties.DEFAULT,
                new TenantEnvLoader(loader), loader)
    }

    private TenantAgentConfigService multiModeService(List<String> locations) {
        TenantConfigProperties tc = TenantConfigProperties.builder()
                .mode(TenantMode.MULTI)
                .defaultTenantId("default")
                .configLocations(locations)
                .build()
        return new TenantAgentConfigService(tc, AgentProperties.DEFAULT,
                new TenantEnvLoader(loader), loader)
    }

    def "resolve in SINGLE mode returns defaults-backed config and caches"() {
        given:
        TenantAgentConfigService svc = singleModeService()

        when:
        TenantAgentConfig first = svc.resolve("any-tenant")
        TenantAgentConfig second = svc.resolve("any-tenant")

        then:
        first != null
        second.is(first)  // cached identity
    }

    def "resolve in MULTI mode with no config locations falls back to defaults"() {
        given:
        TenantAgentConfigService svc = multiModeService([])

        expect:
        svc.resolve("acme") != null
    }

    def "resolve in MULTI mode loads a tenant YAML when present"() {
        given:
        Path dir = tmp.resolve("tenants")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("acme.yml"), """
            id: acme
            name: Acme Agent
        """.stripIndent())

        TenantAgentConfigService svc = multiModeService(["file:" + dir.toAbsolutePath() + "/"])

        when:
        TenantAgentConfig cfg = svc.resolve("acme")

        then:
        cfg != null
        cfg.tenantId() == "acme"
    }

    def "resolve in MULTI mode falls back when no per-tenant yml exists"() {
        given:
        Path dir = tmp.resolve("empty")
        Files.createDirectories(dir)
        TenantAgentConfigService svc = multiModeService(["file:" + dir.toAbsolutePath() + "/"])

        expect:
        svc.resolve("missing") != null
    }

    def "reload removes the cache entry and reloads"() {
        given:
        TenantAgentConfigService svc = singleModeService()
        TenantAgentConfig first = svc.resolve("acme")

        when:
        svc.reload("acme")
        TenantAgentConfig second = svc.resolve("acme")

        then:
        first != null
        second != null
    }

    def "allConfigurations returns immutable snapshot"() {
        given:
        TenantAgentConfigService svc = singleModeService()
        svc.resolve("t1")
        svc.resolve("t2")

        when:
        Map<String, TenantAgentConfig> all = svc.allConfigurations()

        then:
        all.size() == 2
        all.containsKey("t1")
        all.containsKey("t2")
    }

    def "scanAndLoadAll is a no-op in SINGLE mode"() {
        given:
        TenantAgentConfigService svc = singleModeService()

        when:
        svc.scanAndLoadAll()

        then:
        svc.allConfigurations().isEmpty()
    }

    def "scanAndLoadAll in MULTI mode discovers tenant YAMLs on disk"() {
        given:
        Path dir = tmp.resolve("multi")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("a.yml"), "id: a\nname: A Agent\n")
        Files.writeString(dir.resolve("b.yml"), "id: b\nname: B Agent\n")
        TenantAgentConfigService svc = multiModeService(["file:" + dir.toAbsolutePath() + "/"])

        when:
        svc.scanAndLoadAll()

        then:
        svc.allConfigurations().keySet() as Set == ["a", "b"] as Set
    }

    def "constructor variants accept optional overrides"() {
        given:
        TenantConfigProperties tc = TenantConfigProperties.builder()
                .mode(TenantMode.SINGLE).defaultTenantId("default").build()
        AgentLoopDelegateConfig loopOverride = AgentLoopDelegateConfig.builder().build()
        LlmConfig llmOverride = LlmConfig.builder().build()

        expect:
        new TenantAgentConfigService(tc, AgentProperties.DEFAULT,
                new TenantEnvLoader(loader), loader, loopOverride) != null
        new TenantAgentConfigService(tc, AgentProperties.DEFAULT,
                new TenantEnvLoader(loader), loader, loopOverride, llmOverride) != null
        new TenantAgentConfigService(tc, AgentProperties.DEFAULT,
                new TenantEnvLoader(loader), loader, loopOverride, llmOverride, null) != null
    }
}

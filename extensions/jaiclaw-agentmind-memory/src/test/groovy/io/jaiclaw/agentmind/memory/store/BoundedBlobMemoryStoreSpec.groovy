package io.jaiclaw.agentmind.memory.store

import tools.jackson.databind.ObjectMapper
import io.jaiclaw.core.agent.MemoryOverflowException
import io.jaiclaw.core.agent.StaleMemoryVersionException
import io.jaiclaw.core.model.MemoryDocument
import io.jaiclaw.core.model.MemoryScope
import io.jaiclaw.core.tenant.TenantGuard
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class BoundedBlobMemoryStoreSpec extends Specification {

    @TempDir
    Path tmp

    TenantGuard singleTenant = Mock() { isMultiTenant() >> false }
    TenantGuard multiTenant = Mock() { isMultiTenant() >> true }
    ObjectMapper mapper = new ObjectMapper()

    BoundedBlobMemoryStore single() { new BoundedBlobMemoryStore(tmp, singleTenant, mapper) }
    BoundedBlobMemoryStore multi() { new BoundedBlobMemoryStore(tmp, multiTenant, mapper) }

    // ---------- scope dispatch / path layout ----------

    def "SINGLE-mode TENANT Memory lands at root/TENANT.md"() {
        when:
        single().saveMemory(MemoryDocument.forTenant("default", "shared knowledge", 4096))

        then:
        Files.exists(tmp.resolve("TENANT.md"))
    }

    def "MULTI-mode TENANT Memory lands at root/{tenantId}/TENANT.md"() {
        when:
        multi().saveMemory(MemoryDocument.forTenant("acme", "shared knowledge", 4096))

        then:
        Files.exists(tmp.resolve("acme").resolve("TENANT.md"))
    }

    def "AGENT Memory lands under agents/{agentId}/MEMORY.md"() {
        when:
        multi().saveMemory(MemoryDocument.forAgent("acme", "support-bot", "agent notes", 2200))

        then:
        Files.exists(tmp.resolve("acme").resolve("agents").resolve("support-bot").resolve("MEMORY.md"))
    }

    def "PEER Memory lands under users/{peerId}/agents/{agentId}/USER.md"() {
        when:
        multi().saveMemory(MemoryDocument.forPeer("acme", "support-bot", "user-42", "peer notes", 1375))

        then:
        Files.exists(tmp.resolve("acme").resolve("users").resolve("user-42")
                .resolve("agents").resolve("support-bot").resolve("USER.md"))
    }

    def "all three scopes coexist for the same tenant"() {
        given:
        BoundedBlobMemoryStore p = multi()

        when:
        p.saveMemory(MemoryDocument.forTenant("acme", "tenant md", 4096))
        p.saveMemory(MemoryDocument.forAgent("acme", "bot", "agent md", 2200))
        p.saveMemory(MemoryDocument.forPeer("acme", "bot", "user-1", "peer md", 1375))

        then:
        Files.exists(tmp.resolve("acme").resolve("TENANT.md"))
        Files.exists(tmp.resolve("acme").resolve("agents").resolve("bot").resolve("MEMORY.md"))
        Files.exists(tmp.resolve("acme").resolve("users").resolve("user-1")
                .resolve("agents").resolve("bot").resolve("USER.md"))
    }

    // ---------- round-trip ----------

    def "save then find round-trips content + budget + version"() {
        given:
        BoundedBlobMemoryStore p = single()
        MemoryDocument saved = p.saveMemory(
                MemoryDocument.forAgent("default", "bot", "# Notes\nThings to remember.", 2200))

        when:
        Optional<MemoryDocument> loaded = p.findMemory("default", MemoryScope.AGENT, "bot", null)

        then:
        loaded.present
        loaded.get().content() == "# Notes\nThings to remember."
        loaded.get().scope() == MemoryScope.AGENT
        loaded.get().charBudget() == 2200
        loaded.get().version() == saved.version()
    }

    def "find on a missing Memory returns empty"() {
        expect:
        single().findMemory("default", MemoryScope.AGENT, "ghost", null).empty
    }

    // ---------- optimistic CAS ----------

    def "first write succeeds with version 0"() {
        when:
        MemoryDocument out = single().saveMemory(
                MemoryDocument.forAgent("default", "bot", "v0", 2200))

        then:
        out.version() == 0L
    }

    def "writing a newer version replaces content"() {
        given:
        BoundedBlobMemoryStore p = single()
        MemoryDocument v0 = p.saveMemory(MemoryDocument.forAgent("default", "bot", "v0", 2200))

        when:
        MemoryDocument v1 = p.saveMemory(v0.withContent("v1"))

        then:
        v1.version() == 1L
        p.findMemory("default", MemoryScope.AGENT, "bot", null).get().content() == "v1"
    }

    def "writing the same version is rejected as stale"() {
        given:
        BoundedBlobMemoryStore p = single()
        MemoryDocument v0 = p.saveMemory(MemoryDocument.forAgent("default", "bot", "v0", 2200))

        when:
        p.saveMemory(v0)

        then:
        thrown(StaleMemoryVersionException)
    }

    // ---------- overflow ----------

    def "writing content longer than charBudget raises MemoryOverflowException"() {
        given:
        BoundedBlobMemoryStore p = single()
        String tooLong = "x" * 100

        when:
        p.saveMemory(MemoryDocument.forAgent("default", "bot", tooLong, 50))

        then:
        MemoryOverflowException e = thrown()
        e.scope() == MemoryScope.AGENT
        e.charBudget() == 50
        e.attemptedLength() == 100
    }

    def "writing content equal to charBudget is allowed"() {
        given:
        BoundedBlobMemoryStore p = single()
        String exact = "x" * 50

        when:
        MemoryDocument out = p.saveMemory(MemoryDocument.forAgent("default", "bot", exact, 50))

        then:
        out.content().length() == 50
    }

    // ---------- delete ----------

    def "delete removes the on-disk file"() {
        given:
        BoundedBlobMemoryStore p = single()
        p.saveMemory(MemoryDocument.forAgent("default", "bot", "x", 2200))

        when:
        p.deleteMemory("default", MemoryScope.AGENT, "bot", null)

        then:
        p.findMemory("default", MemoryScope.AGENT, "bot", null).empty
    }

    def "delete on a missing Memory is a no-op"() {
        when:
        single().deleteMemory("default", MemoryScope.AGENT, "ghost", null)

        then:
        notThrown(Exception)
    }
}

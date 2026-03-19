package io.jclaw.memory

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.LocalDate

class HybridSearchManagerSpec extends Specification {

    @TempDir
    Path tempDir

    WorkspaceMemoryManager workspaceMemory
    DailyLogAppender dailyLog
    HybridSearchManager searchManager

    def setup() {
        workspaceMemory = new WorkspaceMemoryManager(tempDir)
        dailyLog = new DailyLogAppender(tempDir)
        searchManager = new HybridSearchManager(workspaceMemory, dailyLog, 7.0)
    }

    def "searches across MEMORY.md content"() {
        given:
        workspaceMemory.writeMemory("# Notes\n- The server uses PostgreSQL database")

        when:
        def results = searchManager.search("PostgreSQL")

        then:
        !results.isEmpty()
        results[0].path() == "MEMORY.md"
    }

    def "searches across daily logs"() {
        given:
        dailyLog.append(LocalDate.now(), "Deployed version 2.3.1 to production")

        when:
        def results = searchManager.search("deployed production")

        then:
        !results.isEmpty()
    }

    def "applies temporal decay to older results"() {
        given:
        workspaceMemory.writeMemory("# Notes\n- keyword match here")
        dailyLog.append(LocalDate.now(), "keyword match today")

        when:
        def results = searchManager.search("keyword match")

        then:
        !results.isEmpty()
        // MEMORY.md has 0 age, so no decay
        results.every { it.score() > 0 }
    }

    def "returns empty for no match"() {
        given:
        workspaceMemory.writeMemory("# Notes\n- Something else entirely")

        expect:
        searchManager.search("xyznonexistent").isEmpty()
    }
}

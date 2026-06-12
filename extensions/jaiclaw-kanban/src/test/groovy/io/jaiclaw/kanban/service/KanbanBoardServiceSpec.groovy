package io.jaiclaw.kanban.service

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.TerminalKind
import io.jaiclaw.kanban.model.TransitionDefinition
import io.jaiclaw.kanban.persistence.YamlFileBoardStore
import io.jaiclaw.tasks.TaskStatus
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class KanbanBoardServiceSpec extends Specification {

    @TempDir
    Path tempDir

    private TenantGuard guard = new TenantGuard(TenantProperties.DEFAULT)

    private BoardDefinition board(String id) {
        new BoardDefinition(id, id, [], "backlog", [
                new ColumnDefinition("backlog", "Backlog", TaskStatus.QUEUED, null, false, null, null),
                new ColumnDefinition("done", "Done", TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [new TransitionDefinition("backlog", "done", "FINISH", [:])])
    }

    def "memory-only register survives within the process but not on a fresh instance"() {
        given:
        def service = new KanbanBoardService(guard)

        when:
        service.register(board("ephemeral"))

        then:
        service.get("ephemeral").isPresent()

        and: "a fresh instance has no knowledge of it"
        new KanbanBoardService(guard).get("ephemeral").isEmpty()
    }

    def "register with a writable store persists across a fresh service instance"() {
        given:
        def store = new YamlFileBoardStore(tempDir)
        def service = new KanbanBoardService(guard, store, true)

        when:
        service.register(board("persisted"))

        and: "boot a fresh service from the same store and reseed the cache"
        def fresh = new KanbanBoardService(guard, new YamlFileBoardStore(tempDir), true)
        fresh.cacheAll(store.findAll())

        then:
        fresh.get("persisted").isPresent()
    }

    def "register is rejected when writable=false"() {
        given:
        def store = new YamlFileBoardStore(tempDir)
        def service = new KanbanBoardService(guard, store, false)

        when:
        service.register(board("locked"))

        then:
        def ex = thrown(BoardWriteException)
        ex.message.contains("read-only")
        store.count() == 0L
    }

    def "remove is rejected when writable=false"() {
        given:
        def store = new YamlFileBoardStore(tempDir)
        def writable = new KanbanBoardService(guard, store, true)
        writable.register(board("doomed"))
        def readonly = new KanbanBoardService(guard, store, false)
        readonly.cacheAll(store.findAll())

        when:
        readonly.remove("doomed")

        then:
        thrown(BoardWriteException)
        store.findById("doomed").isPresent()
    }

    def "cache writes only to the in-memory cache and does NOT touch the store"() {
        given: "a writable store"
        def store = new YamlFileBoardStore(tempDir)
        def service = new KanbanBoardService(guard, store, true)

        when: "cache (not register) is used for bootstrap-style loading"
        service.cache(board("from-disk"))

        then:
        service.get("from-disk").isPresent()
        store.count() == 0L
    }

    def "isWritable reports the effective writability"() {
        expect:
        new KanbanBoardService(guard).isWritable()                       // memory-only is writable
        new KanbanBoardService(guard, new YamlFileBoardStore(tempDir), true).isWritable()
        !new KanbanBoardService(guard, new YamlFileBoardStore(tempDir), false).isWritable()
    }
}

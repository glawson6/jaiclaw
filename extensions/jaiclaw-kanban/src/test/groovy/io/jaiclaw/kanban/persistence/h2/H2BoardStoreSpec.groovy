package io.jaiclaw.kanban.persistence.h2

import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.TerminalKind
import io.jaiclaw.kanban.model.TransitionDefinition
import io.jaiclaw.tasks.TaskStatus
import org.h2.Driver
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class H2BoardStoreSpec extends Specification {

    static final AtomicInteger DB_SEQ = new AtomicInteger(0)
    JdbcTemplate jdbc
    H2BoardStore store

    def setup() {
        def url = "jdbc:h2:mem:boardstore-${DB_SEQ.incrementAndGet()};DB_CLOSE_DELAY=-1"
        def ds = new SimpleDriverDataSource(new Driver(), url, "sa", "")
        jdbc = new JdbcTemplate(ds)
        def sql = getClass().getResourceAsStream("/schema-kanban.sql")
                .getText(StandardCharsets.UTF_8.name())
        jdbc.execute(sql)
        store = new H2BoardStore(jdbc)
    }

    private BoardDefinition board(String id, List<String> tenantIds = []) {
        new BoardDefinition(id, "Board ${id}", tenantIds, "backlog", [
                new ColumnDefinition("backlog", "Backlog", TaskStatus.QUEUED, null, false, null, null),
                new ColumnDefinition("drafting", "Drafting", TaskStatus.RUNNING, 2, false, null, null),
                new ColumnDefinition("done", "Done", TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [
                new TransitionDefinition("backlog", "drafting", "START", [:]),
                new TransitionDefinition("drafting", "done", "FINISH", [:]),
        ])
    }

    def "save then findById round-trips the full BoardDefinition"() {
        given:
        def b = board("rt1", ["alpha", "beta"])

        when:
        store.save(b)
        def loaded = store.findById("rt1").orElse(null)

        then:
        loaded != null
        loaded.id() == "rt1"
        loaded.name() == "Board rt1"
        loaded.tenantIds() == ["alpha", "beta"]
        loaded.columns()*.state() == ["backlog", "drafting", "done"]
        loaded.column("drafting").get().wipLimit() == 2
        loaded.transitions()*.event() as Set == ["START", "FINISH"] as Set
    }

    def "save MERGE replaces the previous row"() {
        given:
        store.save(board("merge1"))
        def updated = new BoardDefinition("merge1", "Updated name", [],
                "backlog",
                board("merge1").columns(),
                board("merge1").transitions())

        when:
        store.save(updated)

        then:
        store.findById("merge1").get().name() == "Updated name"
        store.count() == 1L
    }

    def "findAll returns boards in id order"() {
        given:
        store.save(board("c"))
        store.save(board("a"))
        store.save(board("b"))

        when:
        def all = store.findAll()

        then:
        all*.id() == ["a", "b", "c"]
        store.count() == 3L
    }

    def "delete removes the row"() {
        given:
        store.save(board("d1"))

        expect:
        store.delete("d1")
        store.findById("d1").isEmpty()
        !store.delete("d1")     // already gone
    }

    def "findById of an unknown id returns empty"() {
        expect:
        store.findById("missing").isEmpty()
        store.findById(null).isEmpty()
    }
}

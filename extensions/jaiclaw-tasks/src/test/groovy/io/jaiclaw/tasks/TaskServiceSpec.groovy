package io.jaiclaw.tasks

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class TaskServiceSpec extends Specification {

    @TempDir
    Path tempDir

    TaskService service

    def setup() {
        def taskStore = new JsonFileTaskStore(tempDir)
        def flowStore = new JsonFileFlowStore(tempDir)
        service = new TaskService(taskStore, flowStore)
    }

    def "create task returns task with generated id"() {
        when:
        def task = service.createTask("Test Task", "Do something", null)

        then:
        task.id() != null
        task.name() == "Test Task"
        task.status() == TaskStatus.QUEUED
    }

    def "get task by id"() {
        given:
        def task = service.createTask("By ID", null, null)

        expect:
        service.getTask(task.id()).isPresent()
    }

    def "update status changes task status"() {
        given:
        def task = service.createTask("Updateable", null, null)

        when:
        def updated = service.updateStatus(task.id(), TaskStatus.RUNNING)

        then:
        updated.isPresent()
        updated.get().status() == TaskStatus.RUNNING
    }

    def "complete task sets result and status"() {
        given:
        def task = service.createTask("Completable", null, null)

        when:
        def completed = service.completeTask(task.id(), "Done!")

        then:
        completed.isPresent()
        completed.get().status() == TaskStatus.SUCCEEDED
        completed.get().result() == "Done!"
    }

    def "fail task sets error and status"() {
        given:
        def task = service.createTask("Failable", null, null)

        when:
        def failed = service.failTask(task.id(), "Something went wrong")

        then:
        failed.isPresent()
        failed.get().status() == TaskStatus.FAILED
        failed.get().error() == "Something went wrong"
    }

    def "delete task removes it"() {
        given:
        def task = service.createTask("Deleteme", null, null)

        when:
        def deleted = service.deleteTask(task.id())

        then:
        deleted
        service.getTask(task.id()).isEmpty()
    }

    def "list tasks returns all tasks"() {
        given:
        service.createTask("T1", null, null)
        service.createTask("T2", null, null)

        expect:
        service.listTasks().size() == 2
    }
}

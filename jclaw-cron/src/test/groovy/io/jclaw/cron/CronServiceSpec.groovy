package io.jclaw.cron

import io.jclaw.core.model.CronJob
import io.jclaw.core.model.CronJobResult
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant

class CronServiceSpec extends Specification {

    @TempDir
    Path tempDir

    CronJobStore store
    CronJobExecutor executor
    CronService service

    def setup() {
        store = new JsonFileCronJobStore(tempDir.resolve("cron-jobs.json"))
        executor = new CronJobExecutor({ job -> "Response for: " + job.prompt() })
        service = new CronService(store, executor, 3, 300)
    }

    def cleanup() {
        service.stop()
    }

    def "addJob stores and returns job with next run time"() {
        given:
        def job = new CronJob("job1", "Test Job", "default", "0 9 * * *", "UTC",
                "Check status", null, null, true, null, null)

        when:
        def added = service.addJob(job)

        then:
        added.id() == "job1"
        added.nextRunAt() != null
        service.listJobs().size() == 1
    }

    def "removeJob deletes job"() {
        given:
        service.addJob(new CronJob("job1", "Test", "default", "0 9 * * *", "UTC",
                "test", null, null, true, null, null))

        when:
        def removed = service.removeJob("job1")

        then:
        removed
        service.listJobs().isEmpty()
    }

    def "runNow executes job immediately"() {
        given:
        service.addJob(new CronJob("job1", "Test", "default", "0 9 * * *", "UTC",
                "Check pods", null, null, true, null, null))

        when:
        def result = service.runNow("job1")

        then:
        result instanceof CronJobResult.Success
        ((CronJobResult.Success) result).agentResponse().contains("Check pods")
    }

    def "runNow returns failure for unknown job"() {
        when:
        def result = service.runNow("unknown")

        then:
        result instanceof CronJobResult.Failure
    }

    def "getHistory tracks job runs"() {
        given:
        service.addJob(new CronJob("job1", "Test", "default", "0 9 * * *", "UTC",
                "test", null, null, true, null, null))
        service.runNow("job1")

        when:
        def history = service.getHistory("job1")

        then:
        history.size() == 1
    }
}

package io.jclaw.cronmanager

import io.jclaw.core.model.CronJob
import io.jclaw.cron.CronService
import io.jclaw.cronmanager.batch.CronBatchJobFactory
import io.jclaw.cronmanager.model.CronExecutionRecord
import io.jclaw.cronmanager.model.CronJobDefinition
import io.jclaw.cronmanager.persistence.CronExecutionStore
import io.jclaw.cronmanager.persistence.CronJobDefinitionStore
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.launch.JobLauncher
import spock.lang.Specification

import java.time.Instant

class CronJobManagerServiceSpec extends Specification {

    CronJobDefinitionStore definitionStore = Mock()
    CronExecutionStore executionStore = Mock()
    CronService cronService = Mock()
    CronBatchJobFactory batchJobFactory = Mock()
    JobLauncher jobLauncher = Mock()

    CronJobManagerService service

    def setup() {
        service = new CronJobManagerService(
                definitionStore, executionStore, cronService, batchJobFactory, jobLauncher)
    }

    def "initialize performs crash recovery and starts scheduler"() {
        given:
        def orphaned = [
                new CronExecutionRecord("run1", "job1", "Test", "STARTED", null, Instant.now(), null)
        ]

        when:
        service.initialize()

        then:
        1 * executionStore.findStartedButNotCompleted() >> orphaned
        1 * executionStore.updateStatus("run1", "FAILED", _, _)
        1 * cronService.start()
        _ * definitionStore.findAll() >> []
        _ * definitionStore.findEnabled() >> []
    }

    def "initialize with no orphaned records just starts scheduler"() {
        when:
        service.initialize()

        then:
        1 * executionStore.findStartedButNotCompleted() >> []
        1 * cronService.start()
        0 * executionStore.updateStatus(_, _, _, _)
        _ * definitionStore.findAll() >> []
        _ * definitionStore.findEnabled() >> []
    }

    def "createJob saves definition and schedules via CronService"() {
        given:
        def cronJob = new CronJob("job1", "Test Job", "default", "0 9 * * *", "UTC",
                "Check status", null, null, true, null, null)
        def definition = new CronJobDefinition(cronJob)

        when:
        def result = service.createJob(definition)

        then:
        2 * definitionStore.save(_)  // initial save + after scheduling
        1 * cronService.addJob(_) >> cronJob.withNextRunAt(Instant.now().plusSeconds(3600))
        result.cronJob().name() == "Test Job"
    }

    def "createJob assigns ID when blank"() {
        given:
        def cronJob = new CronJob("", "No ID Job", "default", "0 9 * * *", "UTC",
                "prompt", null, null, true, null, null)
        def definition = new CronJobDefinition(cronJob)

        when:
        def result = service.createJob(definition)

        then:
        2 * definitionStore.save({ it.id() != "" && it.id() != null })
        1 * cronService.addJob({ it.id() != "" }) >> { CronJob j -> j.withNextRunAt(Instant.now()) }
    }

    def "getJob delegates to definition store"() {
        given:
        def cronJob = new CronJob("job1", "Test", "default", "0 9 * * *", "UTC",
                "prompt", null, null, true, null, null)
        def definition = new CronJobDefinition(cronJob)
        definitionStore.findById("job1") >> Optional.of(definition)

        when:
        def result = service.getJob("job1")

        then:
        result.isPresent()
        result.get().cronJob().name() == "Test"
    }

    def "listJobs delegates to definition store"() {
        given:
        definitionStore.findAll() >> [
                new CronJobDefinition(new CronJob("j1", "A", "default", "* * * * *", "UTC",
                        "p", null, null, true, null, null)),
                new CronJobDefinition(new CronJob("j2", "B", "default", "* * * * *", "UTC",
                        "p", null, null, false, null, null))
        ]

        when:
        def jobs = service.listJobs()

        then:
        jobs.size() == 2
    }

    def "deleteJob removes from scheduler and store"() {
        when:
        service.deleteJob("job1")

        then:
        1 * cronService.removeJob("job1")
        1 * definitionStore.deleteById("job1") >> true
    }

    def "pauseJob disables job and updates scheduler"() {
        given:
        def cronJob = new CronJob("job1", "Test", "default", "0 9 * * *", "UTC",
                "prompt", null, null, true, null, null)
        def definition = new CronJobDefinition(cronJob)
        definitionStore.findById("job1") >> Optional.of(definition)

        when:
        def result = service.pauseJob("job1")

        then:
        result
        1 * definitionStore.updateEnabled("job1", false)
        1 * cronService.removeJob("job1")
        1 * cronService.addJob({ !it.enabled() })
    }

    def "resumeJob enables job and reschedules"() {
        given:
        def cronJob = new CronJob("job1", "Test", "default", "0 9 * * *", "UTC",
                "prompt", null, null, false, null, null)
        def definition = new CronJobDefinition(cronJob)
        definitionStore.findById("job1") >> Optional.of(definition)

        when:
        def result = service.resumeJob("job1")

        then:
        result
        1 * definitionStore.updateEnabled("job1", true)
        1 * cronService.removeJob("job1")
        1 * cronService.addJob({ it.enabled() })
    }

    def "pauseJob returns false for unknown job"() {
        given:
        definitionStore.findById("unknown") >> Optional.empty()

        when:
        def result = service.pauseJob("unknown")

        then:
        !result
    }

    def "runNow launches batch job"() {
        given:
        def cronJob = new CronJob("job1", "Test", "default", "0 9 * * *", "UTC",
                "prompt", null, null, true, null, null)
        def definition = new CronJobDefinition(cronJob)
        definitionStore.findById("job1") >> Optional.of(definition)
        def batchJob = Mock(Job)
        batchJobFactory.createJob(_, _) >> batchJob
        jobLauncher.run(_, _) >> Mock(JobExecution)

        when:
        def runId = service.runNow("job1")

        then:
        runId != null
    }

    def "runNow throws for unknown job"() {
        given:
        definitionStore.findById("unknown") >> Optional.empty()

        when:
        service.runNow("unknown")

        then:
        thrown(IllegalArgumentException)
    }

    def "getJobHistory delegates to execution store"() {
        given:
        executionStore.findByJobId("job1", 10) >> [
                new CronExecutionRecord("r1", "job1", "Test", "COMPLETED", "OK", Instant.now(), Instant.now())
        ]

        when:
        def history = service.getJobHistory("job1", 10)

        then:
        history.size() == 1
    }

    def "shutdown stops the scheduler"() {
        when:
        service.shutdown()

        then:
        1 * cronService.stop()
    }
}

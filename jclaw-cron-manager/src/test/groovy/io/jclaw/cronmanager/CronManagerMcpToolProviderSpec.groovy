package io.jclaw.cronmanager

import io.jclaw.core.model.CronJob
import io.jclaw.cronmanager.mcp.CronManagerMcpToolProvider
import io.jclaw.cronmanager.model.CronExecutionRecord
import io.jclaw.cronmanager.model.CronJobDefinition
import spock.lang.Specification

import java.time.Instant

class CronManagerMcpToolProviderSpec extends Specification {

    CronJobManagerService managerService = Mock()
    CronManagerMcpToolProvider provider

    def setup() {
        provider = new CronManagerMcpToolProvider(managerService)
    }

    def "server name is cron-manager"() {
        expect:
        provider.getServerName() == "cron-manager"
    }

    def "provides 8 tools"() {
        expect:
        provider.getTools().size() == 8
    }

    def "tool names are correct"() {
        when:
        def toolNames = provider.getTools().collect { it.name() }

        then:
        toolNames.containsAll(["create_job", "list_jobs", "get_job", "delete_job",
                                "run_job_now", "get_job_history", "pause_job", "resume_job"])
    }

    def "list_jobs dispatches to manager service"() {
        given:
        def cronJob = new CronJob("j1", "Test", "default", "0 9 * * *", "UTC",
                "prompt", null, null, true, null, null)
        managerService.listJobs() >> [new CronJobDefinition(cronJob)]

        when:
        def result = provider.execute("list_jobs", [:], null)

        then:
        !result.isError()
        result.content().contains("Test")
    }

    def "get_job returns error for missing job"() {
        given:
        managerService.getJob("unknown") >> Optional.empty()

        when:
        def result = provider.execute("get_job", [jobId: "unknown"], null)

        then:
        result.isError()
        result.content().contains("not found")
    }

    def "delete_job dispatches correctly"() {
        given:
        managerService.deleteJob("j1") >> true

        when:
        def result = provider.execute("delete_job", [jobId: "j1"], null)

        then:
        !result.isError()
        result.content().contains("deleted")
    }

    def "pause_job dispatches correctly"() {
        given:
        managerService.pauseJob("j1") >> true

        when:
        def result = provider.execute("pause_job", [jobId: "j1"], null)

        then:
        !result.isError()
        result.content().contains("paused")
    }

    def "resume_job dispatches correctly"() {
        given:
        managerService.resumeJob("j1") >> true

        when:
        def result = provider.execute("resume_job", [jobId: "j1"], null)

        then:
        !result.isError()
        result.content().contains("resumed")
    }

    def "run_job_now dispatches correctly"() {
        given:
        managerService.runNow("j1") >> "run-123"

        when:
        def result = provider.execute("run_job_now", [jobId: "j1"], null)

        then:
        !result.isError()
        result.content().contains("run-123")
    }

    def "get_job_history returns results"() {
        given:
        managerService.getJobHistory("j1", 20) >> [
                new CronExecutionRecord("r1", "j1", "Test", "COMPLETED", "OK", Instant.now(), Instant.now())
        ]

        when:
        def result = provider.execute("get_job_history", [jobId: "j1"], null)

        then:
        !result.isError()
        result.content().contains("COMPLETED")
    }

    def "unknown tool returns error"() {
        when:
        def result = provider.execute("nonexistent_tool", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }

    def "create_job requires schedule and prompt"() {
        when:
        def result = provider.execute("create_job", [name: "Test"], null)

        then:
        result.isError()
        result.content().contains("required")
    }

    def "create_job succeeds with required fields"() {
        given:
        managerService.createJob(_) >> { CronJobDefinition jobDef ->
            jobDef
        }

        when:
        def result = provider.execute("create_job",
                [name: "Test", schedule: "0 9 * * *", prompt: "Do something"], null)

        then:
        !result.isError()
    }
}

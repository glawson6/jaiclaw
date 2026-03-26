package io.jclaw.cronmanager

import io.jclaw.agent.AgentRuntime
import io.jclaw.agent.AgentRuntimeContext
import io.jclaw.agent.session.SessionManager
import io.jclaw.core.model.AssistantMessage
import io.jclaw.core.model.CronJob
import io.jclaw.core.model.Session
import io.jclaw.core.tool.ToolProfile
import io.jclaw.cronmanager.agent.CronAgentFactory
import io.jclaw.cronmanager.model.CronJobDefinition
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class CronAgentFactorySpec extends Specification {

    SessionManager sessionManager = Mock()
    AgentRuntime agentRuntime = Mock()
    CronAgentFactory factory

    def setup() {
        factory = new CronAgentFactory(sessionManager, agentRuntime)
    }

    def "executeJob creates session with correct key format"() {
        given:
        def cronJob = new CronJob("job1", "Test", "default", "0 9 * * *", "UTC",
                "Check status", null, null, true, null, null)
        def jobDef = new CronJobDefinition(cronJob)
        def runId = "run-abc"
        def session = Session.create("sid", "cron:job1:run-abc", "default", null)
        def response = new AssistantMessage("msg1", "All systems operational", "default")

        when:
        def result = factory.executeJob(jobDef, runId)

        then:
        1 * sessionManager.getOrCreate("cron:job1:run-abc", "default") >> session
        1 * agentRuntime.run("Check status", { AgentRuntimeContext ctx ->
            ctx.sessionKey() == "cron:job1:run-abc" &&
            ctx.agentId() == "default" &&
            ctx.toolProfile() == ToolProfile.MINIMAL
        }) >> CompletableFuture.completedFuture(response)
        1 * sessionManager.reset("cron:job1:run-abc")
        result == "All systems operational"
    }

    def "executeJob uses job's tool profile"() {
        given:
        def cronJob = new CronJob("job2", "Coding Job", "default", "0 * * * *", "UTC",
                "Write code", null, null, true, null, null)
        def jobDef = new CronJobDefinition(cronJob, null, null, null, ToolProfile.CODING, [])
        def session = Session.create("sid", "cron:job2:run-xyz", "default", null)
        def response = new AssistantMessage("msg2", "Code written", "default")

        when:
        factory.executeJob(jobDef, "run-xyz")

        then:
        1 * sessionManager.getOrCreate("cron:job2:run-xyz", "default") >> session
        1 * agentRuntime.run("Write code", { AgentRuntimeContext ctx ->
            ctx.toolProfile() == ToolProfile.CODING
        }) >> CompletableFuture.completedFuture(response)
        1 * sessionManager.reset("cron:job2:run-xyz")
    }

    def "executeJob cleans up session even on failure"() {
        given:
        def cronJob = new CronJob("job3", "Failing Job", "default", "0 * * * *", "UTC",
                "fail", null, null, true, null, null)
        def jobDef = new CronJobDefinition(cronJob)
        def session = Session.create("sid", "cron:job3:run-err", "default", null)

        when:
        factory.executeJob(jobDef, "run-err")

        then:
        1 * sessionManager.getOrCreate("cron:job3:run-err", "default") >> session
        1 * agentRuntime.run(_, _) >> CompletableFuture.failedFuture(new RuntimeException("boom"))
        1 * sessionManager.reset("cron:job3:run-err")
        thrown(Exception)
    }
}

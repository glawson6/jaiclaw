package io.jaiclaw.cronmanager

import io.jaiclaw.agent.AgentRuntime
import io.jaiclaw.agent.AgentRuntimeContext
import io.jaiclaw.agent.session.SessionManager
import io.jaiclaw.core.model.AssistantMessage
import io.jaiclaw.core.model.CronJob
import io.jaiclaw.core.model.Session
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.cronmanager.agent.CronAgentFactory
import io.jaiclaw.cronmanager.model.CronJobDefinition
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
        def cronJob = CronJob.builder().id("job1").name("Test").agentId("default")
                .schedule("0 9 * * *").prompt("Check status").enabled(true).build()
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
        def cronJob = CronJob.builder().id("job2").name("Coding Job").agentId("default")
                .schedule("0 * * * *").prompt("Write code").enabled(true).build()
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
        def cronJob = CronJob.builder().id("job3").name("Failing Job").agentId("default")
                .schedule("0 * * * *").prompt("fail").enabled(true).build()
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

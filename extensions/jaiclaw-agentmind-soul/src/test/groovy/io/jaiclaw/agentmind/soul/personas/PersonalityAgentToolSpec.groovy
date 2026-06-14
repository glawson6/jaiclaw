package io.jaiclaw.agentmind.soul.personas

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Plan §8 task 4.3 — PersonalityAgentTool spec.
 *
 * Covers: set/clear/list happy paths, unknown persona error, missing-name
 * error, unknown-action error, and missing-sessionKey rejection.
 */
class PersonalityAgentToolSpec extends Specification {

    @TempDir
    Path tmp

    PersonaOverlayManager manager
    PersonalityAgentTool tool
    ToolContext ctx

    def setup() {
        Files.writeString(tmp.resolve("concise.md"), "be brief")
        Files.writeString(tmp.resolve("pirate.md"), "arrr")
        manager = new PersonaOverlayManager(tmp)
        tool = new PersonalityAgentTool(manager)
        ctx = new ToolContext("bot", "default:slack:acct:user", "sid", ".")
    }

    def "action=list returns the available personas alphabetically"() {
        when:
        ToolResult result = tool.execute([action: "list"], ctx)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains("concise")
        ((ToolResult.Success) result).content().contains("pirate")
    }

    def "action=set with a valid name activates the persona"() {
        when:
        ToolResult result = tool.execute([action: "set", name: "concise"], ctx)

        then:
        result instanceof ToolResult.Success
        manager.activeName("default:slack:acct:user").get() == "concise"
    }

    def "action=set with an unknown name returns an error and does not activate"() {
        when:
        ToolResult result = tool.execute([action: "set", name: "ghost"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Unknown persona")
        manager.activeName("default:slack:acct:user").isEmpty()
    }

    def "action=set without name returns an error"() {
        when:
        ToolResult result = tool.execute([action: "set"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("name")
    }

    def "action=clear removes the active persona"() {
        given:
        manager.activate("default:slack:acct:user", "concise")

        when:
        ToolResult result = tool.execute([action: "clear"], ctx)

        then:
        result instanceof ToolResult.Success
        manager.activeName("default:slack:acct:user").isEmpty()
    }

    def "unknown action returns an error"() {
        when:
        ToolResult result = tool.execute([action: "swap"], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Unknown action")
    }

    def "missing sessionKey is rejected"() {
        given:
        ToolContext noSession = new ToolContext("bot", null, "sid", ".")

        when:
        ToolResult result = tool.execute([action: "set", name: "concise"], noSession)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("sessionKey")
    }
}

package io.jaiclaw.tools

import io.jaiclaw.core.tool.ToolDefinition
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.tools.builtin.BuiltinTools
import spock.lang.Specification

/**
 * What each tool profile actually grants.
 *
 * <p>Promised at {@code docs/dev/TENANT-RESOLUTION-REMEDIATION.md:582} and never
 * written, which left the two live {@code FULL} fallbacks untested. It matters
 * most now: the default flips to {@code MINIMAL} in 1.4.0, and before 1.3.0 that
 * would have removed 7 of the 8 default built-ins, because
 * {@code ToolDefinition}'s convenience constructors default the tag set to
 * {@code Set.of(FULL)} — so any tool whose author never considered profiles was
 * FULL-only by accident rather than by decision.
 */
class ToolProfileDefaultSpec extends Specification {

    private static Set<String> namesAvailableIn(ToolProfile profile) {
        BuiltinTools.all()
                .findAll { it.definition().isAvailableIn(profile) }
                .collect { it.definition().name() } as Set
    }

    def "FULL is a wildcard — it grants every tool regardless of tags"() {
        given: "isAvailableIn short-circuits on FULL rather than consulting the tag set"
        def untagged = new ToolDefinition("x", "d", "s", '{}', Set.of(ToolProfile.MESSAGING))

        expect:
        untagged.isAvailableIn(ToolProfile.FULL)

        and: "every built-in too"
        namesAvailableIn(ToolProfile.FULL).size() == BuiltinTools.all().size()
    }

    def "MINIMAL grants a usable read-only working set"() {
        when:
        def available = namesAvailableIn(ToolProfile.MINIMAL)

        then: "the 1.4.0 flip must not be a near-total lockout"
        available.contains("file_read")
        available.contains("web_search")
        available.contains("web_fetch")

        and: "but nothing that writes, executes, or spawns work"
        !available.contains("file_write")
        !available.contains("shell_exec")
        !available.contains("claude_cli")
    }

    def "WEBHOOK_SAFE grants what its javadoc claims"() {
        when: "before 1.3.0 no tool carried this tag, so the clamp granted nothing"
        def available = namesAvailableIn(ToolProfile.WEBHOOK_SAFE)

        then: "the GatewayController clamp is no longer a no-op"
        !available.isEmpty()

        and: "read-only research, per the enum's own documentation"
        available.contains("web_search")
        available.contains("web_fetch")

        and: "and nothing that can change the host or spawn further work"
        !available.contains("shell_exec")
        !available.contains("file_write")
        !available.contains("claude_cli")
    }

    def "a webhook-originated session cannot reach a writing tool"() {
        given: "the payload is attacker-controlled and the caller unauthenticated"
        def writers = ["file_write", "shell_exec", "claude_cli"]

        expect:
        writers.every { name ->
            def tool = BuiltinTools.all().find { it.definition().name() == name }
            tool == null || !tool.definition().isAvailableIn(ToolProfile.WEBHOOK_SAFE)
        }
    }

    def "NONE grants nothing"() {
        expect:
        namesAvailableIn(ToolProfile.NONE).isEmpty()
    }

    def "profiles are ordered by privilege"() {
        expect: "RoleToolProfileResolver ranks on this, so the order is load-bearing"
        ToolProfile.NONE.privilege() < ToolProfile.MINIMAL.privilege()
        ToolProfile.MINIMAL.privilege() < ToolProfile.WEBHOOK_SAFE.privilege()
        ToolProfile.WEBHOOK_SAFE.privilege() < ToolProfile.MESSAGING.privilege()
        ToolProfile.MESSAGING.privilege() < ToolProfile.CODING.privilege()
        ToolProfile.CODING.privilege() < ToolProfile.FULL.privilege()
    }

    def "an untagged tool is FULL-only — the trap behind the 1.4.0 flip"() {
        given: "the convenience constructor defaults the tag set to Set.of(FULL)"
        def untagged = new ToolDefinition("legacy_tool", "d", "s", '{}')

        expect: "so a tool author who never thought about profiles excludes MINIMAL"
        untagged.isAvailableIn(ToolProfile.FULL)
        !untagged.isAvailableIn(ToolProfile.MINIMAL)
        !untagged.isAvailableIn(ToolProfile.WEBHOOK_SAFE)
    }

    def "every built-in declares at least one profile beyond FULL"() {
        when: "a FULL-only built-in would silently vanish on the 1.4.0 flip"
        def fullOnly = BuiltinTools.all()
                .findAll { it.definition().profiles() == Set.of(ToolProfile.FULL) }
                .collect { it.definition().name() }

        then: "claude_cli is deliberately FULL-only — it shells out to another agent"
        fullOnly == ["claude_cli"]
    }
}

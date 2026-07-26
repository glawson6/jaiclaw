package io.jaiclaw.autoconfigure

import io.jaiclaw.skills.SkillLoader
import spock.lang.Specification

/**
 * Locks the auto-splice behavior of the {@code object-rendering} bundled
 * skill: when {@code jaiclaw-ascii-render} is on the classpath (which it
 * is transitively for anyone using {@code jaiclaw-tools}), the bundled
 * skill loader discovers it and every JaiClaw agent's system prompt
 * receives the 4 fidelity rules.
 *
 * <p>A future refactor that moves the {@code SKILL.md} path or drops the
 * frontmatter fails this spec immediately rather than silently losing the
 * fidelity rules for every downstream app.
 */
class ObjectRenderingSkillDiscoverySpec extends Specification {

    def "SkillLoader.loadBundled() discovers the object-rendering skill from ascii-render's resources"() {
        given:
        def loader = new SkillLoader()

        when:
        def bundled = loader.loadBundled()

        then: "object-rendering is present in the discovered bundled skills"
        def objectRendering = bundled.find { it.name() == "object-rendering" }
        objectRendering != null

        and: "frontmatter fields match the shipped file"
        objectRendering.metadata().alwaysInclude()

        and: "content carries all four fidelity rules"
        objectRendering.content().contains("Knowledge-gap framing")
        objectRendering.content().contains("Naming forgery as forgery")
        objectRendering.content().contains("Byte-for-byte")
        objectRendering.content().contains("Verbatim-only reply")
    }
}

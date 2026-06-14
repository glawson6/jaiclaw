package io.jaiclaw.examples.agentmind

import io.jaiclaw.agentmind.soul.hook.SoulPromptInjector
import io.jaiclaw.agentmind.soul.personas.PersonaOverlayManager
import io.jaiclaw.core.agent.SoulProvider
import io.jaiclaw.core.hook.event.BeforePromptBuildEvent
import io.jaiclaw.core.model.Soul
import io.jaiclaw.core.model.SoulScope
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

/**
 * Plan tasks 4.5 + 4.11 — in-module demo spec that byte-compares the
 * assembled system prompt against the goldens shipped in
 * {@code .claude/skills/agentmind-e2e/golden/}.
 *
 * Two cases:
 *   1. No persona active — only agent Soul is layered.
 *   2. Pirate persona active — agent Soul + pirate persona overlay.
 *
 * If the goldens diverge, regenerate them by setting
 * {@code -Dagentmind.regenerate.goldens=true} on the test run.
 */
@SpringBootTest(
    classes = AgentMindDemoApplication,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "spring.main.web-application-type=none",
        "spring.ai.anthropic.api-key=test-not-used",
        "jaiclaw.agentmind.soul.personas.dir=\${java.io.tmpdir}/agentmind-demo-prompt-personas",
        "jaiclaw.agentmind.soul.root-dir=\${java.io.tmpdir}/agentmind-demo-prompt-soul",
        "jaiclaw.agentmind.memory.root-dir=\${java.io.tmpdir}/agentmind-demo-prompt-memory",
        "jaiclaw.agentmind.tendencies.root-dir=\${java.io.tmpdir}/agentmind-demo-prompt-tendencies"
    ]
)
class AgentMindPromptCompositionSpec extends Specification {

    static final Path GOLDEN_DIR = Path.of(
            System.getProperty("user.dir"),
            "src", "test", "resources", "goldens").toAbsolutePath().normalize()

    static final String IDENTITY = "You are AgentMind Demo, End-to-end demo of Soul + Memory + Tendencies + Personas.\n\nRespond directly to the user."

    @Autowired
    SoulPromptInjector injector

    @Autowired
    SoulProvider soulProvider

    @Autowired
    PersonaOverlayManager personaManager

    def setup() {
        // Seed an agent Soul so the injector has something to layer. The
        // root-dir property points at the JVM tmpdir, but the dir may
        // outlive the JVM across surefire forks — re-save only when absent.
        if (soulProvider.findSoul("default", SoulScope.AGENT, "default").isEmpty()) {
            soulProvider.saveSoul(Soul.forAgent("default", "default",
                    "## Voice\n\n- Be helpful and direct.\n- Cite sources when stating facts."))
        }
    }

    def setupSpec() {
        // Wipe any persisted state from prior runs so version bumps stay clean.
        deleteDir(Path.of(System.getProperty("java.io.tmpdir"), "agentmind-demo-prompt-soul"))
        deleteDir(Path.of(System.getProperty("java.io.tmpdir"), "agentmind-demo-prompt-personas"))
    }

    private static void deleteDir(Path dir) {
        if (!Files.exists(dir)) return
        Files.walk(dir).sorted({ a, b -> b.compareTo(a) }).forEach { p ->
            try { Files.deleteIfExists(p) } catch (IOException ignored) {}
        }
    }

    def "no persona active: prompt = identity + agent Soul + behaviour preamble"() {
        given:
        String prompt = composePrompt("default", "session-1")

        expect:
        byteCompareOrRegenerate("full-prompt.txt", prompt)
    }

    def "pirate persona active: prompt = identity + agent Soul + pirate persona + behaviour preamble"() {
        given:
        // PersonaOverlayManager's first-pass load happened before the seeder
        // ran. Force a reload now that personas exist on disk.
        personaManager.reload()
        boolean activated = personaManager.activate("session-2", "pirate")
        assert activated : "Pirate persona did not activate — manager sees: ${personaManager.available()}"
        String prompt = composePrompt("default", "session-2")

        expect:
        byteCompareOrRegenerate("full-prompt-pirate.txt", prompt)
    }

    // ---------- helpers ----------

    private String composePrompt(String agentId, String sessionKey) {
        BeforePromptBuildEvent event = BeforePromptBuildEvent.of(agentId, sessionKey, IDENTITY)
        BeforePromptBuildEvent rewritten = injector.rewrite(event)
        return rewritten != null ? rewritten.systemPrompt() : event.systemPrompt()
    }

    private boolean byteCompareOrRegenerate(String goldenName, String actual) {
        Path golden = GOLDEN_DIR.resolve(goldenName)
        if (Boolean.getBoolean("agentmind.regenerate.goldens")) {
            Files.createDirectories(golden.parent)
            Files.writeString(golden, actual)
            return true
        }
        if (!Files.exists(golden)) {
            throw new AssertionError("Golden file ${golden} does not exist. Re-run with " +
                    "-Dagentmind.regenerate.goldens=true to create it.")
        }
        String expected = Files.readString(golden)
        if (expected != actual) {
            throw new AssertionError(
                    "Prompt differs from ${golden}\n" +
                            "Expected:\n${expected}\n" +
                            "Actual:\n${actual}\n" +
                            "Re-run with -Dagentmind.regenerate.goldens=true to update.")
        }
        return true
    }
}

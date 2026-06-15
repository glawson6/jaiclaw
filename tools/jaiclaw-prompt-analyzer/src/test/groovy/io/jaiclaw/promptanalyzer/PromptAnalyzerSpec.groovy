package io.jaiclaw.promptanalyzer

import io.jaiclaw.core.skill.SkillDefinition
import io.jaiclaw.core.skill.SkillMetadata
import io.jaiclaw.skills.SkillLoader
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class PromptAnalyzerSpec extends Specification {

    @TempDir
    Path tempDir

    SkillLoader mockSkillLoader = Mock()

    ProjectScanner scanner = new ProjectScanner(mockSkillLoader)

    private Path createProject(String appYml) {
        Path resources = tempDir.resolve("src/main/resources")
        Files.createDirectories(resources)
        Files.writeString(resources.resolve("application.yml"), appYml)
        return tempDir
    }

    def "parses application.yml with allow-bundled: [] and reports 0 skills"() {
        given:
        Path project = createProject("""
jaiclaw:
  skills:
    allow-bundled: []
  agent:
    agents:
      default:
        tools:
          profile: full
""")
        mockSkillLoader.loadConfigured([], null) >> []

        when:
        AnalysisReport report = scanner.analyze(project)

        then:
        report.skillCount() == 0
        report.skillsTokens() == 0
        report.toolProfile() == "full"
        report.warnings().isEmpty()
    }

    def "parses application.yml with allow-bundled: [*] and reports all skills with warning"() {
        given:
        Path project = createProject("""
jaiclaw:
  agent:
    agents:
      default:
        tools:
          profile: full
""")
        // No allow-bundled configured → defaults to ["*"]
        List<SkillDefinition> allSkills = (1..59).collect { i ->
            new SkillDefinition("skill-$i", "Skill $i", "x" * 400, SkillMetadata.EMPTY)
        }
        mockSkillLoader.loadConfigured(["*"], null) >> allSkills

        when:
        AnalysisReport report = scanner.analyze(project)

        then:
        report.skillCount() == 59
        report.skillsTokens() > 0
        report.warnings().any { it.contains("allow-bundled not configured") }
    }

    def "parses application.yml with allow-bundled: [coding] and reports 1 skill"() {
        given:
        Path project = createProject("""
jaiclaw:
  skills:
    allow-bundled:
      - coding
  agent:
    agents:
      default:
        tools:
          profile: full
""")
        SkillDefinition codingSkill = new SkillDefinition("coding", "Coding skill",
                "You are a coding assistant..." * 10, SkillMetadata.EMPTY)
        mockSkillLoader.loadConfigured(["coding"], null) >> [codingSkill]

        when:
        AnalysisReport report = scanner.analyze(project)

        then:
        report.skillCount() == 1
        report.skillNames() == ["coding"]
        report.skillsTokens() > 0
        report.warnings().isEmpty()
    }

    def "missing application.yml produces error"() {
        given:
        Path emptyProject = tempDir.resolve("empty")
        Files.createDirectories(emptyProject)

        when:
        scanner.analyze(emptyProject)

        then:
        IOException ex = thrown()
        ex.message.contains("No application.yml found")
    }

    def "prompt-check with threshold above estimate passes"() {
        given:
        Path project = createProject("""
jaiclaw:
  skills:
    allow-bundled: []
  agent:
    agents:
      default:
        tools:
          profile: full
""")
        mockSkillLoader.loadConfigured([], null) >> []

        when:
        AnalysisReport report = scanner.analyze(project)

        then:
        report.estimatedTotalTokens() < 5000
    }

    def "prompt-check with threshold below estimate fails"() {
        given:
        Path project = createProject("""
jaiclaw:
  skills:
    allow-bundled: []
  agent:
    agents:
      default:
        tools:
          profile: full
""")
        mockSkillLoader.loadConfigured([], null) >> []

        when:
        AnalysisReport report = scanner.analyze(project)

        then:
        // Built-in tools alone produce ~1800+ tokens, so threshold 1000 should fail
        report.estimatedTotalTokens() > 1000
    }

    def "detects custom ToolCallback implementations in source"() {
        given:
        Path project = createProject("""
jaiclaw:
  skills:
    allow-bundled: []
  agent:
    agents:
      default:
        tools:
          profile: full
""")
        // Create a fake Java source file with ToolCallback
        Path javaDir = tempDir.resolve("src/main/java/com/example")
        Files.createDirectories(javaDir)
        Files.writeString(javaDir.resolve("MyTool.java"), """
package com.example;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolDefinition;
public class MyTool implements ToolCallback {
    private static final ToolDefinition DEF = new ToolDefinition(
        "my_tool", "Does something useful", "custom",
        "{\\"type\\":\\"object\\",\\"properties\\":{}}"
    );
}
""")
        mockSkillLoader.loadConfigured([], null) >> []

        when:
        AnalysisReport report = scanner.analyze(project)

        then:
        report.customToolCount() == 1
    }

    def "report format produces readable output"() {
        given:
        AnalysisReport report = new AnalysisReport(
                "test-app", 148, 0, 0, [], 1872, 6, 0, 0, 0, 2020, "full", [])

        when:
        String formatted = report.format()

        then:
        formatted.contains("Prompt Token Analysis: test-app")
        formatted.contains("System prompt")
        formatted.contains("148")
        formatted.contains("Skills (0 loaded)")
        formatted.contains("Built-in tools (6)")
        formatted.contains("1,872")
        formatted.contains("2,020")
        formatted.contains("(none)")
    }

    def "report format includes warnings when present"() {
        given:
        AnalysisReport report = new AnalysisReport(
                "my-app", 148, 26412, 59, ["*"], 1872, 6, 0, 0, 0, 28432, "full",
                ["allow-bundled not configured"])

        when:
        String formatted = report.format()

        then:
        formatted.contains("! allow-bundled not configured")
        !formatted.contains("(none)")
    }

    def "estimateTokens matches LlmTraceLogger formula"() {
        expect:
        ProjectScanner.estimateTokens("") == 0
        ProjectScanner.estimateTokens(null as String) == 0
        ProjectScanner.estimateTokens("abcd") == 1  // (4+2)/4 = 1
        ProjectScanner.estimateTokens("a" * 100) == 25  // (100+2)/4 = 25
        ProjectScanner.estimateTokens("a" * 1000) == 250  // (1000+2)/4 = 250
    }

    def "profile none filters out all built-in tools"() {
        given:
        Path project = createProject("""
jaiclaw:
  skills:
    allow-bundled: []
  agent:
    agents:
      default:
        tools:
          profile: none
""")
        mockSkillLoader.loadConfigured([], null) >> []

        when:
        AnalysisReport report = scanner.analyze(project)

        then:
        report.builtinToolCount() == 0
        report.builtinToolsTokens() == 0
        report.toolProfile() == "none"
    }

    def "profile minimal includes only FileReadTool"() {
        given:
        Path project = createProject("""
jaiclaw:
  skills:
    allow-bundled: []
  agent:
    agents:
      default:
        tools:
          profile: minimal
""")
        mockSkillLoader.loadConfigured([], null) >> []

        when:
        AnalysisReport report = scanner.analyze(project)

        then:
        report.builtinToolCount() == 1
        report.builtinToolsTokens() > 0
        report.toolProfile() == "minimal"
    }

    def "profile coding includes 7 tools"() {
        given:
        Path project = createProject("""
jaiclaw:
  skills:
    allow-bundled: []
  agent:
    agents:
      default:
        tools:
          profile: coding
""")
        mockSkillLoader.loadConfigured([], null) >> []

        when:
        AnalysisReport report = scanner.analyze(project)

        then:
        // CODING profile: shell_exec, web_fetch, web_search, file_read,
        // file_write, ascii_box, ascii_render (AsciiBoxTool + AsciiRenderTool
        // added in the ascii-render rollout).
        report.builtinToolCount() == 7
        report.builtinToolsTokens() > 0
        report.toolProfile() == "coding"
    }

    def "parses identity name and description for system prompt estimation"() {
        given:
        Path project = createProject("""
jaiclaw:
  identity:
    name: Travel Planner
    description: a specialized AI travel planning assistant
  skills:
    allow-bundled: []
  agent:
    agents:
      default:
        tools:
          profile: full
""")
        mockSkillLoader.loadConfigured([], null) >> []

        when:
        AnalysisReport report = scanner.analyze(project)

        then:
        // Identity text ("You are Travel Planner, a specialized...") + date + overhead
        report.systemPromptTokens() > 60
    }
}

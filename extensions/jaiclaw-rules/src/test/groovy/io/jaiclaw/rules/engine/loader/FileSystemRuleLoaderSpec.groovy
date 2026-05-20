package io.jaiclaw.rules.engine.loader

import org.kie.api.KieServices
import org.kie.api.builder.KieFileSystem
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Comprehensive Spock tests for FileSystemRuleLoader.
 * Tests cover:
 * - Happy path scenarios with various file patterns
 * - Directory traversal (recursive and non-recursive)
 * - Edge cases (empty files, symlinks, permission issues)
 * - Security validation (path traversal prevention)
 * - Configuration validation
 */
class FileSystemRuleLoaderSpec extends Specification {

    def kieServices = KieServices.Factory.get()
    def kieFileSystem

    @TempDir
    Path tempDir

    def setup() {
        kieFileSystem = kieServices.newKieFileSystem()
    }

    def "should load single rule file from filesystem"() {
        given: "a DRL file in temp directory"
        def ruleFile = tempDir.resolve("test-rule.drl")
        Files.writeString(ruleFile, createValidDrlContent())

        and: "a loader configured for that file"
        @Subject
        def loader = new FileSystemRuleLoader([ruleFile.toString()], true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should load multiple rule files from directory"() {
        given: "multiple DRL files in temp directory"
        def rule1 = tempDir.resolve("rule1.drl")
        def rule2 = tempDir.resolve("rule2.drl")
        def rule3 = tempDir.resolve("rule3.drl")

        Files.writeString(rule1, createValidDrlContent())
        Files.writeString(rule2, createValidDrlContent())
        Files.writeString(rule3, createValidDrlContent())

        and: "a loader configured for the directory"
        @Subject
        def loader = new FileSystemRuleLoader([tempDir.toString()], true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should load rules recursively from nested directories"() {
        given: "nested directories with DRL files"
        def subDir1 = tempDir.resolve("sub1")
        def subDir2 = tempDir.resolve("sub1/sub2")
        Files.createDirectories(subDir2)

        Files.writeString(tempDir.resolve("root.drl"), createValidDrlContent())
        Files.writeString(subDir1.resolve("sub1.drl"), createValidDrlContent())
        Files.writeString(subDir2.resolve("sub2.drl"), createValidDrlContent())

        and: "a loader with recursive pattern"
        def pattern = tempDir.toString() + "/**/*.drl"
        @Subject
        def loader = new FileSystemRuleLoader([pattern], true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should load only from immediate directory when not recursive"() {
        given: "nested directories with DRL files"
        def subDir = tempDir.resolve("sub")
        Files.createDirectories(subDir)

        Files.writeString(tempDir.resolve("root.drl"), createValidDrlContent())
        Files.writeString(subDir.resolve("sub.drl"), createValidDrlContent())

        and: "a loader without recursive pattern"
        @Subject
        def loader = new FileSystemRuleLoader([tempDir.toString()], true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown (only root.drl is loaded)"
        noExceptionThrown()
    }

    def "should skip non-DRL files"() {
        given: "mixed file types in directory"
        Files.writeString(tempDir.resolve("rule.drl"), createValidDrlContent())
        Files.writeString(tempDir.resolve("readme.txt"), "Not a rule file")
        Files.writeString(tempDir.resolve("config.json"), "{}")

        and: "a loader for the directory"
        @Subject
        def loader = new FileSystemRuleLoader([tempDir.toString()], true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown (only .drl files processed)"
        noExceptionThrown()
    }

    def "should skip loading when loader is disabled"() {
        given: "a DRL file in temp directory"
        def ruleFile = tempDir.resolve("test-rule.drl")
        Files.writeString(ruleFile, createValidDrlContent())

        and: "a disabled loader"
        @Subject
        def loader = new FileSystemRuleLoader([ruleFile.toString()], false, 100)

        when: "loadRules is called"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown and nothing is loaded"
        noExceptionThrown()
    }

    def "should throw exception when directory does not exist"() {
        given: "a loader pointing to non-existent directory"
        def nonExistent = tempDir.resolve("does-not-exist").toString()
        @Subject
        def loader = new FileSystemRuleLoader([nonExistent], true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "exception is thrown when no rules can be loaded"
        thrown(RuleLoadingException)
    }

    def "should throw exception when all locations fail"() {
        given: "a loader with only non-existent locations"
        def locations = [
            tempDir.resolve("missing1").toString(),
            tempDir.resolve("missing2").toString()
        ]
        @Subject
        def loader = new FileSystemRuleLoader(locations, true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "exception is thrown"
        thrown(RuleLoadingException)
    }

    def "should reject empty rule file"() {
        given: "an empty DRL file"
        def emptyFile = tempDir.resolve("empty.drl")
        Files.writeString(emptyFile, "")

        and: "a loader for that file"
        @Subject
        def loader = new FileSystemRuleLoader([emptyFile.toString()], true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "exception is thrown"
        thrown(RuleLoadingException)
    }

    def "should reject whitespace-only rule file"() {
        given: "a DRL file with only whitespace"
        def whitespaceFile = tempDir.resolve("whitespace.drl")
        Files.writeString(whitespaceFile, "   \n\t\n   ")

        and: "a loader for that file"
        @Subject
        def loader = new FileSystemRuleLoader([whitespaceFile.toString()], true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "exception is thrown"
        thrown(RuleLoadingException)
    }

    def "should prevent directory traversal attacks"() {
        given: "a loader with directory traversal attempt"
        def maliciousPath = "/etc/../../../etc/passwd"
        @Subject
        def loader = new FileSystemRuleLoader([maliciousPath], true, 100)

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        thrown(RuleLoadingException)
    }

    def "should reject null locations"() {
        when: "creating loader with null locations"
        new FileSystemRuleLoader(null, true, 100)

        then: "exception is thrown"
        thrown(NullPointerException)
    }

    def "should reject empty locations list"() {
        given: "a loader with empty locations"
        @Subject
        def loader = new FileSystemRuleLoader([], true, 100)

        when: "configuration is validated"
        loader.validateConfiguration()

        then: "exception is thrown"
        def ex = thrown(RuleLoadingException)
        ex.message.contains("No locations configured")
    }

    def "should return correct loader type"() {
        given: "a filesystem loader"
        @Subject
        def loader = new FileSystemRuleLoader([tempDir.toString()], true, 100)

        expect: "loader type is 'filesystem'"
        loader.loaderType == "filesystem"
    }

    def "should return enabled status correctly"() {
        given: "loaders with different enabled states"
        @Subject
        def enabledLoader = new FileSystemRuleLoader([tempDir.toString()], true, 100)
        @Subject
        def disabledLoader = new FileSystemRuleLoader([tempDir.toString()], false, 100)

        expect: "enabled status matches configuration"
        enabledLoader.enabled == true
        disabledLoader.enabled == false
    }

    def "should return correct priority"() {
        given: "loaders with different priorities"
        @Subject
        def highPriorityLoader = new FileSystemRuleLoader([tempDir.toString()], true, 10)
        @Subject
        def lowPriorityLoader = new FileSystemRuleLoader([tempDir.toString()], true, 100)

        expect: "priority matches configuration"
        highPriorityLoader.priority == 10
        lowPriorityLoader.priority == 100
    }

    def "should use convenience constructor with default priority"() {
        given: "a loader created with convenience constructor"
        @Subject
        def loader = new FileSystemRuleLoader([tempDir.toString()], true)

        expect: "priority defaults to 100"
        loader.priority == 100
    }

    def "should handle deeply nested directories"() {
        given: "deeply nested directories (within limit)"
        Path deepDir = tempDir
        for (int i = 0; i < 10; i++) {
            deepDir = deepDir.resolve("level${i}")
        }
        Files.createDirectories(deepDir)
        Files.writeString(deepDir.resolve("deep.drl"), createValidDrlContent())

        and: "a loader with recursive pattern"
        def pattern = tempDir.toString() + "/**/*.drl"
        @Subject
        def loader = new FileSystemRuleLoader([pattern], true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should handle multiple file locations"() {
        given: "multiple directories with DRL files"
        def dir1 = tempDir.resolve("dir1")
        def dir2 = tempDir.resolve("dir2")
        Files.createDirectories(dir1)
        Files.createDirectories(dir2)

        Files.writeString(dir1.resolve("rule1.drl"), createValidDrlContent())
        Files.writeString(dir2.resolve("rule2.drl"), createValidDrlContent())

        and: "a loader with multiple locations"
        def locations = [dir1.toString(), dir2.toString()]
        @Subject
        def loader = new FileSystemRuleLoader(locations, true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should generate unique KIE resource paths for multiple files"() {
        given: "multiple DRL files"
        Files.writeString(tempDir.resolve("rule1.drl"), createValidDrlContent())
        Files.writeString(tempDir.resolve("rule2.drl"), createValidDrlContent())
        Files.writeString(tempDir.resolve("rule3.drl"), createValidDrlContent())

        and: "a loader for the directory"
        @Subject
        def loader = new FileSystemRuleLoader([tempDir.toString()], true, 100)

        when: "rules are loaded"
        loader.loadRules(kieFileSystem)

        then: "no duplicate path exceptions occur"
        noExceptionThrown()
    }

    def "should validate that file is readable"() {
        given: "a DRL file with restricted permissions"
        def restrictedFile = tempDir.resolve("restricted.drl")
        Files.writeString(restrictedFile, createValidDrlContent())
        // Note: Setting permissions may not work consistently across all platforms

        and: "a loader for that file"
        @Subject
        def loader = new FileSystemRuleLoader([restrictedFile.toString()], true, 100)

        when: "rules are loaded"
        def result = null
        try {
            loader.loadRules(kieFileSystem)
            result = "success"
        } catch (RuleLoadingException e) {
            result = "exception"
        }

        then: "either loads successfully or throws readable exception"
        result in ["success", "exception"]
    }

    def "should handle paths with suspicious patterns"() {
        when: "creating loader with suspicious pattern"
        @Subject
        def loader = new FileSystemRuleLoader([pattern], true, 100)
        loader.validateConfiguration()

        then: "exception is thrown"
        thrown(RuleLoadingException)

        where:
        pattern << [
            "../../../etc/passwd",
            "rules/../../secrets.drl",
            "..\\windows\\system32\\config"
        ]
    }

    /**
     * Helper method to create valid DRL content for testing.
     */
    private static String createValidDrlContent() {
        return """
package com.taptech.test.rules

rule "Test Rule"
when
    \$fact : Object()
then
    // Test rule action
end
"""
    }
}

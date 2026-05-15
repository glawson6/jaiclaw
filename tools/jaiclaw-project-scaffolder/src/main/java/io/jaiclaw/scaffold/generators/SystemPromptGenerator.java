package io.jaiclaw.scaffold.generators;

import io.jaiclaw.scaffold.ProjectManifest;

/**
 * Generates prompts/system-prompt.md when strategy=classpath.
 */
public final class SystemPromptGenerator {

    private SystemPromptGenerator() {}

    public static String generate(ProjectManifest manifest) {
        var sb = new StringBuilder();
        sb.append("# ").append(manifest.agent().name()).append(" System Prompt\n\n");
        sb.append("You are ").append(manifest.agent().name()).append(".\n\n");
        sb.append(manifest.description()).append("\n\n");
        sb.append("## Guidelines\n\n");
        sb.append("- Be helpful and concise\n");
        sb.append("- Use available tools when appropriate\n");
        sb.append("- If you don't know something, say so\n");
        return sb.toString();
    }
}

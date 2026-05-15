package io.jaiclaw.scaffold.generators;

import io.jaiclaw.scaffold.ProjectManifest;

/**
 * Generates README.md following CLAUDE.md required sections.
 */
public final class ReadmeGenerator {

    private ReadmeGenerator() {}

    public static String generate(ProjectManifest manifest) {
        var sb = new StringBuilder();
        String title = ProjectManifest.toPascalCase(manifest.name());

        sb.append("# ").append(title).append("\n\n");
        sb.append(manifest.description()).append("\n\n");

        // Problem
        sb.append("## Problem\n\n");
        if (manifest.readme().problem() != null && !manifest.readme().problem().isBlank()) {
            sb.append(manifest.readme().problem()).append("\n\n");
        } else {
            sb.append("<!-- TODO: Describe the real-world problem this application solves -->\n\n");
        }

        // Solution
        sb.append("## Solution\n\n");
        if (manifest.readme().solution() != null && !manifest.readme().solution().isBlank()) {
            sb.append(manifest.readme().solution()).append("\n\n");
        } else {
            sb.append("<!-- TODO: Describe how this application solves the problem using JaiClaw -->\n\n");
        }

        // Architecture
        sb.append("## Architecture\n\n");
        sb.append("```\n");
        sb.append("User Request → JaiClaw Gateway → ").append(manifest.agent().name());
        if (!manifest.channels().isEmpty()) {
            sb.append(" → [").append(String.join(", ", manifest.channels())).append("]");
        }
        sb.append("\n```\n\n");
        sb.append("- **Archetype**: ").append(manifest.archetype().name().toLowerCase()).append("\n");
        sb.append("- **AI Provider**: ").append(manifest.aiProvider().primary()).append("\n");
        if (!manifest.extensions().isEmpty()) {
            sb.append("- **Extensions**: ").append(String.join(", ", manifest.extensions())).append("\n");
        }
        sb.append("\n");

        // Design
        sb.append("## Design\n\n");
        sb.append("<!-- TODO: Document key design decisions and trade-offs -->\n\n");

        // Build & Run
        sb.append("## Build & Run\n\n");
        sb.append("### Prerequisites\n\n");
        sb.append("- Java 21+\n");
        sb.append("- Maven 3.9+\n");
        appendApiKeyPrereqs(sb, manifest);
        sb.append("\n");

        sb.append("### Build\n\n");
        sb.append("```bash\n");
        sb.append("mvn clean package -DskipTests\n");
        sb.append("```\n\n");

        sb.append("### Run\n\n");
        sb.append("```bash\n");
        appendRunCommand(sb, manifest);
        sb.append("```\n\n");

        sb.append("### Verify\n\n");
        sb.append("```bash\n");
        sb.append("curl http://localhost:").append(manifest.server().port()).append("/actuator/health\n");
        sb.append("```\n");

        return sb.toString();
    }

    private static void appendApiKeyPrereqs(StringBuilder sb, ProjectManifest manifest) {
        String provider = manifest.aiProvider().primary();
        switch (provider) {
            case "anthropic" -> sb.append("- `ANTHROPIC_API_KEY` environment variable\n");
            case "openai" -> sb.append("- `OPENAI_API_KEY` environment variable\n");
            case "gemini", "vertex-ai" -> sb.append("- Google Cloud project with Vertex AI enabled\n");
            case "ollama" -> sb.append("- Ollama running locally (no API key needed)\n");
            case "minimax" -> sb.append("- `MINIMAX_API_KEY` environment variable\n");
            case "bedrock" -> sb.append("- AWS credentials configured\n");
            case "azure-openai" -> sb.append("- `AZURE_OPENAI_API_KEY` and `AZURE_OPENAI_ENDPOINT` environment variables\n");
            case "deepseek" -> sb.append("- `DEEPSEEK_API_KEY` environment variable\n");
            case "mistral" -> sb.append("- `MISTRAL_API_KEY` environment variable\n");
            default -> sb.append("- API key for ").append(provider).append("\n");
        }
    }

    private static void appendRunCommand(StringBuilder sb, ProjectManifest manifest) {
        String provider = manifest.aiProvider().primary();
        switch (provider) {
            case "anthropic" -> sb.append("ANTHROPIC_API_KEY=your-key-here \\\n  ");
            case "openai" -> sb.append("OPENAI_API_KEY=your-key-here \\\n  ");
            case "minimax" -> sb.append("MINIMAX_API_KEY=your-key-here \\\n  ");
            case "deepseek" -> sb.append("DEEPSEEK_API_KEY=your-key-here \\\n  ");
            case "mistral" -> sb.append("MISTRAL_API_KEY=your-key-here \\\n  ");
            default -> {} // No env var needed or platform-specific auth
        }
        sb.append("java -jar target/").append(manifest.artifactId()).append("-").append(manifest.version()).append(".jar\n");
    }
}

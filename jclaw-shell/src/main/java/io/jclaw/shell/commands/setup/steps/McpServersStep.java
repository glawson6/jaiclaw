package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.OnboardResult.McpServerConfig;
import io.jclaw.shell.commands.setup.OnboardResult.McpTransportType;
import io.jclaw.shell.commands.setup.WizardStep;
import org.springframework.shell.component.flow.ComponentFlow;

import java.util.ArrayList;
import java.util.List;

/**
 * Wizard step for configuring MCP server connections.
 * In manual mode, the user can add multiple MCP servers with different
 * transport types (stdio, sse, http).
 */
public final class McpServersStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;

    public McpServersStep(ComponentFlow.Builder flowBuilder) {
        this.flowBuilder = flowBuilder;
    }

    @Override
    public String name() {
        return "MCP Servers";
    }

    @Override
    public boolean execute(OnboardResult result) {
        // Quickstart: no MCP servers by default
        if (!result.isManual()) {
            return true;
        }

        List<McpServerConfig> servers = new ArrayList<>();

        while (true) {
            // Ask if user wants to add an MCP server
            ComponentFlow confirmFlow = flowBuilder.clone().reset()
                    .withConfirmationInput("add-mcp")
                        .name("Add an MCP server connection?")
                        .defaultValue(false)
                        .and()
                    .build();

            ComponentFlow.ComponentFlowResult confirmResult = confirmFlow.run();
            Boolean addServer = WizardStep.getOrNull(confirmResult.getContext(), "add-mcp", Boolean.class);

            if (!Boolean.TRUE.equals(addServer)) {
                break;
            }

            // Collect server details
            ComponentFlow detailsFlow = flowBuilder.clone().reset()
                    .withStringInput("mcp-name")
                        .name("Server name (e.g., filesystem, github):")
                        .and()
                    .withStringInput("mcp-description")
                        .name("Description (optional):")
                        .defaultValue("")
                        .and()
                    .withSingleItemSelector("mcp-transport")
                        .name("Transport type:")
                        .selectItem("stdio", "stdio — subprocess with JSON-RPC over stdin/stdout")
                        .selectItem("sse", "sse — Server-Sent Events connection")
                        .selectItem("http", "http — Streamable HTTP transport")
                        .and()
                    .build();

            ComponentFlow.ComponentFlowResult detailsResult = detailsFlow.run();
            String name = WizardStep.getOrNull(detailsResult.getContext(), "mcp-name", String.class);
            String description = WizardStep.getOrNull(detailsResult.getContext(), "mcp-description", String.class);
            String transport = WizardStep.getOrNull(detailsResult.getContext(), "mcp-transport", String.class);

            if (name == null || name.isBlank()) {
                System.out.println("  Server name is required. Skipping.");
                continue;
            }

            if (description != null && description.isBlank()) {
                description = null;
            }

            McpTransportType transportType = switch (transport) {
                case "sse" -> McpTransportType.SSE;
                case "http" -> McpTransportType.HTTP;
                default -> McpTransportType.STDIO;
            };

            McpServerConfig config = collectTransportConfig(name, description, transportType);
            if (config != null) {
                servers.add(config);
                System.out.println("  Added MCP server: " + name + " (" + transportType + ")");
            }
        }

        result.setMcpServers(servers);
        return true;
    }

    private McpServerConfig collectTransportConfig(String name, String description, McpTransportType type) {
        return switch (type) {
            case STDIO -> collectStdioConfig(name, description);
            case SSE -> collectSseConfig(name, description);
            case HTTP -> collectHttpConfig(name, description);
        };
    }

    private McpServerConfig collectStdioConfig(String name, String description) {
        ComponentFlow flow = flowBuilder.clone().reset()
                .withStringInput("mcp-command")
                    .name("Command (e.g., npx @modelcontextprotocol/server-filesystem /tmp):")
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult result = flow.run();
        String commandLine = WizardStep.getOrNull(result.getContext(), "mcp-command", String.class);

        if (commandLine == null || commandLine.isBlank()) {
            System.out.println("  Command is required for stdio transport. Skipping.");
            return null;
        }

        // Split: first token is command, rest are args
        String[] parts = commandLine.trim().split("\\s+");
        String command = parts[0];
        List<String> args = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            args.add(parts[i]);
        }

        return new McpServerConfig(name, description, McpTransportType.STDIO,
                command, args, null, null);
    }

    private McpServerConfig collectSseConfig(String name, String description) {
        ComponentFlow flow = flowBuilder.clone().reset()
                .withStringInput("mcp-url")
                    .name("SSE endpoint URL:")
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult result = flow.run();
        String url = WizardStep.getOrNull(result.getContext(), "mcp-url", String.class);

        if (url == null || url.isBlank()) {
            System.out.println("  URL is required for SSE transport. Skipping.");
            return null;
        }

        return new McpServerConfig(name, description, McpTransportType.SSE,
                null, null, url, null);
    }

    private McpServerConfig collectHttpConfig(String name, String description) {
        ComponentFlow flow = flowBuilder.clone().reset()
                .withStringInput("mcp-url")
                    .name("HTTP endpoint URL:")
                    .and()
                .withStringInput("mcp-auth-token")
                    .name("Auth token (blank for none):")
                    .defaultValue("")
                    .maskCharacter('*')
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult result = flow.run();
        String url = WizardStep.getOrNull(result.getContext(), "mcp-url", String.class);
        String authToken = WizardStep.getOrNull(result.getContext(), "mcp-auth-token", String.class);

        if (url == null || url.isBlank()) {
            System.out.println("  URL is required for HTTP transport. Skipping.");
            return null;
        }

        if (authToken != null && authToken.isBlank()) {
            authToken = null;
        }

        return new McpServerConfig(name, description, McpTransportType.HTTP,
                null, null, url, authToken);
    }
}

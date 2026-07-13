package io.jaiclaw.email.mcp;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.email.config.EmailProperties;
import io.jaiclaw.email.model.EmailMessage;
import io.jaiclaw.email.model.EmailResult;
import io.jaiclaw.email.provider.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * MCP tool provider exposing email sending tools.
 * Server name: {@code email}, with a single tool for sending emails.
 */
public class EmailMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailMcpToolProvider.class);
    private static final String SERVER_NAME = "email";
    private static final String SERVER_DESCRIPTION = "Email sending — compose and send emails via configured provider";

    private final EmailSender emailSender;
    private final EmailProperties properties;
    private final ObjectMapper objectMapper;

    public EmailMcpToolProvider(EmailSender emailSender, EmailProperties properties) {
        this.emailSender = emailSender;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getServerName() { return SERVER_NAME; }

    @Override
    public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("send_email", "Send an email to one or more recipients", SEND_SCHEMA)
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        try {
            return switch (toolName) {
                case "send_email" -> handleSendEmail(args);
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.error("MCP tool execution failed: {}", toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private McpToolResult handleSendEmail(Map<String, Object> args) throws Exception {
        List<String> to = (List<String>) args.get("to");
        if (to == null || to.isEmpty()) {
            return McpToolResult.error("Missing required parameter: to");
        }

        String subject = requireString(args, "subject");
        String htmlBody = (String) args.get("htmlBody");
        String textBody = (String) args.get("textBody");

        if (htmlBody == null && textBody == null) {
            return McpToolResult.error("At least one of htmlBody or textBody must be provided");
        }

        List<String> cc = (List<String>) args.get("cc");
        List<String> bcc = (List<String>) args.get("bcc");
        String from = (String) args.get("from");
        String fromName = (String) args.get("fromName");
        String replyTo = (String) args.get("replyTo");

        EmailMessage message = EmailMessage.builder()
                .to(to)
                .cc(cc)
                .bcc(bcc)
                .from(from)
                .fromName(fromName)
                .replyTo(replyTo)
                .subject(subject)
                .htmlBody(htmlBody)
                .textBody(textBody)
                .build();

        EmailResult result = emailSender.send(message);

        return switch (result) {
            case EmailResult.Sent sent -> McpToolResult.success(objectMapper.writeValueAsString(Map.of(
                    "status", "sent",
                    "messageId", sent.messageId() != null ? sent.messageId() : "",
                    "recipientCount", sent.recipientCount(),
                    "provider", sent.provider()
            )));
            case EmailResult.Failed failed -> McpToolResult.error(objectMapper.writeValueAsString(Map.of(
                    "status", "failed",
                    "error", failed.error(),
                    "errorCode", failed.errorCode() != null ? failed.errorCode() : "",
                    "provider", failed.provider()
            )));
        };
    }

    private String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) throw new IllegalArgumentException("Missing required parameter: " + key);
        return value.toString();
    }

    private static final String SEND_SCHEMA = """
            {"type":"object","properties":{
            "to":{"type":"array","items":{"type":"string"},"description":"List of recipient email addresses"},
            "subject":{"type":"string","description":"Email subject line"},
            "htmlBody":{"type":"string","description":"HTML body content"},
            "textBody":{"type":"string","description":"Plain text body content"},
            "cc":{"type":"array","items":{"type":"string"},"description":"CC recipient email addresses"},
            "bcc":{"type":"array","items":{"type":"string"},"description":"BCC recipient email addresses"},
            "from":{"type":"string","description":"Sender email address (overrides default)"},
            "fromName":{"type":"string","description":"Sender display name (overrides default)"},
            "replyTo":{"type":"string","description":"Reply-to email address"}
            },"required":["to","subject"]}""";
}

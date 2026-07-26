package io.jaiclaw.email.tool;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.email.config.EmailProperties;
import io.jaiclaw.email.model.EmailMessage;
import io.jaiclaw.email.model.EmailResult;
import io.jaiclaw.email.provider.EmailSender;
import io.jaiclaw.tools.ToolCatalog;
import io.jaiclaw.tools.builtin.AbstractBuiltinTool;

import java.util.List;
import java.util.Map;

/**
 * LLM tool for composing and sending emails via the configured {@link EmailSender}.
 */
public class SendEmailTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EmailSender emailSender;
    private final EmailProperties properties;

    public SendEmailTool(EmailSender emailSender, EmailProperties properties) {
        super(new ToolDefinition("email_send",
                "Send an email to one or more recipients with HTML and/or plain text content",
                ToolCatalog.SECTION_EMAIL, INPUT_SCHEMA));
        this.emailSender = emailSender;
        this.properties = properties;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ToolResult doExecute(Map<String, Object> params, ToolContext context) throws Exception {
        List<String> to = (List<String>) params.get("to");
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException("Missing required parameter: to");
        }

        String subject = requireParam(params, "subject");
        String htmlBody = optionalParam(params, "htmlBody", null);
        String textBody = optionalParam(params, "textBody", null);

        if (htmlBody == null && textBody == null) {
            throw new IllegalArgumentException("At least one of htmlBody or textBody must be provided");
        }

        List<String> cc = params.containsKey("cc") ? (List<String>) params.get("cc") : null;
        List<String> bcc = params.containsKey("bcc") ? (List<String>) params.get("bcc") : null;
        String from = optionalParam(params, "from", null);
        String fromName = optionalParam(params, "fromName", null);
        String replyTo = optionalParam(params, "replyTo", null);

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
            case EmailResult.Sent sent -> new ToolResult.Success(
                    OBJECT_MAPPER.writeValueAsString(Map.of(
                            "status", "sent",
                            "messageId", sent.messageId() != null ? sent.messageId() : "",
                            "recipientCount", sent.recipientCount(),
                            "provider", sent.provider()
                    )));
            case EmailResult.Failed failed -> new ToolResult.Success(
                    OBJECT_MAPPER.writeValueAsString(Map.of(
                            "status", "failed",
                            "error", failed.error(),
                            "errorCode", failed.errorCode() != null ? failed.errorCode() : "",
                            "provider", failed.provider()
                    )));
        };
    }
}

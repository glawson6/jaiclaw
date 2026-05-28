package io.jaiclaw.email.tool;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.email.config.EmailProperties;
import io.jaiclaw.email.provider.EmailSender;
import io.jaiclaw.tools.ToolRegistry;

import java.util.List;

/**
 * Factory for creating and registering all email tools.
 */
public final class EmailTools {

    private EmailTools() {}

    public static List<ToolCallback> all(EmailSender emailSender, EmailProperties properties) {
        return List.of(
                new SendEmailTool(emailSender, properties)
        );
    }

    public static void registerAll(ToolRegistry registry, EmailSender emailSender, EmailProperties properties) {
        registry.registerAll(all(emailSender, properties));
    }
}

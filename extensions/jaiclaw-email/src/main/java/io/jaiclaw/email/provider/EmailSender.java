package io.jaiclaw.email.provider;

import io.jaiclaw.email.model.EmailMessage;
import io.jaiclaw.email.model.EmailResult;

/**
 * SPI for sending emails. Implementations provide the actual transport
 * (e.g., SMTP2GO REST API, AWS SES, SendGrid).
 */
public interface EmailSender {

    /**
     * Send an email message.
     *
     * @param message the email to send
     * @return result indicating success or failure
     */
    EmailResult send(EmailMessage message);

    /**
     * @return the name of this email provider (e.g., "smtp2go", "ses")
     */
    String getProviderName();
}

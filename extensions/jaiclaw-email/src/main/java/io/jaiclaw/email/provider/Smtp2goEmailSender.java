package io.jaiclaw.email.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jaiclaw.email.config.EmailProperties;
import io.jaiclaw.email.model.EmailMessage;
import io.jaiclaw.email.model.EmailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Email sender implementation using the SMTP2GO REST API.
 * Adapted from sentinel-operator's SMTP2GOEmailService with richer
 * result types and configurable from/fromName overrides.
 */
public class Smtp2goEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(Smtp2goEmailSender.class);
    private static final String PROVIDER_NAME = "smtp2go";

    private final RestClient restClient;
    private final EmailProperties properties;

    public Smtp2goEmailSender(EmailProperties properties) {
        this.properties = properties;
        EmailProperties.Smtp2goConfig smtp2go = properties.smtp2go();
        this.restClient = RestClient.builder()
                .baseUrl(smtp2go.apiUrl())
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Override
    public EmailResult send(EmailMessage message) {
        try {
            String sender = resolveFrom(message);
            String senderName = resolveFromName(message);

            Smtp2goRequest request = new Smtp2goRequest();
            request.apiKey = properties.smtp2go().apiKey();
            request.to = message.to();
            request.sender = sender;
            request.senderName = senderName;
            request.subject = message.subject();
            request.textBody = message.textBody();
            request.htmlBody = message.htmlBody();
            request.cc = message.cc().isEmpty() ? null : message.cc();
            request.bcc = message.bcc().isEmpty() ? null : message.bcc();

            log.info("Sending email to {} recipients, subject: '{}'",
                    message.to().size(), message.subject());

            Smtp2goResponse response = restClient.post()
                    .uri("/email/send")
                    .header("X-Smtp2go-Api-Key", properties.smtp2go().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Smtp2goResponse.class);

            if (response != null && response.data != null) {
                if (response.data.error == null) {
                    log.info("Email sent successfully. Request ID: {}, Email ID: {}",
                            response.requestId, response.data.emailId);
                    int recipientCount = response.data.succeeded != null ? response.data.succeeded : message.to().size();
                    return new EmailResult.Sent(response.data.emailId, recipientCount, PROVIDER_NAME);
                } else {
                    log.error("Failed to send email. Error: {}, Error Code: {}",
                            response.data.error, response.data.errorCode);
                    return new EmailResult.Failed(response.data.error, response.data.errorCode, PROVIDER_NAME);
                }
            }

            log.error("Failed to send email. No response received.");
            return new EmailResult.Failed("No response received from SMTP2GO", PROVIDER_NAME);

        } catch (Exception e) {
            log.error("Exception while sending email: {}", e.getMessage(), e);
            return new EmailResult.Failed(e.getMessage(), PROVIDER_NAME);
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    private String resolveFrom(EmailMessage message) {
        if (message.from() != null && !message.from().isBlank()) {
            return message.from();
        }
        return properties.defaultFrom();
    }

    private String resolveFromName(EmailMessage message) {
        if (message.fromName() != null && !message.fromName().isBlank()) {
            return message.fromName();
        }
        return properties.defaultFromName();
    }

    // ============ Request/Response DTOs ============

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class Smtp2goRequest {
        @JsonProperty("api_key") String apiKey;
        @JsonProperty("to") List<String> to;
        @JsonProperty("cc") List<String> cc;
        @JsonProperty("bcc") List<String> bcc;
        @JsonProperty("sender") String sender;
        @JsonProperty("sender_name") String senderName;
        @JsonProperty("subject") String subject;
        @JsonProperty("text_body") String textBody;
        @JsonProperty("html_body") String htmlBody;
    }

    private static class Smtp2goResponse {
        @JsonProperty("request_id") String requestId;
        @JsonProperty("data") Smtp2goResponseData data;
    }

    private static class Smtp2goResponseData {
        @JsonProperty("error") String error;
        @JsonProperty("error_code") String errorCode;
        @JsonProperty("email_id") String emailId;
        @JsonProperty("succeeded") Integer succeeded;
        @JsonProperty("failed") Integer failed;
    }
}

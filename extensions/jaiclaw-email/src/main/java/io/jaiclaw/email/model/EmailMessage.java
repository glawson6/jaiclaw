package io.jaiclaw.email.model;

import java.util.List;
import java.util.Map;

/**
 * Immutable email message with builder. Supports standard email fields
 * including multiple recipients, CC/BCC, reply-to, and both plain text
 * and HTML body content.
 */
public record EmailMessage(
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String from,
        String fromName,
        String replyTo,
        String subject,
        String textBody,
        String htmlBody,
        Map<String, Object> metadata
) {
    public EmailMessage {
        to = to != null ? List.copyOf(to) : List.of();
        cc = cc != null ? List.copyOf(cc) : List.of();
        bcc = bcc != null ? List.copyOf(bcc) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private List<String> to;
        private List<String> cc;
        private List<String> bcc;
        private String from;
        private String fromName;
        private String replyTo;
        private String subject;
        private String textBody;
        private String htmlBody;
        private Map<String, Object> metadata;

        public Builder to(List<String> to) { this.to = to; return this; }
        public Builder cc(List<String> cc) { this.cc = cc; return this; }
        public Builder bcc(List<String> bcc) { this.bcc = bcc; return this; }
        public Builder from(String from) { this.from = from; return this; }
        public Builder fromName(String fromName) { this.fromName = fromName; return this; }
        public Builder replyTo(String replyTo) { this.replyTo = replyTo; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder textBody(String textBody) { this.textBody = textBody; return this; }
        public Builder htmlBody(String htmlBody) { this.htmlBody = htmlBody; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public EmailMessage build() {
            return new EmailMessage(to, cc, bcc, from, fromName, replyTo, subject, textBody, htmlBody, metadata);
        }
    }
}

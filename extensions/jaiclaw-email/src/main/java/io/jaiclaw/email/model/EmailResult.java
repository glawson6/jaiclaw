package io.jaiclaw.email.model;

/**
 * Result of sending an email — either sent successfully or failed.
 */
public sealed interface EmailResult permits EmailResult.Sent, EmailResult.Failed {

    boolean isSuccess();

    record Sent(String messageId, int recipientCount, String provider) implements EmailResult {
        @Override
        public boolean isSuccess() { return true; }
    }

    record Failed(String error, String errorCode, String provider) implements EmailResult {
        public Failed(String error, String provider) {
            this(error, null, provider);
        }

        @Override
        public boolean isSuccess() { return false; }
    }
}

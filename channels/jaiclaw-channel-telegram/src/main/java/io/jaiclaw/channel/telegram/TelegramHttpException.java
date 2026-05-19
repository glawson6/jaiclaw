package io.jaiclaw.channel.telegram;

/**
 * Thrown by {@link TelegramHttpClient} implementations on HTTP errors
 * or connectivity failures when calling the Telegram Bot API.
 */
public class TelegramHttpException extends RuntimeException {

    private final int statusCode;

    public TelegramHttpException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public TelegramHttpException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public TelegramHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * HTTP status code, or {@code -1} if the failure was not HTTP-related
     * (e.g. timeout, connection refused).
     */
    public int statusCode() {
        return statusCode;
    }
}

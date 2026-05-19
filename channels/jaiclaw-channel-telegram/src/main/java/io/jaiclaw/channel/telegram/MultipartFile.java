package io.jaiclaw.channel.telegram;

/**
 * Carries binary file data with a filename for use in
 * {@link TelegramHttpClient#postMultipart} calls.
 */
public record MultipartFile(String filename, byte[] data) {
}

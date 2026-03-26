package io.jaiclaw.docstore.model;

import java.util.Set;

/**
 * Request to add a new entry to the DocStore.
 *
 * @param filename          original filename (null for URL entries)
 * @param mimeType          MIME type (null for URL entries)
 * @param fileSize          file size in bytes
 * @param content           file bytes for analysis (not stored long-term)
 * @param channelId         originating channel
 * @param channelFileRef    channel-specific file reference
 * @param channelMessageRef channel-specific message reference
 * @param userId            who uploaded
 * @param chatId            which chat
 * @param sourceUrl         for URL entries
 * @param entryType         FILE, URL, or FORWARDED
 * @param tags              initial tags
 * @param description       initial description
 */
public record AddRequest(
        String filename,
        String mimeType,
        long fileSize,
        byte[] content,
        String channelId,
        String channelFileRef,
        String channelMessageRef,
        String userId,
        String chatId,
        String sourceUrl,
        DocStoreEntry.EntryType entryType,
        Set<String> tags,
        String description
) {
    public AddRequest {
        if (tags == null) tags = Set.of();
    }
}

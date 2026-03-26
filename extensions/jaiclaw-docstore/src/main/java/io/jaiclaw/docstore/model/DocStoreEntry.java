package io.jaiclaw.docstore.model;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * An indexed document, URL, or forwarded file in the DocStore.
 *
 * @param id                unique identifier (UUID)
 * @param entryType         FILE, URL, or FORWARDED
 * @param filename          original filename (null for URL entries)
 * @param mimeType          MIME type (null for URL entries)
 * @param fileSize          file size in bytes (0 for URL entries)
 * @param sourceUrl         URL for URL entries; message link for files
 * @param channelId         originating channel ("telegram", "slack", etc.)
 * @param channelFileRef    channel-specific file reference (e.g. Telegram file_id)
 * @param channelMessageRef channel-specific message reference (e.g. chatId:messageId)
 * @param userId            who uploaded or forwarded
 * @param chatId            which chat the entry came from
 * @param indexedAt         when the entry was indexed
 * @param tags              user-assigned tags
 * @param description       user-provided description
 * @param category          category (user or auto-assigned)
 * @param analysis          analysis result (null until analyzed)
 */
public record DocStoreEntry(
        String id,
        EntryType entryType,
        String filename,
        String mimeType,
        long fileSize,
        String sourceUrl,
        String channelId,
        String channelFileRef,
        String channelMessageRef,
        String userId,
        String chatId,
        Instant indexedAt,
        Set<String> tags,
        String description,
        String category,
        AnalysisResult analysis
) {
    public enum EntryType { FILE, URL, FORWARDED }

    public DocStoreEntry {
        if (tags == null) tags = Set.of();
        if (indexedAt == null) indexedAt = Instant.now();
    }

    public DocStoreEntry withTags(Set<String> newTags) {
        return new DocStoreEntry(id, entryType, filename, mimeType, fileSize, sourceUrl,
                channelId, channelFileRef, channelMessageRef, userId, chatId,
                indexedAt, newTags, description, category, analysis);
    }

    public DocStoreEntry withDescription(String newDescription) {
        return new DocStoreEntry(id, entryType, filename, mimeType, fileSize, sourceUrl,
                channelId, channelFileRef, channelMessageRef, userId, chatId,
                indexedAt, tags, newDescription, category, analysis);
    }

    public DocStoreEntry withAnalysis(AnalysisResult newAnalysis) {
        return new DocStoreEntry(id, entryType, filename, mimeType, fileSize, sourceUrl,
                channelId, channelFileRef, channelMessageRef, userId, chatId,
                indexedAt, tags, description, category, newAnalysis);
    }

    public DocStoreEntry withCategory(String newCategory) {
        return new DocStoreEntry(id, entryType, filename, mimeType, fileSize, sourceUrl,
                channelId, channelFileRef, channelMessageRef, userId, chatId,
                indexedAt, tags, description, newCategory, analysis);
    }

    /**
     * Short display name: filename for files, URL for links, or "forwarded: filename".
     */
    public String displayName() {
        return switch (entryType) {
            case FILE -> filename != null ? filename : "unnamed-file";
            case URL -> sourceUrl != null ? sourceUrl : "unnamed-url";
            case FORWARDED -> "fwd: " + (filename != null ? filename : "unnamed");
        };
    }

    /**
     * Short ID suitable for display in chat messages (first 6 chars).
     */
    public String shortId() {
        return id != null && id.length() > 6 ? id.substring(0, 6) : id;
    }
}

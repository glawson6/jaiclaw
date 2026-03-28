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
        AnalysisResult analysis,
        String tenantId
) {
    public enum EntryType { FILE, URL, FORWARDED }

    public DocStoreEntry {
        if (tags == null) tags = Set.of();
        if (indexedAt == null) indexedAt = Instant.now();
    }

    public DocStoreEntry withTags(Set<String> newTags) {
        return new DocStoreEntry(id, entryType, filename, mimeType, fileSize, sourceUrl,
                channelId, channelFileRef, channelMessageRef, userId, chatId,
                indexedAt, newTags, description, category, analysis, tenantId);
    }

    public DocStoreEntry withDescription(String newDescription) {
        return new DocStoreEntry(id, entryType, filename, mimeType, fileSize, sourceUrl,
                channelId, channelFileRef, channelMessageRef, userId, chatId,
                indexedAt, tags, newDescription, category, analysis, tenantId);
    }

    public DocStoreEntry withAnalysis(AnalysisResult newAnalysis) {
        return new DocStoreEntry(id, entryType, filename, mimeType, fileSize, sourceUrl,
                channelId, channelFileRef, channelMessageRef, userId, chatId,
                indexedAt, tags, description, category, newAnalysis, tenantId);
    }

    public DocStoreEntry withCategory(String newCategory) {
        return new DocStoreEntry(id, entryType, filename, mimeType, fileSize, sourceUrl,
                channelId, channelFileRef, channelMessageRef, userId, chatId,
                indexedAt, tags, description, newCategory, analysis, tenantId);
    }

    public DocStoreEntry withTenantId(String newTenantId) {
        return new DocStoreEntry(id, entryType, filename, mimeType, fileSize, sourceUrl,
                channelId, channelFileRef, channelMessageRef, userId, chatId,
                indexedAt, tags, description, category, analysis, newTenantId);
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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private EntryType entryType;
        private String filename;
        private String mimeType;
        private long fileSize;
        private String sourceUrl;
        private String channelId;
        private String channelFileRef;
        private String channelMessageRef;
        private String userId;
        private String chatId;
        private Instant indexedAt;
        private Set<String> tags;
        private String description;
        private String category;
        private AnalysisResult analysis;
        private String tenantId;

        public Builder id(String id) { this.id = id; return this; }
        public Builder entryType(EntryType entryType) { this.entryType = entryType; return this; }
        public Builder filename(String filename) { this.filename = filename; return this; }
        public Builder mimeType(String mimeType) { this.mimeType = mimeType; return this; }
        public Builder fileSize(long fileSize) { this.fileSize = fileSize; return this; }
        public Builder sourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; return this; }
        public Builder channelId(String channelId) { this.channelId = channelId; return this; }
        public Builder channelFileRef(String channelFileRef) { this.channelFileRef = channelFileRef; return this; }
        public Builder channelMessageRef(String channelMessageRef) { this.channelMessageRef = channelMessageRef; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder chatId(String chatId) { this.chatId = chatId; return this; }
        public Builder indexedAt(Instant indexedAt) { this.indexedAt = indexedAt; return this; }
        public Builder tags(Set<String> tags) { this.tags = tags; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder category(String category) { this.category = category; return this; }
        public Builder analysis(AnalysisResult analysis) { this.analysis = analysis; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }

        public DocStoreEntry build() {
            return new DocStoreEntry(
                    id, entryType, filename, mimeType, fileSize, sourceUrl, channelId, channelFileRef, channelMessageRef, userId, chatId, indexedAt, tags, description, category, analysis, tenantId);
        }
    }
}

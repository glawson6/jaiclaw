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

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String filename;
        private String mimeType;
        private long fileSize;
        private byte[] content;
        private String channelId;
        private String channelFileRef;
        private String channelMessageRef;
        private String userId;
        private String chatId;
        private String sourceUrl;
        private DocStoreEntry.EntryType entryType;
        private Set<String> tags;
        private String description;

        public Builder filename(String filename) { this.filename = filename; return this; }
        public Builder mimeType(String mimeType) { this.mimeType = mimeType; return this; }
        public Builder fileSize(long fileSize) { this.fileSize = fileSize; return this; }
        public Builder content(byte[] content) { this.content = content; return this; }
        public Builder channelId(String channelId) { this.channelId = channelId; return this; }
        public Builder channelFileRef(String channelFileRef) { this.channelFileRef = channelFileRef; return this; }
        public Builder channelMessageRef(String channelMessageRef) { this.channelMessageRef = channelMessageRef; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder chatId(String chatId) { this.chatId = chatId; return this; }
        public Builder sourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; return this; }
        public Builder entryType(DocStoreEntry.EntryType entryType) { this.entryType = entryType; return this; }
        public Builder tags(Set<String> tags) { this.tags = tags; return this; }
        public Builder description(String description) { this.description = description; return this; }

        public AddRequest build() {
            return new AddRequest(
                    filename, mimeType, fileSize, content, channelId, channelFileRef, channelMessageRef, userId, chatId, sourceUrl, entryType, tags, description);
        }
    }
}

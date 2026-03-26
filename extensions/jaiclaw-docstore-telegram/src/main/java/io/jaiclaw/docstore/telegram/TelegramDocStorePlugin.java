package io.jaiclaw.docstore.telegram;

import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.core.hook.HookName;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import io.jaiclaw.docstore.DocStoreService;
import io.jaiclaw.docstore.model.AddRequest;
import io.jaiclaw.docstore.model.DocStoreEntry;
import io.jaiclaw.docstore.search.DocStoreSearchOptions;
import io.jaiclaw.docstore.tool.DocStoreToolProvider;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Telegram integration for DocStore. Handles:
 * <ul>
 *   <li>Auto-indexing of uploaded files, forwarded messages, and URLs</li>
 *   <li>Telegram commands: /ls, /list, /upload, /download, /search, /tag, /analyze, /delete</li>
 *   <li>Tool registration for agent-driven interactions</li>
 * </ul>
 */
public class TelegramDocStorePlugin implements JaiClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(TelegramDocStorePlugin.class);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");

    private final DocStoreService docStoreService;
    private final boolean autoIndex;
    private final boolean autoAnalyze;

    public TelegramDocStorePlugin(DocStoreService docStoreService, boolean autoIndex, boolean autoAnalyze) {
        this.docStoreService = docStoreService;
        this.autoIndex = autoIndex;
        this.autoAnalyze = autoAnalyze;
    }

    @Override
    public PluginDefinition definition() {
        return new PluginDefinition(
                "telegram-docstore-plugin",
                "Telegram DocStore Plugin",
                "Document storage, retrieval, and search via Telegram",
                "1.0.0",
                PluginKind.GENERAL
        );
    }

    @Override
    public void register(PluginApi api) {
        // Register all DocStore tools
        var toolProvider = new DocStoreToolProvider(docStoreService);
        toolProvider.tools().forEach(api::registerTool);

        // Auto-index incoming files and URLs
        if (autoIndex) {
            api.on(HookName.MESSAGE_RECEIVED, (event, ctx) -> {
                if (event instanceof ChannelMessage message && "telegram".equals(message.channelId())) {
                    handleIncomingMessage(message);
                }
                return null;
            });
        }
    }

    /**
     * Process an incoming Telegram message — checks for commands, files, and URLs.
     * Returns a response string if the message was handled, null otherwise.
     */
    public String handleCommand(String text, String userId, String chatId) {
        if (text == null || !text.startsWith("/")) return null;

        String[] parts = text.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        return switch (command) {
            case "/ls" -> handleLs(args, userId, chatId);
            case "/list" -> handleList(args, userId, chatId);
            case "/search" -> handleSearch(args, userId, chatId);
            case "/download" -> handleDownload(args);
            case "/tag" -> handleTag(args);
            case "/analyze" -> handleAnalyze(args);
            case "/delete" -> handleDelete(args);
            default -> null;
        };
    }

    // ---- Command handlers ----

    private String handleLs(String args, String userId, String chatId) {
        String scopeId = chatId != null ? chatId : userId;

        if (!args.isBlank()) {
            // Filter by tag or type
            if (args.startsWith("#")) {
                Set<String> tags = Set.of(args.replace("#", "").split("\\s+"));
                var entries = docStoreService.listByTags(tags, scopeId);
                return formatEntryList(entries, "Entries tagged " + args);
            }
            // Treat as MIME type filter
            String prefix = args.contains("/") ? args : mapTypeShorthand(args);
            var entries = docStoreService.listByType(prefix, scopeId, 10);
            return formatEntryList(entries, "Entries (%s)".formatted(args));
        }

        var entries = docStoreService.list(scopeId, 10, 0);
        return formatEntryList(entries, "Recent entries");
    }

    private String handleList(String args, String userId, String chatId) {
        String scopeId = chatId != null ? chatId : userId;
        int limit = 10;
        if (!args.isBlank()) {
            try { limit = Integer.parseInt(args.trim()); } catch (NumberFormatException ignored) {}
        }
        var entries = docStoreService.list(scopeId, limit, 0);
        return formatDetailedEntryList(entries);
    }

    private String handleSearch(String query, String userId, String chatId) {
        if (query.isBlank()) return "Usage: /search <query>";
        String scopeId = chatId != null ? chatId : userId;
        var options = new DocStoreSearchOptions(scopeId, 5, null, null, null, null);
        var results = docStoreService.search(query, options);
        if (results.isEmpty()) return "No results found for: " + query;

        var sb = new StringBuilder("Search results for \"%s\":\n\n".formatted(query));
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            sb.append("%d. %s (ID: %s)\n".formatted(i + 1, r.entry().displayName(), r.entry().shortId()));
            sb.append("   Score: %.0f%% | Tags: %s\n".formatted(r.score() * 100, formatTags(r.entry().tags())));
            if (r.matchSnippet() != null && !r.matchSnippet().isEmpty()) {
                sb.append("   %s\n".formatted(r.matchSnippet()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String handleDownload(String args) {
        if (args.isBlank()) return "Usage: /download <id>";
        String id = args.trim();
        var entry = docStoreService.get(id).orElse(null);
        if (entry == null) return "Entry not found: " + id;
        if (entry.channelFileRef() == null) return "No file reference for: " + id;
        // Actual re-download is handled by the Telegram adapter via the file_id
        return "DOWNLOAD:" + entry.channelFileRef();
    }

    private String handleTag(String args) {
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) return "Usage: /tag <id> #tag1 #tag2";
        String id = parts[0];
        Set<String> tags = Arrays.stream(parts[1].split("\\s+"))
                .map(t -> t.startsWith("#") ? t.substring(1) : t)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var updated = docStoreService.tag(id, tags);
        if (updated == null) return "Entry not found: " + id;
        return "Tagged %s: %s".formatted(updated.shortId(), formatTags(updated.tags()));
    }

    private String handleAnalyze(String args) {
        if (args.isBlank()) return "Usage: /analyze <id>";
        String id = args.trim();
        var entry = docStoreService.get(id).orElse(null);
        if (entry == null) return "Entry not found: " + id;
        if (entry.analysis() != null) {
            return "Analysis (%s):\nSummary: %s\nTopics: %s\nEntities: %s".formatted(
                    entry.analysis().level(),
                    entry.analysis().summary(),
                    String.join(", ", entry.analysis().topics()),
                    String.join(", ", entry.analysis().entities()));
        }
        return "ANALYZE:" + id;
    }

    private String handleDelete(String args) {
        if (args.isBlank()) return "Usage: /delete <id>";
        String id = args.trim();
        var entry = docStoreService.get(id).orElse(null);
        if (entry == null) return "Entry not found: " + id;
        docStoreService.delete(id);
        return "Deleted: %s (%s)".formatted(entry.displayName(), entry.shortId());
    }

    // ---- Auto-indexing ----

    void handleIncomingMessage(ChannelMessage message) {
        String userId = message.peerId();
        String chatId = message.platformData() != null
                ? String.valueOf(message.platformData().get("chat_id"))
                : userId;

        // Index attachments
        if (message.hasAttachments()) {
            for (var attachment : message.attachments()) {
                indexAttachment(attachment, message, userId, chatId, false);
            }
        }

        // Index URLs in message text
        if (message.content() != null) {
            Matcher matcher = URL_PATTERN.matcher(message.content());
            while (matcher.find()) {
                String url = matcher.group();
                var entry = docStoreService.addUrl(url, userId, chatId, "telegram");
                log.info("Auto-indexed URL: {} (id={})", url, entry.shortId());
            }
        }
    }

    /**
     * Index a Telegram file attachment.
     */
    public DocStoreEntry indexAttachment(ChannelMessage.Attachment attachment, ChannelMessage message,
                                         String userId, String chatId, boolean forwarded) {
        String messageId = message.platformData() != null
                ? String.valueOf(message.platformData().get("message_id"))
                : message.id();
        String channelMessageRef = chatId + ":" + messageId;

        // Extract file_id from platform data if available
        String fileRef = null;
        if (message.platformData() != null && message.platformData().containsKey("file_id")) {
            fileRef = String.valueOf(message.platformData().get("file_id"));
        }

        var request = new AddRequest(
                attachment.name(),
                attachment.mimeType(),
                attachment.data() != null ? attachment.data().length : 0,
                attachment.data(),
                "telegram",
                fileRef,
                channelMessageRef,
                userId,
                chatId,
                null,
                forwarded ? DocStoreEntry.EntryType.FORWARDED : DocStoreEntry.EntryType.FILE,
                Set.of(),
                null
        );

        var entry = docStoreService.add(request);
        log.info("Auto-indexed file: {} (id={}, type={})",
                attachment.name(), entry.shortId(), entry.entryType());

        // Auto-analyze if configured and content is available
        if (autoAnalyze && attachment.data() != null) {
            docStoreService.analyze(entry.id(), attachment.data());
        }

        return entry;
    }

    // ---- Formatting ----

    private String formatEntryList(List<DocStoreEntry> entries, String title) {
        if (entries.isEmpty()) return title + ": (empty)";
        var sb = new StringBuilder(title + ":\n\n");
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            sb.append("%d. %s (%s) — %s\n".formatted(
                    i + 1, e.displayName(), e.shortId(), formatTags(e.tags())));
        }
        return sb.toString();
    }

    private String formatDetailedEntryList(List<DocStoreEntry> entries) {
        if (entries.isEmpty()) return "DocStore is empty.";
        var sb = new StringBuilder("DocStore (%d entries):\n\n".formatted(entries.size()));
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            sb.append("%d. %s (ID: %s)\n".formatted(i + 1, e.displayName(), e.shortId()));
            sb.append("   Type: %s | Tags: %s\n".formatted(e.entryType(), formatTags(e.tags())));
            if (e.description() != null) sb.append("   %s\n".formatted(e.description()));
            if (e.analysis() != null) sb.append("   Analysis: %s\n".formatted(e.analysis().level()));
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String formatTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) return "(none)";
        return tags.stream().map(t -> "#" + t).collect(Collectors.joining(" "));
    }

    private static String mapTypeShorthand(String shorthand) {
        return switch (shorthand.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "image", "images", "photo", "photos" -> "image/";
            case "video", "videos" -> "video/";
            case "audio" -> "audio/";
            case "text" -> "text/";
            default -> shorthand + "/";
        };
    }
}

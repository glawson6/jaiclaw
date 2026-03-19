package io.jclaw.gateway.routing;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts @mention bot IDs from message text, specific to each channel's format.
 */
public class MentionParser {

    private static final Pattern SLACK_MENTION = Pattern.compile("<@([A-Z0-9]+)>");
    private static final Pattern DISCORD_MENTION = Pattern.compile("<@!?(\\d+)>");
    private static final Pattern TELEGRAM_COMMAND_MENTION = Pattern.compile("^/\\w+@(\\w+)");

    public Set<String> extractMentions(String channelId, String text) {
        Set<String> mentions = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) return mentions;

        return switch (channelId) {
            case "slack" -> extractPattern(text, SLACK_MENTION);
            case "discord" -> extractPattern(text, DISCORD_MENTION);
            case "telegram" -> extractTelegramMentions(text);
            default -> mentions;
        };
    }

    private Set<String> extractTelegramMentions(String text) {
        Set<String> mentions = new LinkedHashSet<>();
        Matcher commandMatcher = TELEGRAM_COMMAND_MENTION.matcher(text);
        if (commandMatcher.find()) {
            mentions.add(commandMatcher.group(1));
        }
        // Also check for @username mentions in text
        Pattern atMention = Pattern.compile("@(\\w+)");
        Matcher atMatcher = atMention.matcher(text);
        while (atMatcher.find()) {
            mentions.add(atMatcher.group(1));
        }
        return mentions;
    }

    private Set<String> extractPattern(String text, Pattern pattern) {
        Set<String> mentions = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions;
    }
}

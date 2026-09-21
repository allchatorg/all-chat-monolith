package com.mk3.chatapp.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chat-only interpretation of the inline emoji wire format; ads keep MessageMarkers semantics. */
public final class ChatMessageContent {
    // Consume URLs first so a reserved marker in a URL stays literal. Share
    // the frontend-compatible URL grammar used for chat formatting/lengths.
    private static final Pattern URL_OR_EMOJI = Pattern.compile(
            MessageMarkers.CHAT_URL_PATTERN.pattern() + "|:allchat:([a-zA-Z0-9_-]+):");
    private static final String EMOJI_PLACEHOLDER = "\uFFFC";

    private ChatMessageContent() {
    }

    /** Includes unknown IDs, allowing old unavailable emojis to be retained or removed. */
    public static Map<String, Integer> customEmojiCounts(String content) {
        Map<String, Integer> counts = new HashMap<>();
        if (content == null || content.isEmpty()) {
            return counts;
        }
        Matcher matcher = URL_OR_EMOJI.matcher(content);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (id != null) {
                counts.merge(id, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** Each inline emoji occupies one UTF-16 character in chat limits and content_plain. */
    public static String plainText(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = URL_OR_EMOJI.matcher(content);
        StringBuilder normalized = new StringBuilder(content.length());
        while (matcher.find()) {
            matcher.appendReplacement(normalized, Matcher.quoteReplacement(
                    matcher.group(1) == null ? matcher.group() : EMOJI_PLACEHOLDER));
        }
        matcher.appendTail(normalized);
        // Match markers before stripping formatting: fragments separated by
        // formatting boundaries must never combine into a new emoji identity.
        return MessageMarkers.stripChat(normalized.toString());
    }
}

package com.redis.redissearchdemo.search;

import java.util.Locale;

public final class SnippetBuilder {

    private SnippetBuilder() {
    }

    public static String build(String chunkText, String query) {
        if (chunkText == null || chunkText.isBlank()) {
            return "";
        }
        if (query == null) {
            return truncate(chunkText);
        }

        String lowerChunk = chunkText.toLowerCase(Locale.ROOT);
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        int matchIndex = lowerChunk.indexOf(lowerQuery);
        if (matchIndex < 0) {
            return truncate(chunkText);
        }

        int start = Math.max(0, matchIndex - 90);
        int end = Math.min(chunkText.length(), matchIndex + lowerQuery.length() + 170);
        String snippet = chunkText.substring(start, end);
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < chunkText.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }

    private static String truncate(String chunkText) {
        return chunkText.length() <= 260 ? chunkText : chunkText.substring(0, 260) + "...";
    }
}

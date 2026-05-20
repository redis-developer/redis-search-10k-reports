package com.redis.redissearchdemo.search;

import java.util.Locale;

public enum SearchMode {
    FULL_TEXT("full-text"),
    VECTOR("vector"),
    HYBRID("hybrid");

    private final String apiValue;

    SearchMode(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static SearchMode from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return HYBRID;
        }
        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "full-text" -> FULL_TEXT;
            case "vector" -> VECTOR;
            case "hybrid" -> HYBRID;
            case "compare" -> throw new IllegalArgumentException("Compare mode is not supported.");
            default -> throw new IllegalArgumentException("Unsupported search mode: " + rawValue);
        };
    }
}

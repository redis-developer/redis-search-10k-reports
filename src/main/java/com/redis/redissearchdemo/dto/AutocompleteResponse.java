package com.redis.redissearchdemo.dto;

import java.util.List;

public record AutocompleteResponse(
        String field,
        String q,
        List<AutocompleteSuggestion> suggestions
) {
}

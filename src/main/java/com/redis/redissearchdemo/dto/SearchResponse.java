package com.redis.redissearchdemo.dto;

import java.util.List;

public record SearchResponse(
        String query,
        String mode,
        List<SearchResult> results,
        int count,
        SearchDiagnostics diagnostics
) {
}

package com.redis.redissearchdemo.dto;

public record SearchDiagnostics(
        long latencyMs,
        int resultCount
) {
}

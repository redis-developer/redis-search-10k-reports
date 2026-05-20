package com.redis.redissearchdemo.dto;

public record DatasetInitializationResponse(
        boolean initialized,
        int companyCount,
        int chunkCount,
        long indexingDurationMs
) {
}

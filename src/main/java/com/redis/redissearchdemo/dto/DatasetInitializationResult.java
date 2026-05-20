package com.redis.redissearchdemo.dto;

public record DatasetInitializationResult(
        int companyCount,
        int chunkCount,
        long elapsedMillis
) {
}

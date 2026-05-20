package com.redis.redissearchdemo.dto;

public record DatasetStatusResponse(
        boolean initialized,
        int companyCount,
        int chunkCount
) {
}

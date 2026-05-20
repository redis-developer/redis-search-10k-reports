package com.redis.redissearchdemo.dto;

import com.redis.redissearchdemo.domain.FilingChunk;
import com.redis.redissearchdemo.search.SearchMode;
import com.redis.redissearchdemo.search.SnippetBuilder;

public record SearchResult(
        String id,
        String companyName,
        String ticker,
        String sector,
        String sectionName,
        int filingYear,
        String filingDate,
        String secUrl,
        String chunkText,
        String snippet,
        String mode
) {
    public static SearchResult fromFilingChunk(FilingChunk chunk, String query, SearchMode mode) {
        return new SearchResult(
                chunk.getId(),
                chunk.getCompanyName(),
                chunk.getTicker(),
                chunk.getSector(),
                chunk.getSectionName(),
                chunk.getFilingYear(),
                chunk.getFilingDate(),
                chunk.getSecUrl(),
                chunk.getChunkText(),
                SnippetBuilder.build(chunk.getChunkText(), query),
                mode.apiValue()
        );
    }
}

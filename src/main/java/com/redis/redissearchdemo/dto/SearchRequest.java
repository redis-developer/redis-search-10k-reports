package com.redis.redissearchdemo.dto;

import java.util.List;

public record SearchRequest(
        String mode,
        String query,
        String companyName,
        String ticker,
        List<String> sectors,
        List<String> sections,
        String filingYear,
        String filingDate,
        Integer limit
) {
    private static final String DEFAULT_MODE = "hybrid";
    private static final String DEFAULT_FILING_YEAR = "FY2025";
    private static final int DEFAULT_LIMIT = 20;

    public SearchRequest {
        mode = mode == null || mode.isBlank() ? DEFAULT_MODE : mode;
        sectors = sectors == null ? List.of() : sectors;
        sections = sections == null ? List.of() : sections;
        filingYear = filingYear == null || filingYear.isBlank() ? DEFAULT_FILING_YEAR : filingYear;
        limit = limit == null ? DEFAULT_LIMIT : limit;
    }

    public SearchRequest() {
        this(DEFAULT_MODE, null, null, null, List.of(), List.of(), DEFAULT_FILING_YEAR, null, DEFAULT_LIMIT);
    }
}

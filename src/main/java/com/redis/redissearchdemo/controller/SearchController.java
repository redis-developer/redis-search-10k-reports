package com.redis.redissearchdemo.controller;

import com.redis.redissearchdemo.service.FilingChunkService;
import com.redis.redissearchdemo.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class SearchController {

    private final SearchService searchService;
    private final FilingChunkService filingChunkService;

    public SearchController(SearchService searchService, FilingChunkService filingChunkService) {
        this.searchService = searchService;
        this.filingChunkService = filingChunkService;
    }

    @GetMapping("/filters")
    public Map<String, Object> filters() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("coverage", searchService.getCoverageSummary());
        payload.put("sectors", searchService.getAllSectors());
        payload.put("sections", searchService.getAllSections());
        return payload;
    }

    @GetMapping("/dataset/status")
    public Map<String, Object> datasetStatus() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("initialized", filingChunkService.isDataLoaded());
        payload.put("minCompanyCount", filingChunkService.minCompanyCount());
        payload.put("maxCompanyCount", filingChunkService.maxCompanyCount());
        payload.put("coverage", searchService.getCoverageSummary());
        return payload;
    }

    @PostMapping("/dataset/initialize")
    public Map<String, Object> initializeDataset(@RequestBody InitializeDatasetRequest request) throws Exception {
        InitializeDatasetRequest safeRequest = request == null ? new InitializeDatasetRequest() : request;
        int companyCount = safeRequest.companyCount() == null ? 100 : safeRequest.companyCount();
        FilingChunkService.DatasetInitializationResult initializationResult =
                filingChunkService.initializeDefaultDataset(companyCount);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("initialized", true);
        payload.put("companyCount", initializationResult.companyCount());
        payload.put("chunkCount", initializationResult.chunkCount());
        payload.put("indexingDurationMs", initializationResult.elapsedMillis());
        payload.put("coverage", searchService.getCoverageSummary());
        return payload;
    }

    @GetMapping("/autocomplete")
    public Map<String, Object> autocomplete(
            @RequestParam String field,
            @RequestParam("q") String query,
            @RequestParam(required = false, defaultValue = "8") Integer limit
    ) {
        List<Map<String, Object>> suggestions = switch (field) {
            case "companyName" -> searchService.autocompleteCompanyNames(query, limit);
            case "ticker" -> searchService.autocompleteTickers(query, limit);
            case "sectionName" -> searchService.autocompleteSections(query, limit);
            default -> List.of();
        };

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("field", field);
        payload.put("q", query);
        payload.put("suggestions", suggestions);
        return payload;
    }

    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody(required = false) SearchRequest request) {
        SearchRequest safeRequest = request == null ? new SearchRequest() : request;

        Integer filingYear = parseFilingYear(safeRequest.filingYear());
        return searchService.search(
                safeRequest.mode(),
                safeRequest.query(),
                safeRequest.companyName(),
                safeRequest.ticker(),
                safeRequest.sectors(),
                safeRequest.sections(),
                filingYear,
                safeRequest.filingDate(),
                safeRequest.limit()
        );
    }

    private Integer parseFilingYear(String filingYear) {
        if (filingYear == null || filingYear.isBlank()) {
            return null;
        }
        String digits = filingYear.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : Integer.parseInt(digits);
    }

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
        public SearchRequest() {
            this("hybrid", null, null, null, List.of(), List.of(), "FY2025", null, 12);
        }
    }

    public record InitializeDatasetRequest(Integer companyCount) {
        public InitializeDatasetRequest() {
            this(100);
        }
    }
}

package com.redis.redissearchdemo.controller;

import com.redis.redissearchdemo.dto.AutocompleteResponse;
import com.redis.redissearchdemo.dto.AutocompleteSuggestion;
import com.redis.redissearchdemo.dto.DatasetInitializationResult;
import com.redis.redissearchdemo.dto.DatasetInitializationResponse;
import com.redis.redissearchdemo.dto.DatasetStatusResponse;
import com.redis.redissearchdemo.dto.FiltersResponse;
import com.redis.redissearchdemo.dto.SearchRequest;
import com.redis.redissearchdemo.dto.SearchResponse;
import com.redis.redissearchdemo.service.FilingChunkService;
import com.redis.redissearchdemo.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SearchController {

    private final SearchService searchService;
    private final FilingChunkService filingChunkService;

    public SearchController(SearchService searchService, FilingChunkService filingChunkService) {
        this.searchService = searchService;
        this.filingChunkService = filingChunkService;
    }

    @GetMapping("/filters")
    public FiltersResponse filters() {
        return new FiltersResponse(
                searchService.getAllSectors(),
                searchService.getAllSections()
        );
    }

    @GetMapping("/dataset/status")
    public DatasetStatusResponse datasetStatus() {
        return new DatasetStatusResponse(
                filingChunkService.isDataLoaded(),
                filingChunkService.indexedCompanyCount(),
                filingChunkService.indexedChunkCount()
        );
    }

    @PostMapping("/dataset/initialize")
    public DatasetInitializationResponse initializeDataset() throws Exception {
        DatasetInitializationResult initializationResult = filingChunkService.initializeDefaultDataset();

        return new DatasetInitializationResponse(
                true,
                initializationResult.companyCount(),
                initializationResult.chunkCount(),
                initializationResult.elapsedMillis()
        );
    }

    @GetMapping("/autocomplete")
    public AutocompleteResponse autocomplete(
            @RequestParam String field,
            @RequestParam("q") String query
    ) {
        List<AutocompleteSuggestion> suggestions = switch (field) {
            case "companyName" -> searchService.autocompleteCompanyNames(query);
            case "ticker" -> searchService.autocompleteTickers(query);
            case "sectionName" -> searchService.autocompleteSections(query);
            default -> List.of();
        };

        return new AutocompleteResponse(field, query, suggestions);
    }

    @PostMapping("/search")
    public SearchResponse search(@RequestBody(required = false) SearchRequest request) {
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
}

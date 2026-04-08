package com.redis.redissearchdemo.controller;

import com.redis.redissearchdemo.service.FilingChunkService;
import com.redis.redissearchdemo.service.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private SearchService searchService;

    @Mock
    private FilingChunkService filingChunkService;

    @InjectMocks
    private SearchController controller;

    @Test
    void filtersEndpointReturnsCoverageAndMetadataSets() {
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("indexedCompanies", 2);
        coverage.put("targetCompanies", 500);
        when(searchService.getCoverageSummary()).thenReturn(coverage);
        when(searchService.getAllSectors()).thenReturn(linkedSet("Financials", "Information Technology"));
        when(searchService.getAllSections()).thenReturn(linkedSet("Business", "Risk Factors"));

        Map<String, Object> payload = controller.filters();

        assertThat(payload).containsEntry("coverage", coverage);
        assertThat((Set<String>) payload.get("sectors")).containsExactly("Financials", "Information Technology");
        assertThat((Set<String>) payload.get("sections")).containsExactly("Business", "Risk Factors");
    }

    @Test
    void autocompleteRoutesByFieldAndFallsBackToEmptyList() {
        when(searchService.autocompleteCompanyNames("Mic", 8)).thenReturn(List.of(Map.of("label", "Microsoft Corporation")));
        when(searchService.autocompleteTickers("MS", 5)).thenReturn(List.of(Map.of("label", "MSFT")));
        when(searchService.autocompleteSections("Risk", 3)).thenReturn(List.of(Map.of("label", "Risk Factors")));

        Map<String, Object> company = controller.autocomplete("companyName", "Mic", 8);
        Map<String, Object> ticker = controller.autocomplete("ticker", "MS", 5);
        Map<String, Object> section = controller.autocomplete("sectionName", "Risk", 3);
        Map<String, Object> invalid = controller.autocomplete("bogus", "Any", 4);

        assertThat(company.get("field")).isEqualTo("companyName");
        assertThat((List<?>) company.get("suggestions")).hasSize(1);
        assertThat((Map<String, Object>) ((List<?>) ticker.get("suggestions")).get(0)).containsEntry("label", "MSFT");
        assertThat((Map<String, Object>) ((List<?>) section.get("suggestions")).get(0)).containsEntry("label", "Risk Factors");
        assertThat((List<?>) invalid.get("suggestions")).isEmpty();
    }

    @Test
    void searchUsesHybridDefaultsWhenRequestIsNull() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mode", "hybrid");
        response.put("results", List.of(Map.of("companyName", "Apple Inc.")));
        response.put("count", 1);
        response.put("diagnostics", Map.of("latencyMs", 42, "resultCount", 1));
        when(searchService.search(eq("hybrid"), eq(null), eq(null), eq(null), eq(List.of()), eq(List.of()), eq(2025), eq(null), eq(12)))
                .thenReturn(response);

        Map<String, Object> payload = controller.search(null);

        assertThat(payload.get("mode")).isEqualTo("hybrid");
        assertThat(payload.get("count")).isEqualTo(1);
        assertThat((List<?>) payload.get("results")).hasSize(1);
        verify(searchService).search(eq("hybrid"), isNull(), isNull(), isNull(), eq(List.of()), eq(List.of()), eq(2025), isNull(), eq(12));
    }

    @Test
    void datasetStatusReflectsInitializationStateAndBounds() {
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("indexedCompanies", 0);
        coverage.put("targetCompanies", 500);
        when(filingChunkService.isDataLoaded()).thenReturn(false);
        when(filingChunkService.minCompanyCount()).thenReturn(10);
        when(filingChunkService.maxCompanyCount()).thenReturn(500);
        when(searchService.getCoverageSummary()).thenReturn(coverage);

        Map<String, Object> payload = controller.datasetStatus();

        assertThat(payload).containsEntry("initialized", false);
        assertThat(payload).containsEntry("minCompanyCount", 10);
        assertThat(payload).containsEntry("maxCompanyCount", 500);
        assertThat(payload).containsEntry("coverage", coverage);
    }

    @Test
    void initializeDatasetLoadsRequestedCompanyCount() throws Exception {
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("indexedCompanies", 50);
        coverage.put("targetCompanies", 500);
        when(filingChunkService.initializeDefaultDataset(50))
                .thenReturn(new FilingChunkService.DatasetInitializationResult(50, 1234, 9876));
        when(searchService.getCoverageSummary()).thenReturn(coverage);

        Map<String, Object> payload = controller.initializeDataset(new SearchController.InitializeDatasetRequest(50));

        assertThat(payload).containsEntry("initialized", true);
        assertThat(payload).containsEntry("companyCount", 50);
        assertThat(payload).containsEntry("chunkCount", 1234);
        assertThat(payload).containsEntry("indexingDurationMs", 9876L);
        assertThat(payload).containsEntry("coverage", coverage);
        verify(filingChunkService).initializeDefaultDataset(50);
    }

    private Set<String> linkedSet(String... values) {
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            set.add(value);
        }
        return set;
    }
}

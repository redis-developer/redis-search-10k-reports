package com.redis.redissearchdemo.controller;

import com.redis.redissearchdemo.dto.AutocompleteResponse;
import com.redis.redissearchdemo.dto.AutocompleteSuggestion;
import com.redis.redissearchdemo.dto.DatasetInitializationResult;
import com.redis.redissearchdemo.dto.DatasetInitializationResponse;
import com.redis.redissearchdemo.dto.DatasetStatusResponse;
import com.redis.redissearchdemo.dto.FiltersResponse;
import com.redis.redissearchdemo.dto.SearchDiagnostics;
import com.redis.redissearchdemo.dto.SearchResponse;
import com.redis.redissearchdemo.dto.SearchResult;
import com.redis.redissearchdemo.service.FilingChunkService;
import com.redis.redissearchdemo.service.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
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
        when(searchService.getAllSectors()).thenReturn(linkedSet("Financials", "Information Technology"));
        when(searchService.getAllSections()).thenReturn(linkedSet("Business", "Risk Factors"));

        FiltersResponse payload = controller.filters();

        assertThat(payload.sectors()).containsExactly("Financials", "Information Technology");
        assertThat(payload.sections()).containsExactly("Business", "Risk Factors");
    }

    @Test
    void autocompleteRoutesByFieldAndFallsBackToEmptyList() {
        when(searchService.autocompleteCompanyNames("Mic"))
                .thenReturn(List.of(new AutocompleteSuggestion("Microsoft Corporation", "Microsoft Corporation")));
        when(searchService.autocompleteTickers("MS"))
                .thenReturn(List.of(new AutocompleteSuggestion("MSFT", "MSFT")));
        when(searchService.autocompleteSections("Risk"))
                .thenReturn(List.of(new AutocompleteSuggestion("Risk Factors", "Risk Factors")));

        AutocompleteResponse company = controller.autocomplete("companyName", "Mic");
        AutocompleteResponse ticker = controller.autocomplete("ticker", "MS");
        AutocompleteResponse section = controller.autocomplete("sectionName", "Risk");
        AutocompleteResponse invalid = controller.autocomplete("bogus", "Any");

        assertThat(company.field()).isEqualTo("companyName");
        assertThat(company.suggestions()).hasSize(1);
        assertThat(ticker.suggestions().get(0).label()).isEqualTo("MSFT");
        assertThat(section.suggestions().get(0).label()).isEqualTo("Risk Factors");
        assertThat(invalid.suggestions()).isEmpty();
    }

    @Test
    void searchUsesHybridDefaultsWhenRequestIsNull() {
        SearchResponse response = new SearchResponse(
                null,
                "hybrid",
                List.of(new SearchResult(
                        "chunk-1",
                        "Apple Inc.",
                        "AAPL",
                        "Information Technology",
                        "Business",
                        2025,
                        "2025-01-31",
                        "https://www.sec.gov/",
                        "Apple filing chunk",
                        "Apple filing chunk",
                        "hybrid"
                )),
                1,
                new SearchDiagnostics(42, 1)
        );
        when(searchService.search(eq("hybrid"), eq(null), eq(null), eq(null), eq(List.of()), eq(List.of()), eq(2025), eq(null), eq(20)))
                .thenReturn(response);

        SearchResponse payload = controller.search(null);

        assertThat(payload.mode()).isEqualTo("hybrid");
        assertThat(payload.count()).isEqualTo(1);
        assertThat(payload.results()).hasSize(1);
        verify(searchService).search(eq("hybrid"), isNull(), isNull(), isNull(), eq(List.of()), eq(List.of()), eq(2025), isNull(), eq(20));
    }

    @Test
    void datasetStatusReflectsInitializationStateAndBounds() {
        when(filingChunkService.isDataLoaded()).thenReturn(false);
        when(filingChunkService.indexedCompanyCount()).thenReturn(0);
        when(filingChunkService.indexedChunkCount()).thenReturn(0);

        DatasetStatusResponse payload = controller.datasetStatus();

        assertThat(payload.initialized()).isFalse();
        assertThat(payload.companyCount()).isZero();
        assertThat(payload.chunkCount()).isZero();
    }

    @Test
    void initializeDatasetLoadsRequestedCompanyCount() throws Exception {
        when(filingChunkService.initializeDefaultDataset())
                .thenReturn(new DatasetInitializationResult(25, 1234, 9876));

        DatasetInitializationResponse payload = controller.initializeDataset();

        assertThat(payload.initialized()).isTrue();
        assertThat(payload.companyCount()).isEqualTo(25);
        assertThat(payload.chunkCount()).isEqualTo(1234);
        assertThat(payload.indexingDurationMs()).isEqualTo(9876L);
        verify(filingChunkService).initializeDefaultDataset();
    }

    private Set<String> linkedSet(String... values) {
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            set.add(value);
        }
        return set;
    }
}

package com.redis.redissearchdemo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.om.spring.search.stream.EntityStream;
import com.redis.om.spring.vectorize.Embedder;
import com.redis.redissearchdemo.domain.FilingChunk;
import com.redis.redissearchdemo.repository.FilingChunkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchServiceMetadataTest {

    private final FilingChunkRepository filingChunkRepository = mock(FilingChunkRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<Embedder> embedderProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final EntityStream entityStream = mock(EntityStream.class);
    private final SearchService service = new SearchService(
            filingChunkRepository,
            entityStream,
            embedderProvider,
            new DefaultResourceLoader(),
            new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void getAllSectorsAndSectionsReturnSortedDistinctValues() {
        when(filingChunkRepository.getAllSector()).thenReturn(List.of(
                "energy",
                "Financials",
                "Information Technology",
                "Financials"
        ));
        when(filingChunkRepository.getAllSectionName()).thenReturn(List.of(
                "Risk Factors",
                "Business",
                "Management's Discussion and Analysis",
                "Business"
        ));

        assertThat(service.getAllSectors()).containsExactly("energy", "Financials", "Information Technology");
        assertThat(service.getAllSections()).containsExactly(
                "Business",
                "Management's Discussion and Analysis",
                "Risk Factors"
        );
    }

    @Test
    void getCoverageSummaryReturnsPackagedCoverageTemplate() {
        when(filingChunkRepository.findAll()).thenReturn(List.of(
                chunkWithTicker("AAPL"),
                chunkWithTicker("MSFT"),
                chunkWithTicker("AAPL")
        ));
        when(filingChunkRepository.count()).thenReturn(24L);

        Map<String, Object> coverage = service.getCoverageSummary();

        assertThat(coverage.get("targetUniverse")).isEqualTo("S&P 500 FY2025-era constituents");
        assertThat(coverage.get("targetCompanies")).isEqualTo(500);
        assertThat(coverage.get("indexedCompanies")).isEqualTo(2);
        assertThat(coverage.get("indexedChunks")).isEqualTo(24);
        assertThat(coverage.get("indexedSections")).isEqualTo(4);
        assertThat(coverage.get("initialized")).isEqualTo(true);
        assertThat((List<?>) coverage.get("notes")).isNotEmpty();
    }

    @Test
    void compareModeIsRejected() {
        assertThatThrownBy(() -> service.search(
                "compare",
                "cloud",
                null,
                null,
                List.of(),
                List.of(),
                2025,
                null,
                12
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Compare mode is not supported");
    }

    private FilingChunk chunkWithTicker(String ticker) {
        FilingChunk chunk = new FilingChunk();
        chunk.setTicker(ticker);
        return chunk;
    }
}

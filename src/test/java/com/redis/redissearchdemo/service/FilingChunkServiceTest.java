package com.redis.redissearchdemo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.redissearchdemo.domain.FilingChunk;
import com.redis.redissearchdemo.repository.FilingChunkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilingChunkServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(MapperFeature.USE_GETTERS_AS_SETTERS);
    private final FilingChunkRepository filingChunkRepository = mock(FilingChunkRepository.class);
    private final FilingChunkService service = new FilingChunkService(
            objectMapper,
            new DefaultResourceLoader(),
            filingChunkRepository
    );

    @Test
    void loadAndSaveFilingChunks_readsPackagedSampleDatasetAndSavesAllChunks() throws Exception {
        service.loadAndSaveFilingChunks("datasets/sp500-fy2025-10k/sample-records.json");

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(filingChunkRepository, atLeastOnce()).saveAll(captor.capture());

        List<FilingChunk> savedChunks = new ArrayList<>();
        for (Iterable<?> batch : captor.getAllValues()) {
            batch.forEach(chunk -> savedChunks.add((FilingChunk) chunk));
        }

        assertThat(savedChunks).isNotEmpty();
        assertThat(savedChunks)
                .allSatisfy(chunk -> {
                    assertThat(chunk.getCompanyName()).isNotBlank();
                    assertThat(chunk.getTicker()).isNotBlank();
                    assertThat(chunk.getSectionName()).isNotBlank();
                    assertThat(chunk.getChunkText()).isNotBlank();
                    assertThat(chunk.getSecUrl()).startsWith("https://www.sec.gov/Archives/edgar/data/");
                    assertThat(chunk.getFilingYear()).isEqualTo(2025);
                });
    }

    @Test
    void isDataLoadedReflectsRepositoryCount() {
        when(filingChunkRepository.count()).thenReturn(2L);

        assertThat(service.isDataLoaded()).isTrue();
    }

    @Test
    void initializeDefaultDatasetLoadsOnlyRequestedCompanySubset() throws Exception {
        FilingChunkService.DatasetInitializationResult result = service.initializeDefaultDataset(10);

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Iterable.class);
        verify(filingChunkRepository, atLeastOnce()).saveAll(captor.capture());

        List<FilingChunk> savedChunks = new ArrayList<>();
        for (Iterable<?> batch : captor.getAllValues()) {
            batch.forEach(chunk -> savedChunks.add((FilingChunk) chunk));
        }

        List<String> tickerOrder = new ArrayList<>(savedChunks.stream()
                .map(FilingChunk::getTicker)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        assertThat(tickerOrder).containsExactly(
                "NVDA",
                "AAPL",
                "GOOGL",
                "MSFT",
                "AMZN",
                "AVGO",
                "META",
                "TSLA",
                "BRK.B",
                "WMT"
        );
        assertThat(result.companyCount()).isEqualTo(10);
        assertThat(result.chunkCount()).isEqualTo(savedChunks.size());
        assertThat(result.elapsedMillis()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void packagedSampleDatasetMatchesContractShape() throws Exception {
        try (InputStream inputStream = new DefaultResourceLoader()
                .getResource("classpath:datasets/sp500-fy2025-10k/sample-records.json")
                .getInputStream()) {
            ObjectMapper tolerantMapper = new ObjectMapper()
                    .findAndRegisterModules()
                    .disable(MapperFeature.USE_GETTERS_AS_SETTERS);
            List<FilingChunk> chunks = tolerantMapper.readValue(inputStream, new TypeReference<>() {});

            assertThat(chunks).isNotEmpty();
            assertThat(chunks).extracting(FilingChunk::getFilingYear)
                    .allMatch(year -> year == 2025);
            assertThat(chunks).extracting(FilingChunk::getSecUrl)
                    .allMatch(url -> url.startsWith("https://www.sec.gov/Archives/edgar/data/"));
            assertThat(chunks).extracting(FilingChunk::getSectionName)
                    .allMatch(section -> section != null && !section.isBlank());
        }
    }

    @Test
    void packagedConstituentSnapshotCarriesMarketCapRanks() throws Exception {
        try (InputStream inputStream = new DefaultResourceLoader()
                .getResource("classpath:datasets/sp500-fy2025-10k/constituents-snapshot.json")
                .getInputStream()) {
            List<java.util.Map<String, Object>> rows = objectMapper.readValue(inputStream, new TypeReference<>() {});

            assertThat(rows).hasSize(500);
            assertThat(rows.stream()
                    .map(row -> ((Number) row.get("marketCapRank")).intValue())
                    .sorted()
                    .toList()).containsExactly(java.util.stream.IntStream.rangeClosed(1, 500).boxed().toArray(Integer[]::new));
        }
    }
}

package com.redis.redissearchdemo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.redissearchdemo.domain.FilingChunk;
import com.redis.redissearchdemo.dto.DatasetInitializationResult;
import com.redis.redissearchdemo.repository.FilingChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FilingChunkService {

    private static final Logger log = LoggerFactory.getLogger(FilingChunkService.class);
    private static final String DEFAULT_DATASET_PATH = "datasets/sp500-fy2025-10k/sample-records.json";
    private static final String DEFAULT_CONSTITUENTS_SNAPSHOT_PATH = "datasets/sp500-fy2025-10k/constituents-snapshot.json";
    private static final int WORKSHOP_COMPANY_COUNT = 25;
    private static final int SAVE_BATCH_SIZE = 250;

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final FilingChunkRepository filingChunkRepository;
    private Integer cachedAvailableCompanyCount;

    public FilingChunkService(
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader,
            FilingChunkRepository filingChunkRepository
    ) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.filingChunkRepository = filingChunkRepository;
    }

    public void loadDefaultDataset() throws Exception {
        loadAndSaveFilingChunks(DEFAULT_DATASET_PATH);
    }

    public DatasetInitializationResult initializeDefaultDataset() throws Exception {
        int boundedCompanyCount = targetCompanyCount();
        List<FilingChunk> filingChunks = readDefaultDataset();
        List<FilingChunk> subset = selectFirstCompanies(filingChunks, boundedCompanyCount);
        int loadedCompanyCount = distinctTickerCount(subset);
        int loadedChunkCount = subset.size();

        filingChunkRepository.deleteAll();
        long startTime = System.currentTimeMillis();
        saveInBatches(subset);
        long elapsedMillis = System.currentTimeMillis() - startTime;
        log.info(
                "Saved {} filing chunks across {} companies from {} in {} ms",
                subset.size(),
                loadedCompanyCount,
                DEFAULT_DATASET_PATH,
                elapsedMillis
        );
        return new DatasetInitializationResult(loadedCompanyCount, loadedChunkCount, elapsedMillis);
    }

    public void loadAndSaveFilingChunks(String filePath) throws Exception {
        Resource resource = resourceLoader.getResource("classpath:" + filePath);
        try (InputStream inputStream = resource.getInputStream()) {
            List<FilingChunk> filingChunks = datasetObjectMapper().readValue(inputStream, new TypeReference<>() {});
            long startTime = System.currentTimeMillis();
            saveInBatches(filingChunks);
            long elapsedMillis = System.currentTimeMillis() - startTime;
            log.info("Saved {} filing chunks from {} in {} ms", filingChunks.size(), filePath, elapsedMillis);
        }
    }

    public void reloadDefaultDataset() throws Exception {
        filingChunkRepository.deleteAll();
        loadDefaultDataset();
    }

    public boolean isDataLoaded() {
        return filingChunkRepository.count() > 0;
    }

    public int targetCompanyCount() {
        return Math.min(WORKSHOP_COMPANY_COUNT, availableCompanyCount());
    }

    public int indexedCompanyCount() {
        Set<String> tickers = new LinkedHashSet<>();
        filingChunkRepository.findAll().forEach(chunk -> {
            String ticker = chunk.getTicker();
            if (ticker != null && !ticker.isBlank()) {
                tickers.add(ticker);
            }
        });
        return tickers.size();
    }

    public int indexedChunkCount() {
        return Math.toIntExact(filingChunkRepository.count());
    }

    private List<FilingChunk> selectFirstCompanies(List<FilingChunk> filingChunks, int companyCount) {
        List<String> selectedTickers = selectRankedTickers(filingChunks, companyCount);
        if (selectedTickers.isEmpty()) {
            return List.of();
        }

        Map<String, List<FilingChunk>> chunksByTicker = new LinkedHashMap<>();
        Set<String> selectedTickerSet = new LinkedHashSet<>(selectedTickers);
        for (FilingChunk chunk : filingChunks) {
            String ticker = chunk.getTicker();
            if (ticker != null && selectedTickerSet.contains(ticker)) {
                chunksByTicker.computeIfAbsent(ticker, ignored -> new ArrayList<>()).add(chunk);
            }
        }

        List<FilingChunk> subset = new ArrayList<>();
        for (String ticker : selectedTickers) {
            List<FilingChunk> companyChunks = chunksByTicker.get(ticker);
            if (companyChunks != null) {
                subset.addAll(companyChunks);
            }
        }
        return subset;
    }

    private List<String> selectRankedTickers(List<FilingChunk> filingChunks, int companyCount) {
        Set<String> availableTickers = new LinkedHashSet<>();
        for (FilingChunk chunk : filingChunks) {
            String ticker = chunk.getTicker();
            if (ticker != null && !ticker.isBlank()) {
                availableTickers.add(ticker);
            }
        }

        LinkedHashSet<String> selectedTickers = new LinkedHashSet<>();
        for (ConstituentSnapshot snapshot : readConstituentSnapshot()) {
            if (availableTickers.contains(snapshot.ticker())) {
                selectedTickers.add(snapshot.ticker());
                if (selectedTickers.size() == companyCount) {
                    return new ArrayList<>(selectedTickers);
                }
            }
        }

        for (String ticker : availableTickers) {
            selectedTickers.add(ticker);
            if (selectedTickers.size() == companyCount) {
                break;
            }
        }

        return new ArrayList<>(selectedTickers);
    }

    private synchronized int availableCompanyCount() {
        if (cachedAvailableCompanyCount != null) {
            return cachedAvailableCompanyCount;
        }

        try {
            cachedAvailableCompanyCount = distinctTickerCount(readDefaultDataset());
            return cachedAvailableCompanyCount;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to inspect the packaged dataset.", exception);
        }
    }

    private List<FilingChunk> readDefaultDataset() throws Exception {
        Resource resource = resourceLoader.getResource("classpath:" + DEFAULT_DATASET_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            return datasetObjectMapper().readValue(inputStream, new TypeReference<>() {});
        }
    }

    private List<ConstituentSnapshot> readConstituentSnapshot() {
        Resource resource = resourceLoader.getResource("classpath:" + DEFAULT_CONSTITUENTS_SNAPSHOT_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            List<Map<String, Object>> snapshotRows = datasetObjectMapper()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(inputStream, new TypeReference<>() {});

            List<ConstituentSnapshot> snapshot = new ArrayList<>();
            for (int index = 0; index < snapshotRows.size(); index++) {
                Map<String, Object> row = snapshotRows.get(index);
                Object tickerValue = row.get("ticker");
                if (!(tickerValue instanceof String ticker) || ticker.isBlank()) {
                    continue;
                }
                snapshot.add(new ConstituentSnapshot(ticker, integerValue(row.get("marketCapRank")), index));
            }

            snapshot.sort(
                    Comparator.comparingInt(
                                    (ConstituentSnapshot row) -> row.marketCapRank() == null ? Integer.MAX_VALUE : row.marketCapRank()
                            )
                            .thenComparingInt(ConstituentSnapshot::originalPosition)
            );
            return snapshot;
        } catch (Exception exception) {
            log.warn("Unable to load constituent ranking from {}. Falling back to dataset order.", DEFAULT_CONSTITUENTS_SNAPSHOT_PATH, exception);
            return List.of();
        }
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Integer.parseInt(string);
        }
        return null;
    }

    private int distinctTickerCount(List<FilingChunk> filingChunks) {
        return (int) filingChunks.stream()
                .map(FilingChunk::getTicker)
                .filter(ticker -> ticker != null && !ticker.isBlank())
                .distinct()
                .count();
    }

    private void saveInBatches(List<FilingChunk> filingChunks) {
        if (filingChunks.isEmpty()) {
            return;
        }

        for (int start = 0; start < filingChunks.size(); start += SAVE_BATCH_SIZE) {
            int end = Math.min(start + SAVE_BATCH_SIZE, filingChunks.size());
            filingChunkRepository.saveAll(new ArrayList<>(filingChunks.subList(start, end)));
        }
    }

    private ObjectMapper datasetObjectMapper() {
        return objectMapper.copy().disable(MapperFeature.USE_GETTERS_AS_SETTERS);
    }

    private record ConstituentSnapshot(
            String ticker,
            Integer marketCapRank,
            int originalPosition
    ) {
    }
}

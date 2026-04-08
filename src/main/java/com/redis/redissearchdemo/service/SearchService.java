package com.redis.redissearchdemo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.om.spring.autocomplete.Suggestion;
import com.redis.om.spring.repository.query.autocomplete.AutoCompleteOptions;
import com.redis.om.spring.search.stream.EntityStream;
import com.redis.om.spring.search.stream.SearchStream;
import com.redis.om.spring.search.stream.CombinationMethod;
import com.redis.om.spring.vectorize.Embedder;
import com.redis.redissearchdemo.domain.FilingChunk;
import com.redis.redissearchdemo.domain.FilingChunk$;
import com.redis.redissearchdemo.repository.FilingChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final String COVERAGE_PATH = "datasets/sp500-fy2025-10k/coverage-template.json";
    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    private final FilingChunkRepository filingChunkRepository;
    private final EntityStream entityStream;
    private final ObjectProvider<Embedder> embedderProvider;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public SearchService(
            FilingChunkRepository filingChunkRepository,
            EntityStream entityStream,
            ObjectProvider<Embedder> embedderProvider,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper
    ) {
        this.filingChunkRepository = filingChunkRepository;
        this.entityStream = entityStream;
        this.embedderProvider = embedderProvider;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> search(
            String mode,
            String query,
            String companyName,
            String ticker,
            List<String> sectors,
            List<String> sections,
            Integer filingYear,
            String filingDate,
            Integer limit
    ) {
        SearchMode searchMode = SearchMode.from(mode);
        return searchSingleMode(searchMode, query, companyName, ticker, sectors, sections, filingYear, filingDate, limit);
    }

    public Set<String> getAllSectors() {
        return toSortedSet(filingChunkRepository.getAllSector());
    }

    public Set<String> getAllSections() {
        return toSortedSet(filingChunkRepository.getAllSectionName());
    }

    public List<Map<String, Object>> autocompleteCompanyNames(String query, int limit) {
        return autocomplete(filingChunkRepository.autoCompleteCompanyName(query, AutoCompleteOptions.get()), limit);
    }

    public List<Map<String, Object>> autocompleteTickers(String query, int limit) {
        return autocomplete(filingChunkRepository.autoCompleteTicker(query, AutoCompleteOptions.get()), limit);
    }

    public List<Map<String, Object>> autocompleteSections(String query, int limit) {
        return autocomplete(filingChunkRepository.autoCompleteSectionName(query, AutoCompleteOptions.get()), limit);
    }

    public Map<String, Object> getCoverageSummary() {
        Map<String, Object> coverage = new LinkedHashMap<>(readCoverageTemplate());
        coverage.put("indexedCompanies", indexedCompanyCount());
        coverage.put("indexedChunks", indexedChunkCount());
        coverage.put("indexedSections", indexedSectionCount());
        coverage.put("initialized", filingChunkRepository.count() > 0);
        return coverage;
    }

    private Map<String, Object> searchSingleMode(
            SearchMode mode,
            String query,
            String companyName,
            String ticker,
            List<String> sectors,
            List<String> sections,
            Integer filingYear,
            String filingDate,
            Integer limit
    ) {
        long startTime = System.currentTimeMillis();

        String normalizedQuery = trimToNull(query);
        int resultLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);

        List<FilingChunk> chunks = switch (mode) {
            case FULL_TEXT -> runFullTextSearch(
                    normalizedQuery, companyName, ticker, sectors, sections, filingYear, filingDate, resultLimit
            );
            case VECTOR -> runVectorSearch(
                    normalizedQuery, companyName, ticker, sectors, sections, filingYear, filingDate, resultLimit
            );
            case HYBRID -> runHybridSearch(
                    normalizedQuery, companyName, ticker, sectors, sections, filingYear, filingDate, resultLimit
            );
        };

        List<Map<String, Object>> results = chunks.stream()
                .map(chunk -> toResultRow(chunk, normalizedQuery, mode))
                .toList();

        long latencyMs = System.currentTimeMillis() - startTime;
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("latencyMs", latencyMs);
        diagnostics.put("resultCount", results.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", normalizedQuery);
        response.put("mode", mode.apiValue);
        response.put("results", results);
        response.put("count", results.size());
        response.put("diagnostics", diagnostics);
        return response;
    }

    private List<FilingChunk> runFullTextSearch(
            String query,
            String companyName,
            String ticker,
            List<String> sectors,
            List<String> sections,
            Integer filingYear,
            String filingDate,
            int limit
    ) {
        SearchStream<FilingChunk> stream = applyFilters(
                entityStream.of(FilingChunk.class), companyName, ticker, sectors, sections, filingYear, filingDate
        );

        if (query == null) {
            return stream.limit(limit).collect(Collectors.toList());
        }

        return stream.filter(query).limit(limit).collect(Collectors.toList());
    }

    private List<FilingChunk> runVectorSearch(
            String query,
            String companyName,
            String ticker,
            List<String> sectors,
            List<String> sections,
            Integer filingYear,
            String filingDate,
            int limit
    ) {
        if (query == null) {
            throw new IllegalArgumentException("Vector search requires a free-text query.");
        }
        SearchStream<FilingChunk> stream = applyFilters(
                entityStream.of(FilingChunk.class), companyName, ticker, sectors, sections, filingYear, filingDate
        );

        float[] queryEmbedding = embedQuery(query);
        return stream
                .filter(FilingChunk$.CHUNK_EMBEDDING.knn(Math.max(limit, 20), queryEmbedding))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<FilingChunk> runHybridSearch(
            String query,
            String companyName,
            String ticker,
            List<String> sectors,
            List<String> sections,
            Integer filingYear,
            String filingDate,
            int limit
    ) {
        if (query == null) {
            throw new IllegalArgumentException("Hybrid search requires a free-text query.");
        }
        SearchStream<FilingChunk> stream = applyFilters(
                entityStream.of(FilingChunk.class), companyName, ticker, sectors, sections, filingYear, filingDate
        );

        float[] queryEmbedding = embedQuery(query);
        try {
            return stream
                    .hybridSearch(
                            query,
                            FilingChunk$.CHUNK_TEXT,
                            queryEmbedding,
                            FilingChunk$.CHUNK_EMBEDDING,
                            CombinationMethod.RRF,
                            0.65f
                    )
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (RuntimeException exception) {
            logger.warn("Redis OM Spring hybridSearch failed", exception);
            throw new IllegalStateException("Hybrid search is unavailable in the current Redis OM Spring runtime.", exception);
        }
    }

    private SearchStream<FilingChunk> applyFilters(
            SearchStream<FilingChunk> stream,
            String companyName,
            String ticker,
            List<String> sectors,
            List<String> sections,
            Integer filingYear,
            String filingDate
    ) {
        SearchStream<FilingChunk> filtered = stream;

        String normalizedCompanyName = trimToNull(companyName);
        if (normalizedCompanyName != null) {
            filtered = filtered.filter(FilingChunk$.COMPANY_NAME.eq(normalizedCompanyName));
        }

        String normalizedTicker = trimToNull(ticker);
        if (normalizedTicker != null) {
            filtered = filtered.filter(FilingChunk$.TICKER.eq(normalizedTicker.toUpperCase(Locale.ROOT)));
        }

        List<String> normalizedSectors = normalizeList(sectors);
        if (!normalizedSectors.isEmpty()) {
            filtered = filtered.filter(FilingChunk$.SECTOR.in(normalizedSectors.toArray(String[]::new)));
        }

        List<String> normalizedSections = normalizeList(sections);
        if (!normalizedSections.isEmpty()) {
            filtered = filtered.filter(FilingChunk$.SECTION_NAME.in(normalizedSections.toArray(String[]::new)));
        }

        if (filingYear != null) {
            filtered = filtered.filter(FilingChunk$.FILING_YEAR.eq(filingYear));
        }

        String normalizedFilingDate = trimToNull(filingDate);
        if (normalizedFilingDate != null) {
            filtered = filtered.filter(FilingChunk$.FILING_DATE.eq(normalizedFilingDate));
        }

        return filtered;
    }

    private float[] embedQuery(String query) {
        Embedder embedder = embedderProvider.getIfAvailable();
        if (embedder == null || !embedder.isReady()) {
            throw new IllegalStateException("Redis OM Spring embedder is not ready in the current runtime.");
        }

        try {
            Field field = FilingChunk.class.getDeclaredField("chunkText");
            List<float[]> embeddings = embedder.getTextEmbeddingsAsFloats(List.of(query), field);
            if (embeddings.isEmpty()) {
                throw new IllegalStateException("Embedding provider returned no query embedding.");
            }
            return embeddings.getFirst();
        } catch (Exception exception) {
            logger.warn("Unable to create query embedding", exception);
            throw new IllegalStateException("Query embedding generation failed.", exception);
        }
    }

    private List<Map<String, Object>> autocomplete(List<Suggestion> suggestions, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 20));
        return suggestions.stream()
                .limit(boundedLimit)
                .map(suggestion -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", suggestion.getValue());
                    row.put("value", suggestion.getValue());
                    return row;
                })
                .toList();
    }

    private Map<String, Object> readCoverageTemplate() {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + COVERAGE_PATH);
            try (var inputStream = resource.getInputStream()) {
                return objectMapper.readValue(inputStream, new TypeReference<>() {});
            }
        } catch (Exception exception) {
            logger.warn("Unable to read coverage template", exception);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("targetUniverse", "S&P 500 FY2025-era constituents");
            fallback.put("targetCompanies", 500);
            fallback.put("indexedCompanies", indexedCompanyCount());
            fallback.put("skippedCompanies", 0);
            fallback.put("indexedSections", indexedSectionCount());
            fallback.put("indexedChunks", indexedChunkCount());
            fallback.put("estimatedCompressedBytes", 0);
            fallback.put("estimatedUncompressedBytes", 0);
            fallback.put("generatedAt", null);
            fallback.put("notes", List.of("Coverage file unavailable; reporting live repository counts only."));
            return fallback;
        }
    }

    private Map<String, Object> toResultRow(FilingChunk chunk, String query, SearchMode mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", chunk.getId());
        result.put("companyName", chunk.getCompanyName());
        result.put("ticker", chunk.getTicker());
        result.put("sector", chunk.getSector());
        result.put("sectionName", chunk.getSectionName());
        result.put("filingYear", chunk.getFilingYear());
        result.put("filingDate", chunk.getFilingDate());
        result.put("secUrl", chunk.getSecUrl());
        result.put("chunkText", chunk.getChunkText());
        result.put("snippet", buildSnippet(chunk.getChunkText(), query));
        result.put("mode", mode.apiValue);
        return result;
    }

    private int indexedCompanyCount() {
        Set<String> tickers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        filingChunkRepository.findAll().forEach(chunk -> {
            String ticker = trimToNull(chunk.getTicker());
            if (ticker != null) {
                tickers.add(ticker);
            }
        });
        return tickers.size();
    }

    private int indexedChunkCount() {
        return Math.toIntExact(filingChunkRepository.count());
    }

    private int indexedSectionCount() {
        if (indexedChunkCount() == 0) {
            return 0;
        }
        Map<String, Object> coverage = readCoverageTemplate();
        Object indexedSections = coverage.get("indexedSections");
        if (indexedSections instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private Set<String> toSortedSet(Iterable<String> values) {
        Set<String> normalized = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (values == null) {
            return normalized;
        }
        values.forEach(value -> {
            String cleaned = trimToNull(value);
            if (cleaned != null) {
                normalized.add(cleaned);
            }
        });
        return normalized;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildSnippet(String chunkText, String query) {
        if (chunkText == null || chunkText.isBlank()) {
            return "";
        }
        if (query == null) {
            return chunkText.length() <= 260 ? chunkText : chunkText.substring(0, 260) + "...";
        }

        String lowerChunk = chunkText.toLowerCase(Locale.ROOT);
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        int matchIndex = lowerChunk.indexOf(lowerQuery);
        if (matchIndex < 0) {
            return chunkText.length() <= 260 ? chunkText : chunkText.substring(0, 260) + "...";
        }

        int start = Math.max(0, matchIndex - 90);
        int end = Math.min(chunkText.length(), matchIndex + lowerQuery.length() + 170);
        String snippet = chunkText.substring(start, end);
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < chunkText.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }

    private enum SearchMode {
        FULL_TEXT("full-text"),
        VECTOR("vector"),
        HYBRID("hybrid");

        private final String apiValue;

        SearchMode(String apiValue) {
            this.apiValue = apiValue;
        }

        static SearchMode from(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return HYBRID;
            }
            return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
                case "full-text" -> FULL_TEXT;
                case "vector" -> VECTOR;
                case "hybrid" -> HYBRID;
                case "compare" -> throw new IllegalArgumentException("Compare mode is not supported.");
                default -> throw new IllegalArgumentException("Unsupported search mode: " + rawValue);
            };
        }
    }
}

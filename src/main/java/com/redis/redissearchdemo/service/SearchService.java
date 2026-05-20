package com.redis.redissearchdemo.service;

import com.redis.om.spring.autocomplete.Suggestion;
import com.redis.om.spring.repository.query.autocomplete.AutoCompleteOptions;
import com.redis.om.spring.search.stream.EntityStream;
import com.redis.om.spring.search.stream.CombinationMethod;
import com.redis.om.spring.vectorize.Embedder;
import com.redis.redissearchdemo.domain.FilingChunk;
import com.redis.redissearchdemo.domain.FilingChunk$;
import com.redis.redissearchdemo.dto.AutocompleteSuggestion;
import com.redis.redissearchdemo.dto.SearchDiagnostics;
import com.redis.redissearchdemo.dto.SearchResponse;
import com.redis.redissearchdemo.dto.SearchResult;
import com.redis.redissearchdemo.repository.FilingChunkRepository;
import com.redis.redissearchdemo.search.SearchMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final FilingChunkRepository filingChunkRepository;
    private final EntityStream entityStream;
    private final ObjectProvider<Embedder> embedderProvider;

    public SearchService(
            FilingChunkRepository filingChunkRepository,
            EntityStream entityStream,
            ObjectProvider<Embedder> embedderProvider
    ) {
        this.filingChunkRepository = filingChunkRepository;
        this.entityStream = entityStream;
        this.embedderProvider = embedderProvider;
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
        List<FilingChunk> results = entityStream.of(FilingChunk.class)
                .filter(query)
                .filter(FilingChunk$.COMPANY_NAME.eq(companyName))
                .filter(FilingChunk$.TICKER.eq(ticker))
                .filter(FilingChunk$.SECTOR.in(sectors))
                .filter(FilingChunk$.SECTION_NAME.in(sections))
                .filter(FilingChunk$.FILING_YEAR.eq(filingYear))
                .filter(FilingChunk$.FILING_DATE.eq(filingDate))
                .limit(limit)
                .collect(Collectors.toList());

        return results;
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
        Embedder embedder = embedderProvider.getIfAvailable();
        float[] queryEmbedding = embedder.getTextEmbeddingsAsFloats(List.of(query), FilingChunk$.CHUNK_TEXT).getFirst();

        List<FilingChunk> results = entityStream.of(FilingChunk.class)
                .filter(FilingChunk$.CHUNK_EMBEDDING.knn(limit, queryEmbedding))
                .filter(FilingChunk$.COMPANY_NAME.eq(companyName))
                .filter(FilingChunk$.TICKER.eq(ticker))
                .filter(FilingChunk$.SECTOR.in(sectors))
                .filter(FilingChunk$.SECTION_NAME.in(sections))
                .filter(FilingChunk$.FILING_YEAR.eq(filingYear))
                .filter(FilingChunk$.FILING_DATE.eq(filingDate))
                .limit(limit)
                .collect(Collectors.toList());

        return results;
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
        Embedder embedder = embedderProvider.getIfAvailable();
        float[] queryEmbedding = embedder.getTextEmbeddingsAsFloats(List.of(query), FilingChunk$.CHUNK_TEXT).getFirst();

        List<FilingChunk> results = entityStream.of(FilingChunk.class)
                .hybridSearch(
                        query,
                        FilingChunk$.CHUNK_TEXT,
                        queryEmbedding,
                        FilingChunk$.CHUNK_EMBEDDING,
                        CombinationMethod.RRF,
                        0.65f
                )
                .filter(FilingChunk$.COMPANY_NAME.eq(companyName))
                .filter(FilingChunk$.TICKER.eq(ticker))
                .filter(FilingChunk$.SECTOR.in(sectors))
                .filter(FilingChunk$.SECTION_NAME.in(sections))
                .filter(FilingChunk$.FILING_YEAR.eq(filingYear))
                .filter(FilingChunk$.FILING_DATE.eq(filingDate))
                .limit(limit)
                .collect(Collectors.toList());

        return results;
    }

    public SearchResponse search(
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
        long startTime = System.currentTimeMillis();
        int resultLimit = normalizeLimit(limit);

        List<FilingChunk> chunks = switch (searchMode) {
            case FULL_TEXT -> runFullTextSearch(
                    query, companyName, ticker, sectors, sections, filingYear, filingDate, resultLimit
            );
            case VECTOR -> runVectorSearch(
                    query, companyName, ticker, sectors, sections, filingYear, filingDate, resultLimit
            );
            case HYBRID -> runHybridSearch(
                    query, companyName, ticker, sectors, sections, filingYear, filingDate, resultLimit
            );
        };

        List<SearchResult> results = chunks.stream()
                .map(chunk -> SearchResult.fromFilingChunk(chunk, query, searchMode))
                .toList();

        long latencyMs = System.currentTimeMillis() - startTime;
        SearchDiagnostics diagnostics = new SearchDiagnostics(latencyMs, results.size());

        return new SearchResponse(query, searchMode.apiValue(), results, results.size(), diagnostics);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private List<AutocompleteSuggestion> autocomplete(List<Suggestion> suggestions) {
        return suggestions.stream()
                .map(suggestion -> new AutocompleteSuggestion(suggestion.getValue(), suggestion.getValue()))
                .toList();
    }

    private Set<String> toSortedSet(Iterable<String> values) {
        Set<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        values.forEach(sorted::add);
        return sorted;
    }

    public Set<String> getAllSectors() {
        return toSortedSet(filingChunkRepository.getAllSector());
    }

    public Set<String> getAllSections() {
        return toSortedSet(filingChunkRepository.getAllSectionName());
    }

    public List<AutocompleteSuggestion> autocompleteCompanyNames(String query) {
        return autocomplete(filingChunkRepository.autoCompleteCompanyName(query, AutoCompleteOptions.get()));
    }

    public List<AutocompleteSuggestion> autocompleteTickers(String query) {
        return autocomplete(filingChunkRepository.autoCompleteTicker(query, AutoCompleteOptions.get()));
    }

    public List<AutocompleteSuggestion> autocompleteSections(String query) {
        return autocomplete(filingChunkRepository.autoCompleteSectionName(query, AutoCompleteOptions.get()));
    }

}

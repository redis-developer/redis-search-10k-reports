package com.redis.redissearchdemo.repository;

import com.redis.om.spring.autocomplete.Suggestion;
import com.redis.om.spring.repository.RedisDocumentRepository;
import com.redis.om.spring.repository.query.autocomplete.AutoCompleteOptions;
import com.redis.redissearchdemo.domain.FilingChunk;

import java.util.List;

public interface FilingChunkRepository extends RedisDocumentRepository<FilingChunk, String> {
    List<Suggestion> autoCompleteCompanyName(String companyName, AutoCompleteOptions options);

    List<Suggestion> autoCompleteTicker(String ticker, AutoCompleteOptions options);

    List<Suggestion> autoCompleteSectionName(String sectionName, AutoCompleteOptions options);

    Iterable<String> getAllSector();

    Iterable<String> getAllSectionName();
}

package com.redis.redissearchdemo.repository;

import com.redis.om.spring.autocomplete.Suggestion;
import com.redis.om.spring.repository.RedisDocumentRepository;
import com.redis.om.spring.repository.query.autocomplete.AutoCompleteOptions;
import com.redis.redissearchdemo.domain.Movie;

import java.util.List;

public interface MovieRepository extends RedisDocumentRepository<Movie, String> {
    List<Suggestion> autoCompleteTitle(String title, AutoCompleteOptions options);

    Iterable<String> getAllGenres();
}
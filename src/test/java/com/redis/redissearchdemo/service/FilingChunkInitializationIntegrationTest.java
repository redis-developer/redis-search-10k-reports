package com.redis.redissearchdemo.service;

import com.redis.redissearchdemo.domain.FilingChunk;
import com.redis.redissearchdemo.dto.DatasetInitializationResult;
import com.redis.redissearchdemo.repository.FilingChunkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "demo.dataset.initialize-on-startup=false")
class FilingChunkInitializationIntegrationTest {

    @Autowired
    private FilingChunkService filingChunkService;

    @Autowired
    private FilingChunkRepository filingChunkRepository;

    @AfterEach
    void tearDown() {
        filingChunkRepository.deleteAll();
    }

    @Test
    void initializeDefaultDatasetLoadsRequestedCompaniesIntoRedis() throws Exception {
        filingChunkRepository.deleteAll();

        DatasetInitializationResult result = filingChunkService.initializeDefaultDataset();

        assertThat(result.companyCount()).isEqualTo(25);
        assertThat(result.chunkCount()).isPositive();
        assertThat(result.elapsedMillis()).isGreaterThanOrEqualTo(0L);
        assertThat(filingChunkRepository.count()).isPositive();

        Set<String> tickers = new HashSet<>();
        for (FilingChunk chunk : filingChunkRepository.findAll()) {
            tickers.add(chunk.getTicker());
        }

        assertThat(tickers).hasSize(25);
    }
}

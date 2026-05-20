package com.redis.redissearchdemo.service;

import com.redis.om.spring.search.stream.EntityStream;
import com.redis.om.spring.vectorize.Embedder;
import com.redis.redissearchdemo.repository.FilingChunkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

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
            embedderProvider
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

}

package com.redis.redissearchdemo.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.redis.om.spring.annotations.AutoComplete;
import com.redis.om.spring.annotations.Document;
import com.redis.om.spring.annotations.Indexed;
import com.redis.om.spring.annotations.Searchable;
import com.redis.om.spring.annotations.TagIndexed;
import com.redis.om.spring.annotations.VectorIndexed;
import com.redis.om.spring.annotations.Vectorize;
import com.redis.om.spring.annotations.EmbeddingProvider;
import com.redis.om.spring.indexing.DistanceMetric;
import org.springframework.data.annotation.Id;
import redis.clients.jedis.search.schemafields.VectorField;

@Document
@JsonIgnoreProperties(ignoreUnknown = true)
public class FilingChunk {

    @Id
    private String id;

    @Searchable
    @AutoComplete
    private String companyName;

    @Searchable
    @AutoComplete
    private String ticker;

    @TagIndexed
    private String sector;

    @TagIndexed
    @AutoComplete
    private String sectionName;

    @Indexed(sortable = true)
    private int filingYear;

    @Indexed(sortable = true)
    private String filingDate;

    private String secUrl;

    @Vectorize(
            destination = "chunkEmbedding",
            provider = EmbeddingProvider.TRANSFORMERS
    )
    @Searchable
    private String chunkText;

    @VectorIndexed(
            algorithm = VectorField.VectorAlgorithm.HNSW,
            dimension = 384,
            distanceMetric = DistanceMetric.COSINE,
            initialCapacity = 50000
    )
    @JsonIgnore
    private float[] chunkEmbedding;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public int getFilingYear() {
        return filingYear;
    }

    public void setFilingYear(int filingYear) {
        this.filingYear = filingYear;
    }

    public String getFilingDate() {
        return filingDate;
    }

    public void setFilingDate(String filingDate) {
        this.filingDate = filingDate;
    }

    public String getSecUrl() {
        return secUrl;
    }

    public void setSecUrl(String secUrl) {
        this.secUrl = secUrl;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public float[] getChunkEmbedding() {
        return chunkEmbedding;
    }

    public void setChunkEmbedding(float[] chunkEmbedding) {
        this.chunkEmbedding = chunkEmbedding;
    }
}

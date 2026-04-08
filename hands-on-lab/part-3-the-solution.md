# Part 3: The Solution

We will solve the problem in two passes:

1. Build the solution directly with Redis JSON and Redis Search commands.
2. Map the same solution into the Java and Spring application with Redis OM Spring.

## 1. Start with the data model

We will use a shortened 10-K document shape based on this demo:

```redis
JSON.SET filing:1 $ '{
  "companyName": "Microsoft",
  "ticker": "MSFT",
  "sector": "Information Technology",
  "sectionName": "Risk Factors",
  "filingYear": 2025,
  "filingDate": "2025-07-30",
  "secUrl": "https://www.sec.gov/Archives/edgar/data/example/msft-10k.htm",
  "chunkText": "Demand for AI infrastructure and cloud services may fluctuate due to capital spending, competition, and supply chain constraints.",
  "chunkEmbedding": [0.12, -0.08, 0.31, 0.44]
}'
```

For readability, the example uses a 4-dimensional placeholder vector. In the application, Redis OM Spring generates a 384-dimensional embedding for `chunkText`.

## 2. Secondary indexes

What we need: query structured fields without knowing the key first.

How it works:

- What Redis stores: indexed field values. In this example, `TAG` indexes store categorical values such as `ticker`, `sector`, and `sectionName`, and a `NUMERIC` index stores `filingYear`.
- What the query does: jump directly to documents that match a field value or numeric range.
- Why it helps: Redis can filter by field values without scanning every JSON document.

Redis commands:

```redis
FT.CREATE idx:filings ON JSON PREFIX 1 filing: SCHEMA \
  $.companyName AS companyName TAG \
  $.ticker AS ticker TAG \
  $.sector AS sector TAG \
  $.sectionName AS sectionName TAG \
  $.filingYear AS filingYear NUMERIC SORTABLE \
  $.filingDate AS filingDate TAG
```

Example queries:

```redis
FT.SEARCH idx:filings '@ticker:{MSFT}'

FT.SEARCH idx:filings '@sector:{Information\ Technology} @filingYear:[2025 2025]' \
  RETURN 4 companyName ticker sectionName filingDate
```

What the query returns: documents filtered by structured fields instead of by key lookup.

How this maps to Redis OM Spring later: indexed Java fields that can be queried with repository and search stream filters.

## 3. Autocomplete

What we need: prefix matching while the user is typing.

How it works:

- What Redis stores: suggestion strings and scores in a suggestion dictionary. This sits beside the main search index.
- What the query does: look up prefixes such as `Mic`, `MS`, or `Ris`.
- Why it helps: Redis can return valid suggestions quickly while the user is still typing.

Redis commands:

```redis
FT.SUGADD sug:company "Microsoft" 1
FT.SUGADD sug:company "Micron Technology" 1
FT.SUGADD sug:ticker "MSFT" 1
FT.SUGADD sug:section "Risk Factors" 1
```

Example queries:

```redis
FT.SUGGET sug:company Mic MAX 5
FT.SUGGET sug:ticker MS MAX 5
FT.SUGGET sug:section Ris MAX 5
```

What the query returns: suggestion lists for prefixes such as `Mic`, `MS`, or `Ris`.

How this maps to Redis OM Spring later: `@AutoComplete` fields and repository autocomplete methods.

## 4. Full-text search

What we need: keyword search and ranking over filing text.

How it works:

- What Redis stores: an inverted index. Redis stores terms and the documents that contain those terms.
- What the query does: look up one or more terms and retrieve the matching documents.
- Why it helps: keyword search becomes efficient, and Redis can rank results by lexical relevance.

About BM25:

- Full-text search is not just term matching. Redis also scores results.
- `BM25` is a ranking model that rewards term frequency, rewards rarer terms more than common terms, and normalizes for document length.
- In practice, that means a chunk that matches important query terms more strongly should rank above a chunk with weaker or noisier matches.
- In current Redis docs, the scorer name is `BM25STD`. `BM25` is the older name and is deprecated in Redis 8.4.

Redis commands:

```redis
FT.ALTER idx:filings SCHEMA ADD $.chunkText AS chunkText TEXT
```

Example queries:

```redis
FT.SEARCH idx:filings '@chunkText:(pricing pressure)' \
  RETURN 4 companyName ticker sectionName filingDate

FT.SEARCH idx:filings '@sectionName:{Risk\ Factors} @chunkText:(supply chain)' \
  RETURN 4 companyName ticker sectionName filingDate
```

What the query returns: ranked text matches based on lexical relevance.

How this maps to Redis OM Spring later: `@Searchable` text fields and `stream.filter(query)`.

## 5. Vector search

What we need: semantic retrieval when the query and the relevant text do not use the same words.

How it works:

- What Redis stores: embeddings, which are numeric representations of text, inside a vector index.
- What the query does: compare the query vector to stored vectors and retrieve the nearest neighbors.
- Why it helps: Redis can retrieve semantically similar text even when the exact words do not match.

About `HNSW`:

- `HNSW` is the vector index structure used in this example.
- It is designed for approximate nearest-neighbor search.
- Instead of checking every vector one by one, Redis uses the index to find close matches much more efficiently.

Other vector index options:

- `FLAT` performs exact nearest-neighbor search.
- `FLAT` compares the query vector against all stored vectors, so it is simple and exact, but query cost grows linearly with the number of vectors.
- `FLAT` is a good fit for small datasets or cases where exact recall matters more than speed.
- `SVS-VAMANA` is another approximate vector index supported by Redis.
- `SVS-VAMANA` is designed for high performance with compression and lower memory usage, and Redis documents it as a good fit when approximate search is acceptable and Intel-optimized performance matters.

Redis commands:

```redis
FT.ALTER idx:filings SCHEMA ADD $.chunkEmbedding AS chunkEmbedding VECTOR HNSW 6 \
  TYPE FLOAT32 DIM 4 DISTANCE_METRIC COSINE
```

Example query:

```redis
FT.SEARCH idx:filings '*=>[KNN 3 @chunkEmbedding $query_vector AS vector_score]' \
  PARAMS 2 query_vector <binary-encoded-query-vector> \
  SORTBY vector_score \
  RETURN 5 companyName ticker sectionName filingDate vector_score \
  DIALECT 2
```

What the query returns: the nearest vectors to the query embedding.

How this maps to Redis OM Spring later: `@Vectorize`, `@VectorIndexed`, and `knn(...)`.

Note: the query text still needs to be converted into an embedding before this command runs. In the application, Redis OM Spring handles that step.

## 6. Hybrid search

What we need: combine lexical constraints with semantic similarity.

How it works:

- What Redis uses: the full-text index and the vector index together.
- What the query does: apply a lexical condition and a vector similarity condition in the same query.
- Why it helps: exact terms and semantic similarity can both influence which documents are returned.

Important detail:

- Hybrid search is not a separate index type.
- It is a query pattern that combines the indexes already built for text and vectors.

Redis command:

```redis
FT.SEARCH idx:filings '(@chunkText:(cloud demand))=>[KNN 3 @chunkEmbedding $query_vector AS vector_score]' \
  PARAMS 2 query_vector <binary-encoded-query-vector> \
  SORTBY vector_score \
  RETURN 5 companyName ticker sectionName filingDate vector_score \
  DIALECT 2
```

What the query returns: documents that match the text clause and are also close to the query vector.

How this maps to Redis OM Spring later: a higher-level hybrid search API that combines text and vector retrieval in application code.

## 7. Map the solution to Redis OM Spring

Now that we have the Redis-native model, we can map the same solution into the Java and Spring application.

### Step 1: Define the document model

```java
@Document
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

    @Vectorize(destination = "chunkEmbedding")
    @Searchable
    private String chunkText;

    @VectorIndexed(
            algorithm = VectorField.VectorAlgorithm.HNSW,
            dimension = 384,
            distanceMetric = DistanceMetric.COSINE,
            initialCapacity = 50000
    )
    private float[] chunkEmbedding;
}
```

This tells Redis OM Spring how the document should be indexed.

### Step 2: Create the repository

```java
public interface FilingChunkRepository extends RedisDocumentRepository<FilingChunk, String> {
    List<Suggestion> autoCompleteCompanyName(String companyName, AutoCompleteOptions options);

    List<Suggestion> autoCompleteTicker(String ticker, AutoCompleteOptions options);

    List<Suggestion> autoCompleteSectionName(String sectionName, AutoCompleteOptions options);

    Iterable<String> getAllSector();

    Iterable<String> getAllSectionName();
}
```

This gives us document persistence plus autocomplete and filter helpers.

### Step 3: Load the data

The application reads the packaged 10-K dataset and saves `FilingChunk` documents into Redis. When documents are saved, Redis OM Spring can generate embeddings for `chunkText` and write them into `chunkEmbedding`.

### Step 4: Implement the query logic

Structured filters:

```java
filtered = filtered.filter(FilingChunk$.COMPANY_NAME.eq(normalizedCompanyName));
filtered = filtered.filter(FilingChunk$.TICKER.eq(normalizedTicker));
filtered = filtered.filter(FilingChunk$.SECTOR.in(normalizedSectors));
filtered = filtered.filter(FilingChunk$.FILING_YEAR.eq(filingYear));
```

Full-text search:

```java
stream.filter(query).limit(limit).collect(Collectors.toList());
```

Vector search:

```java
stream
    .filter(FilingChunk$.CHUNK_EMBEDDING.knn(Math.max(limit, 20), queryEmbedding))
    .limit(limit)
    .collect(Collectors.toList());
```

Hybrid search:

```java
stream
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
```

Autocomplete:

```java
filingChunkRepository.autoCompleteCompanyName(query, AutoCompleteOptions.get());
filingChunkRepository.autoCompleteTicker(query, AutoCompleteOptions.get());
filingChunkRepository.autoCompleteSectionName(query, AutoCompleteOptions.get());
```

### Step 5: Run the application

Once the documents, indexes, and query logic are in place, the Spring application exposes:

- structured filters
- autocomplete
- full-text search
- vector search
- hybrid search

## 8. What we built

We started with Redis JSON documents, added search capabilities directly in Redis, and then mapped the same ideas into Java with Redis OM Spring. The result is one application that can query the same 10-K data in multiple ways without moving it into a separate search system.

In Part 4, we will test the demo end to end.
